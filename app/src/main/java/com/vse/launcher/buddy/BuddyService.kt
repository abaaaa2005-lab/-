package com.vse.launcher.buddy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vse.launcher.MainActivity
import com.vse.launcher.R
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/** Помощник поверх всех приложений: сидит в углу, смотрит на экран, говорит и показывает эмоции. */
class BuddyService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_PAUSE = "pause"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL = "buddy"
        private const val NOTIF_ID = 77

        /** [code]/[data] — результат запроса на запись экрана (null — помощник без зрения). */
        fun start(ctx: Context, code: Int?, data: Intent?) {
            val i = Intent(ctx, BuddyService::class.java).setAction(ACTION_START)
            if (code != null && data != null) i.putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, BuddyService::class.java).setAction(ACTION_STOP))
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var grabber: ScreenGrabber? = null
    private val brain = BuddyBrain()
    private lateinit var free: FreeBrain
    private var lastAppSeen: String? = null
    private lateinit var voice: BuddyVoice
    private var lastComment = 0L
    private var hideJob: Job? = null
    private var recognizer: SpeechRecognizer? = null
    private var paused by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        BuddyState.load(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        free = FreeBrain(this)
        voice = BuddyVoice(this) { t -> scope.launch { BuddyState.talking = t } }
        if (BuddyState.look.voiceMode == 3) PiperVoice.preload(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Помощник", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_PAUSE -> { paused = !paused; updateNotification(); return START_NOT_STICKY }
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        val vision = data != null
        val type = when {
            vision && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else -> 0
        }
        // Сначала foreground с нужным типом, и только потом — MediaProjection (требование Android 14)
        ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)
        if (vision) {
            grabber?.stop()
            grabber = runCatching {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                ScreenGrabber(this, mpm.getMediaProjection(code, data!!)) {
                    scope.launch {
                        grabber = null
                        BuddyState.seeing = false
                        react(BuddyReply(Emotion.SAD, "Ой, я больше не вижу экран."))
                    }
                }
            }.getOrNull()
            BuddyState.seeing = grabber != null
        }
        if (view == null) {
            showOverlay()
            BuddyState.running = true
            scope.launch { loop() }
            scope.launch {
                delay(600)
                react(if (BuddyState.seeing) BuddyReply(Emotion.HAPPY, "Привет! Я ${BuddyState.look.name}, теперь я вижу твой экран.") else free.greeting())
            }
        } else if (vision) {
            scope.launch { react(BuddyReply(Emotion.EXCITED, "Ура, теперь я всё вижу!")) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        recognizer?.destroy()
        voice.release()
        grabber?.stop()
        grabber = null
        brain.close()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        BuddyState.running = false
        BuddyState.seeing = false
        BuddyState.talking = false
        BuddyState.bubble = null
        BuddyState.busy = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    // ---------------- Логика ----------------

    /** Думает через Claude: включён «умный режим» и есть ключ. */
    private val smart get() = BuddyState.look.smart && BuddyState.look.apiKey.isNotBlank()

    /**
     * Умный режим: раз в N секунд смотрит на экран и, если там что-то поменялось, комментирует.
     * Бесплатный: шутит, когда открываешь другое приложение, и иногда — просто так.
     */
    private suspend fun loop() {
        while (true) {
            delay(1000)
            val every = BuddyState.look.interval
            if (paused || BuddyState.busy) continue
            val now = System.currentTimeMillis()
            if (smart) {
                if (every <= 0 || grabber == null) continue
                if (now - lastComment < every * 1000L) continue
                comment(auto = true, question = null)
            } else {
                val app = BuddyState.currentApp
                // Открыли другое приложение и задержались в нём пару секунд
                val switched = app != null && app != lastAppSeen && now - BuddyState.appSince > 2500
                if (switched && now - lastComment > 20_000) {
                    lastAppSeen = app
                    if (Math.random() < 0.75) react(free.react(tap = false))
                    lastComment = now
                } else if (switched) {
                    lastAppSeen = app
                } else if (every > 0 && now - lastComment > every * 2000L) {
                    react(free.react(tap = false))
                    lastComment = now
                }
            }
        }
    }

    private suspend fun comment(auto: Boolean, question: String?) {
        if (BuddyState.busy) return
        if (!smart) {
            react(if (question != null) free.answer(question) else free.react(tap = !auto))
            lastComment = System.currentTimeMillis()
            return
        }
        BuddyState.busy = true
        lastComment = System.currentTimeMillis()
        try {
            val g = grabber
            val shot = withContext(Dispatchers.Default) { g?.grab() }
            if (auto && (shot == null || !withContext(Dispatchers.Default) { g!!.changed(shot) })) return
            if (!auto) {
                BuddyState.emotion = Emotion.THINKING
                BuddyState.bubble = "Хм…"
            }
            val reply = withContext(Dispatchers.IO) { brain.think(BuddyState.look, shot, question) }
            react(reply)
        } finally {
            BuddyState.busy = false
            lastComment = System.currentTimeMillis()
        }
    }

    private fun react(r: BuddyReply) {
        val look = BuddyState.look
        BuddyState.emotion = r.emotion
        BuddyState.bubble = r.text
        if (!BuddyState.muted) {
            if (look.chirps) voice.chirp(r.emotion)
            if (look.voiceMode != 2) scope.launch { delay(if (look.chirps) 350 else 0); voice.speak(r.text, r.emotion, BuddyState.look) }
        }
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(BuddyState.look.bubbleSeconds * 1000L)
            while (BuddyState.talking) delay(300)
            BuddyState.bubble = null
            delay(4000)
            if (!BuddyState.busy) BuddyState.emotion = Emotion.NEUTRAL
        }
    }

    private fun poke() {
        if (BuddyState.busy) return
        voice.stop()
        scope.launch { comment(auto = false, question = null) }
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            react(BuddyReply(Emotion.CONFUSED, "Разреши мне микрофон в настройках помощника, и я буду тебя слышать."))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            react(BuddyReply(Emotion.SAD, "На этом телефоне нет распознавания речи."))
            return
        }
        voice.stop()
        hideJob?.cancel()
        BuddyState.emotion = Emotion.SURPRISED
        BuddyState.bubble = "Слушаю…"
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) react(BuddyReply(Emotion.CONFUSED, "Я ничего не расслышал."))
                    else {
                        BuddyState.bubble = "«$text»"
                        scope.launch { comment(auto = false, question = text) }
                    }
                }
                override fun onError(error: Int) = react(BuddyReply(Emotion.CONFUSED, "Не расслышал, повтори, пожалуйста."))
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { BuddyState.emotion = Emotion.THINKING }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            )
        }
    }

    // ---------------- Окно ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun placeInCorner() {
        val c = BuddyState.look.corner
        params.gravity = (if (c % 2 == 0) Gravity.END else Gravity.START) or (if (c < 2) Gravity.BOTTOM else Gravity.TOP)
        params.x = dp(8)
        params.y = if (c < 2) dp(110) else dp(70)
    }

    private fun showOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        placeInCorner()
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BuddyService)
            setViewTreeSavedStateRegistryOwner(this@BuddyService)
            setContent { Overlay() }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        wm.addView(v, params)
        view = v
    }

    private fun moveBy(dx: Float, dy: Float) {
        val v = view ?: return
        val g = params.gravity
        params.x += (if (g and Gravity.END == Gravity.END) -dx else dx).roundToInt()
        params.y += (if (g and Gravity.BOTTOM == Gravity.BOTTOM) -dy else dy).roundToInt()
        runCatching { wm.updateViewLayout(v, params) }
    }

    /** После перетаскивания «прилипаем» к ближайшему углу. */
    private fun snap() {
        val v = view ?: return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val m = resources.displayMetrics
        val right = loc[0] + v.width / 2 > m.widthPixels / 2
        val bottom = loc[1] + v.height / 2 > m.heightPixels / 2
        val corner = (if (right) 0 else 1) + (if (bottom) 0 else 2)
        BuddyState.update(this) { it.copy(corner = corner) }
        placeInCorner()
        runCatching { wm.updateViewLayout(v, params) }
    }

    @Composable
    private fun Overlay() {
        val look = BuddyState.look
        var menu by remember { mutableStateOf(false) }
        // Если угол поменяли в настройках — переезжаем
        LaunchedEffect(look.corner) { view?.let { placeInCorner(); runCatching { wm.updateViewLayout(it, params) } } }
        val right = look.corner % 2 == 0
        val bottom = look.corner < 2
        val pop = remember { Animatable(0f) }
        LaunchedEffect(BuddyState.emotion) { pop.snapTo(0.85f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }

        Row(verticalAlignment = if (bottom) Alignment.Bottom else Alignment.Top, modifier = Modifier.padding(4.dp)) {
            val side: @Composable () -> Unit = @Composable {
                Box(Modifier.padding(bottom = if (bottom) (look.sizeDp * 0.45f).dp else 0.dp, top = if (!bottom) (look.sizeDp * 0.3f).dp else 0.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = menu || BuddyState.bubble != null,
                        enter = fadeIn() + scaleIn(initialScale = 0.6f),
                        exit = fadeOut() + scaleOut(targetScale = 0.6f),
                    ) {
                        if (menu) Menu { menu = false }
                        else Bubble(BuddyState.bubble.orEmpty())
                    }
                }
            }
            if (right) side()
            Box(
                Modifier
                    .size(look.sizeDp.dp)
                    .alpha(look.opacity)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (menu) menu = false else poke() },
                            onDoubleTap = { BuddyState.muted = !BuddyState.muted; if (BuddyState.muted) voice.stop(); react(BuddyReply(if (BuddyState.muted) Emotion.SLEEPY else Emotion.HAPPY, if (BuddyState.muted) "Молчу-молчу." else "Я снова со звуком!")) },
                            onLongPress = { menu = !menu },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { snap() }) { ch, d -> ch.consume(); moveBy(d.x, d.y) }
                    }
            ) {
                BuddyCharacter(
                    look, BuddyState.emotion, BuddyState.talking,
                    Modifier.fillMaxSize().graphicsScale(pop.value),
                )
            }
            if (!right) side()
        }
    }

    @Composable
    private fun Bubble(text: String) {
        Box(
            Modifier
                .widthIn(max = 230.dp)
                .shadow(6.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2FFFFFF))
                .clickable { hideJob?.cancel(); BuddyState.bubble = null; voice.stop() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = Color(0xFF23233A), fontSize = 15.sp, lineHeight = 19.sp)
        }
    }

    @Composable
    private fun Menu(close: () -> Unit) {
        Column(
            Modifier
                .width(200.dp)
                .shadow(8.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF22B2B35))
                .padding(vertical = 6.dp)
        ) {
            @Composable
            fun item(label: String, action: () -> Unit) = Text(
                label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable { close(); action() }.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            item("🎤  Спросить голосом") { listen() }
            item(if (smart) "👀  Что на экране?" else "😄  Скажи что-нибудь") { poke() }
            item(if (BuddyState.muted) "🔊  Включить звук" else "🔇  Без звука") { BuddyState.muted = !BuddyState.muted; voice.stop() }
            item(if (paused) "▶️  Снова комментировать" else "⏸  Не комментировать сам") { paused = !paused; updateNotification() }
            item("🎨  Настроить образ") {
                startActivity(Intent(this@BuddyService, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_BUDDY, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            item("👋  Выключить") { stopSelf() }
        }
    }

    // ---------------- Уведомление ----------------

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_BUDDY, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(this, 2, Intent(this, BuddyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 3, Intent(this, BuddyService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val name = BuddyState.look.name
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$name рядом")
            .setContentText(
                when {
                    paused -> "Пауза: комментирует только по нажатию"
                    !smart -> "Бесплатный режим: шутит про открытые приложения"
                    BuddyState.seeing -> "Смотрит на экран и комментирует"
                    else -> "Экран не видит — нажмите на него, чтобы поболтать"
                }
            )
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, if (paused) "Продолжить" else "Пауза", pause)
            .addAction(0, "Выключить", stop)
            .build()
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification()) }
    }
}

private fun Modifier.graphicsScale(s: Float) = graphicsLayer(scaleX = s, scaleY = s)
