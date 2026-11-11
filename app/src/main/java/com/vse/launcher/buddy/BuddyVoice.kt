package com.vse.launcher.buddy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** Голос помощника: синтез речи + короткие «звуки эмоций». */
class BuddyVoice(ctx: Context, private val onTalking: (Boolean) -> Unit) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.language = Locale("ru", "RU")
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

    fun say(text: String, look: BuddyLook) {
        if (!ready) { onTalking(false); return }
        tts.setPitch(look.voicePitch)
        tts.setSpeechRate(look.voiceRate)
        // Эмодзи и звёздочки вслух не читаем
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}*_~#]"), "").trim()
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), "buddy-${System.nanoTime()}")
    }

    fun stop() = tts.stop()

    fun release() {
        tts.stop()
        tts.shutdown()
    }

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
        thread(isDaemon = true) { playTones(notes) }
    }

    private fun playTones(notes: List<Pair<Double, Int>>) = runCatching {
        val rate = 22050
        val samples = ShortArray(notes.sumOf { it.second } * rate / 1000)
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
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep(notes.sumOf { it.second }.toLong() + 60)
        track.release()
    }
}
