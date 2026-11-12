package com.vse.launcher.buddy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors

/**
 * Нейросетевой голос «Ирина» (Piper) — работает без интернета.
 * Модель грузится один раз в фоне (несколько секунд), фразы озвучиваются по очереди.
 */
object PiperVoice {
    private const val ASSETS = "piper"
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "piper").apply { isDaemon = true } }
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var failed = false
    @Volatile private var generation = 0
    @Volatile private var track: AudioTrack? = null
    private val cache = object : LinkedHashMap<String, ShortArray>(16, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ShortArray>?) = size > 24
    }

    /** Голос встроен в эту сборку (модель есть в APK). */
    fun available(ctx: Context): Boolean =
        !failed && runCatching { ctx.assets.list(ASSETS)?.contains("model.onnx") == true }.getOrDefault(false)

    /** Заранее загрузить модель, чтобы первая фраза не ждала. */
    fun preload(ctx: Context) {
        val app = ctx.applicationContext
        worker.execute { ensure(app) }
    }

    private fun ensure(ctx: Context): OfflineTts? {
        tts?.let { return it }
        if (failed) return null
        return runCatching {
            // eSpeak (разбор русских слов на звуки) читает данные только из обычной папки — копируем один раз
            val data = File(ctx.filesDir, "piper/espeak-ng-data")
            val marker = File(ctx.filesDir, "piper/.copied")
            if (!marker.exists()) {
                data.deleteRecursively()
                copyAssets(ctx, "$ASSETS/espeak-ng-data", data)
                marker.writeText("1")
            }
            val vits = OfflineTtsVitsModelConfig(
                model = "$ASSETS/model.onnx",
                lexicon = "",
                tokens = "$ASSETS/tokens.txt",
                dataDir = data.absolutePath,
                dictDir = "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            )
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(vits = vits, numThreads = 2, debug = false, provider = "cpu"),
            )
            OfflineTts(assetManager = ctx.assets, config = config).also { tts = it }
        }.onFailure { failed = true }.getOrNull()
    }

    private fun copyAssets(ctx: Context, from: String, to: File) {
        val list = ctx.assets.list(from).orEmpty()
        if (list.isEmpty()) {
            to.parentFile?.mkdirs()
            ctx.assets.open(from).use { i -> to.outputStream().use { i.copyTo(it) } }
        } else {
            to.mkdirs()
            for (name in list) copyAssets(ctx, "$from/$name", File(to, name))
        }
    }

    /**
     * Сказать фразу. [pitch] поднимает/опускает голос (1 — как есть), [speed] — темп.
     * [onTalking] вызывается в начале и в конце; [onFail] — если голос не загрузился.
     */
    fun speak(ctx: Context, text: String, pitch: Float, speed: Float, onTalking: (Boolean) -> Unit, onFail: () -> Unit) {
        val app = ctx.applicationContext
        val my = ++generation
        stopTrack()
        worker.execute {
            if (my != generation) return@execute
            val engine = ensure(app)
            if (engine == null) { onFail(); return@execute }
            val clean = text.replace(Regex("[\\p{So}\\p{Cn}*_~#«»]"), "").trim()
            if (clean.isEmpty()) return@execute
            val p = pitch.coerceIn(0.7f, 1.6f)
            // Высоту меняем скоростью воспроизведения, а темп заранее компенсируем
            val genSpeed = (speed / p).coerceIn(0.5f, 2.5f)
            val key = "$clean|$genSpeed"
            val pcm = synchronized(cache) { cache[key] } ?: run {
                val audio = runCatching { engine.generate(clean, 0, genSpeed) }.getOrNull() ?: run { onFail(); return@execute }
                ShortArray(audio.samples.size) { i -> (audio.samples[i].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
                    .also { synchronized(cache) { cache[key] = it } }
            }
            if (my != generation) return@execute
            play(pcm, (engine.sampleRate() * p).toInt(), my, onTalking)
        }
    }

    private fun play(pcm: ShortArray, rate: Int, my: Int, onTalking: (Boolean) -> Unit) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        try {
            t.write(pcm, 0, pcm.size)
            onTalking(true)
            t.play()
            val ms = pcm.size * 1000L / rate
            val end = System.currentTimeMillis() + ms + 80
            while (System.currentTimeMillis() < end && my == generation) Thread.sleep(40)
        } catch (_: Exception) {
        } finally {
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
            onTalking(false)
        }
    }

    private fun stopTrack() {
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    fun stop() {
        generation++
        stopTrack()
    }
}
