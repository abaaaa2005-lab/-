package com.vse.launcher

import com.vse.launcher.buddy.BuddyVoice
import com.vse.launcher.buddy.Emotion
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** Собирает пример «мультяшного лепета» в build/sounds/babble.wav — послушать без телефона. */
class BuddyVoiceTest {
    @Test
    fun babbleSample() {
        val phrases = listOf(
            "Привет! Я Дружок, твой помощник!" to Emotion.HAPPY,
            "О, видосики! Что смотрим?" to Emotion.EXCITED,
            "Ой, батарейка почти села…" to Emotion.SAD,
        )
        val all = ArrayList<Short>()
        for ((text, emo) in phrases) {
            val (pcm, _) = BuddyVoice.babblePcm(text, emo, 1f, 1f)!!
            assertTrue(pcm.isNotEmpty())
            pcm.forEach { all += it }
            repeat(BuddyVoice.RATE / 2) { all += 0 } // пауза полсекунды
        }
        val dir = File("build/sounds").apply { mkdirs() }
        File(dir, "babble.wav").writeBytes(wav(all.toShortArray(), BuddyVoice.RATE))
    }

    private fun wav(pcm: ShortArray, rate: Int): ByteArray {
        val data = ByteArray(pcm.size * 2)
        pcm.forEachIndexed { i, v -> data[i * 2] = (v.toInt() and 0xFF).toByte(); data[i * 2 + 1] = (v.toInt() shr 8).toByte() }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            fun le32(v: Int) { write(v and 0xFF); write(v shr 8 and 0xFF); write(v shr 16 and 0xFF); write(v shr 24 and 0xFF) }
            fun le16(v: Int) { write(v and 0xFF); write(v shr 8 and 0xFF) }
            writeBytes("RIFF"); le32(36 + data.size); writeBytes("WAVE")
            writeBytes("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            writeBytes("data"); le32(data.size); write(data)
        }
        return out.toByteArray()
    }
}
