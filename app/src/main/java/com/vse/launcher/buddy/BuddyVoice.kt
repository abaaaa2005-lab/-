package com.vse.launcher.buddy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** Голос в списке выбора. */
data class VoiceOption(val name: String, val label: String)

/**
 * Голос помощника:
 * - «мультяшный лепет» — как у персонажей в играх: короткие слоги-звуки в ритм текста;
 * - голос телефона — синтез речи Android с выбором голоса;
 * - короткие мелодии эмоций.
 */
class BuddyVoice(ctx: Context, private val onTalking: (Boolean) -> Unit) {
    private var ready = false
    @Volatile private var babbling: Thread? = null

    /** Русские голоса, установленные в телефоне (появляются после запуска синтеза). */
    var voices by mutableStateOf<List<VoiceOption>>(emptyList())
        private set

    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.language = Locale("ru", "RU")
            voices = runCatching { russianVoices() }.getOrDefault(emptyList())
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = onTalking(true)
            override fun onDone(id: String?) = onTalking(false)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = onTalking(false)
            override fun onStop(id: String?, interrupted: Boolean) = onTalking(false)
        })
    }

    private fun russianVoices(): List<VoiceOption> {
        val list = tts.voices.orEmpty()
            .filter { it.locale.language == "ru" && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
        return list.mapIndexed { i, v ->
            val q = when {
                v.quality >= Voice.QUALITY_VERY_HIGH -> "отличное качество"
                v.quality >= Voice.QUALITY_HIGH -> "хорошее качество"
                else -> "обычное качество"
            }
            VoiceOption(v.name, "Голос ${i + 1} · $q" + if (v.isNetworkConnectionRequired) " · через интернет" else "")
        }
    }

    /** Сказать фразу так, как выбрано в настройках. */
    fun speak(text: String, emotion: Emotion, look: BuddyLook) {
        when (look.voiceMode) {
            0 -> babble(text, emotion, look)
            1 -> say(text, look)
        }
    }

    fun say(text: String, look: BuddyLook) {
        if (!ready) { onTalking(false); return }
        val chosen = tts.voices.orEmpty().firstOrNull { it.name == look.voiceName }
            ?: tts.voices.orEmpty().filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
        if (chosen != null) runCatching { tts.voice = chosen } else tts.language = Locale("ru", "RU")
        tts.setPitch(look.voicePitch)
        tts.setSpeechRate(look.voiceRate)
        // Эмодзи и звёздочки вслух не читаем
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}*_~#]"), "").trim()
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), "buddy-${System.nanoTime()}")
    }

    fun stop() {
        tts.stop()
        babbling?.interrupt()
        babbling = null
    }

    fun release() {
        stop()
        tts.shutdown()
    }

    // ---------------- Мультяшный лепет ----------------

    /** Слоги-звуки в ритм фразы: высота зависит от букв, эмоции и настройки «высота голоса». */
    fun babble(text: String, emotion: Emotion, look: BuddyLook) {
        stop()
        val (pcm, ms) = babblePcm(text, emotion, look.voicePitch, look.voiceRate) ?: return
        val t = thread(isDaemon = true) {
            onTalking(true)
            try {
                playPcm(pcm, RATE, ms)
            } catch (_: InterruptedException) {
            } finally {
                onTalking(false)
            }
        }
        babbling = t
    }

    companion object {
        const val RATE = 22050

        /** Звук лепета (PCM 16 бит, моно, [RATE] Гц) и его длительность в мс. */
        fun babblePcm(text: String, emotion: Emotion, pitch: Float, speed: Float): Pair<ShortArray, Int>? {
            val letters = text.filter { it.isLetter() }.lowercase()
            if (letters.isEmpty()) return null
            val base = 300.0 * pitch * when (emotion) {
                Emotion.EXCITED, Emotion.LAUGHING, Emotion.SURPRISED, Emotion.SCARED -> 1.25
                Emotion.SAD, Emotion.SLEEPY -> 0.8
                Emotion.ANGRY -> 0.75
                else -> 1.0
            }
            val syllableMs = (75 / speed).toInt().coerceIn(40, 140)
            // Каждые ~2 буквы — один слог, не дольше ~4 секунд
            val count = (letters.length / 2).coerceIn(2, 4000 / syllableMs)
            val vowels = "аеёиоуыэюяaeiouy"
            val question = text.trimEnd().endsWith("?")
            val gapMs = 22
            val notes = (0 until count).map { i ->
                val ch = letters[(i * 2) % letters.length]
                val step = (ch.code * 7 + i * 3) % 7 // «мелодия» из букв
                val f = base * Math.pow(2.0, (step - 3) / 12.0 * 2)
                // В конце вопроса — вверх, в конце фразы — вниз
                val tail = if (i == count - 1) (if (question) 1.25 else 0.85) else 1.0
                Triple(f * tail, syllableMs + (if (ch in vowels) 15 else 0), i == count - 1)
            }
            val total = notes.sumOf { it.second + gapMs }
            val samples = ShortArray(total * RATE / 1000)
            var pos = 0
            for ((f, ms, last) in notes) {
                val n = ms * RATE / 1000
                for (i in 0 until n) {
                    val tt = i.toDouble() / RATE
                    // Лёгкое «плавание» высоты делает звук живым
                    val glide = 1.0 + 0.06 * sin(PI * i / n) + (if (last) -0.1 * i / n else 0.0)
                    val ph = 2 * PI * f * glide * tt
                    // Мягкий «голосовой» тембр: основной тон + обертоны
                    val v = sin(ph) * 0.6 + sin(2 * ph) * 0.25 + sin(3 * ph) * 0.1
                    val env = minOf(1.0, i / 120.0, (n - i) / 300.0)
                    samples[pos++] = (v * env * 0.32 * Short.MAX_VALUE).toInt().toShort()
                }
                pos += gapMs * RATE / 1000
            }
            return samples to total
        }
    }

    // ---------------- Мелодии эмоций ----------------

    /** Короткая «мелодия» эмоции: последовательность нот (Гц, мс). */
    fun chirp(e: Emotion) {
        val notes: List<Pair<Double, Int>> = when (e) {
            Emotion.HAPPY -> listOf(660.0 to 70, 880.0 to 90)
            Emotion.LAUGHING -> listOf(700.0 to 50, 900.0 to 50, 700.0 to 50, 900.0 to 50, 1100.0 to 80)
            Emotion.EXCITED -> listOf(523.0 to 60, 659.0 to 60, 784.0 to 60, 1047.0 to 120)
            Emotion.LOVE -> listOf(784.0 to 120, 988.0 to 180)
            Emotion.SURPRISED -> listOf(400.0 to 40, 1200.0 to 160)
            Emotion.THINKING -> listOf(500.0 to 80, 560.0 to 80, 500.0 to 80)
            Emotion.CONFUSED -> listOf(600.0 to 90, 450.0 to 90, 650.0 to 120)
            Emotion.SAD -> listOf(520.0 to 160, 400.0 to 260)
            Emotion.ANGRY -> listOf(180.0 to 90, 160.0 to 140)
            Emotion.SCARED -> listOf(1200.0 to 40, 900.0 to 40, 1300.0 to 40, 800.0 to 80)
            Emotion.SLEEPY -> listOf(300.0 to 300, 250.0 to 300)
            Emotion.COOL -> listOf(440.0 to 80, 660.0 to 160)
            Emotion.SMUG -> listOf(550.0 to 90, 500.0 to 60, 700.0 to 120)
            Emotion.NEUTRAL -> listOf(700.0 to 70)
        }
        thread(isDaemon = true) { runCatching { playTones(notes) } }
    }

    private fun playTones(notes: List<Pair<Double, Int>>) {
        val rate = 22050
        val total = notes.sumOf { it.second }
        val samples = ShortArray(total * rate / 1000)
        var pos = 0
        for ((f, ms) in notes) {
            val n = ms * rate / 1000
            for (i in 0 until n) {
                // Мягкая огибающая, чтобы не щёлкало
                val env = minOf(1.0, i / 200.0, (n - i) / 400.0)
                val v = sin(2 * PI * f * i / rate) * 0.5 + sin(4 * PI * f * i / rate) * 0.15
                samples[pos++] = (v * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        playPcm(samples, rate, total)
    }

    private fun playPcm(samples: ShortArray, rate: Int, totalMs: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(totalMs.toLong() + 60)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
