package com.vse.launcher.buddy

import android.graphics.Bitmap
import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Ответ помощника: эмоция + короткая реплика. */
data class BuddyReply(val emotion: Emotion, val text: String)

/** Отправляет скриншот в Claude и получает комментарий с эмоцией. */
class BuddyBrain {
    private var client: AnthropicClient? = null
    private var clientKey = ""
    private val recent = ArrayDeque<String>()

    private fun client(key: String): AnthropicClient {
        if (client == null || clientKey != key) {
            client?.close()
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    fun close() { client?.close(); client = null }

    /**
     * [shot] — скриншот (или null, если экран не виден), [question] — вопрос пользователя голосом.
     * Вызывать не из главного потока.
     */
    fun think(look: BuddyLook, shot: Bitmap?, question: String?): BuddyReply {
        if (look.apiKey.isBlank()) return BuddyReply(Emotion.CONFUSED, "Дай мне ключ Claude API в настройках — и я начну видеть!")
        val (model, _) = Wardrobe.models.getOrElse(look.model) { Wardrobe.models[0] }
        val persona = Wardrobe.personalities.getOrElse(look.personality) { Wardrobe.personalities[0] }.second

        val content = mutableListOf<BetaContentBlockParam>()
        if (shot != null) {
            content += BetaContentBlockParam.ofImage(
                BetaImageBlockParam.builder()
                    .source(BetaBase64ImageSource.builder().mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG).data(jpeg(shot)).build())
                    .build()
            )
        }
        val ask = buildString {
            if (recent.isNotEmpty()) append("Твои последние реплики (не повторяйся): ${recent.joinToString(" | ")}\n")
            when {
                question != null && shot != null -> append("Пользователь спрашивает голосом: «$question». Ответь, учитывая то, что видно на экране.")
                question != null -> append("Пользователь спрашивает голосом: «$question». Экран ты сейчас не видишь.")
                shot != null -> append("Вот что сейчас на экране телефона. Прокомментируй это одной живой репликой.")
                else -> append("Экран ты сейчас не видишь. Скажи что-нибудь дружелюбное, чтобы пользователь не скучал.")
            }
        }
        content += BetaContentBlockParam.ofText(ask)

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            .system(systemPrompt(look.name, persona))
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(BetaOutputConfig.Effort.LOW)
                    .format(BetaJsonOutputFormat.builder().schema(schema).build())
                    .build()
            )
            .addUserMessageOfContentBlockParams(content)
            .apply {
                // Если модель отказалась отвечать, сервер сам попробует запасную модель
                if (model != "claude-haiku-5-5") addBeta("server-side-fallback-2026-07-01").fallbacksDefault()
            }
            .build()

        return try {
            val msg = client(look.apiKey).beta().messages().create(params)
            if (msg.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                return BuddyReply(Emotion.CONFUSED, "Хм, об этом я лучше промолчу.")
            }
            val raw = msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            val o = JSONObject(raw)
            val text = o.optString("comment").trim().ifBlank { "…" }
            remember(text)
            BuddyReply(Emotion.of(o.optString("emotion")), text)
        } catch (e: UnauthorizedException) {
            BuddyReply(Emotion.SAD, "Ключ API не подходит. Проверь его в настройках.")
        } catch (e: RateLimitException) {
            BuddyReply(Emotion.SLEEPY, "Я немного устал от болтовни. Отдохну минутку.")
        } catch (e: AnthropicServiceException) {
            BuddyReply(Emotion.CONFUSED, "Сервер Claude ответил ошибкой ${e.statusCode()}. Попробую позже.")
        } catch (e: org.json.JSONException) {
            BuddyReply(Emotion.CONFUSED, "Я запутался в своих мыслях…")
        } catch (e: Exception) {
            BuddyReply(Emotion.SAD, "Нет связи с интернетом — я ничего не вижу.")
        }
    }

    private fun remember(text: String) {
        recent.addLast(text)
        while (recent.size > 4) recent.removeFirst()
    }

    private fun jpeg(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 72, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun systemPrompt(name: String, persona: String) = """
        Ты — $name, маленький живой помощник-персонаж, который сидит в углу экрана Android-телефона и смотрит на экран вместе с пользователем.
        Твой характер: $persona.
        Ты видишь скриншот экрана. Себя (маленького персонажа в углу) на скриншоте не замечай и не комментируй.
        Отвечай по-русски, коротко: одна-две фразы, не больше 160 символов — реплика покажется в облачке и будет произнесена вслух.
        Комментируй конкретно то, что видно: приложение, картинку, текст, игру, видео. Можно шутить, удивляться, подсказывать.
        Не зачитывай вслух личные данные: пароли, номера карт, коды, адреса, номера телефонов, содержимое чужих сообщений — просто отреагируй в общих словах.
        Выбери эмоцию, которая подходит к реплике: ${Emotion.entries.joinToString { it.key }}.
    """.trimIndent()

    private val schema: BetaJsonOutputFormat.Schema = BetaJsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties", JsonValue.from(
                mapOf(
                    "emotion" to mapOf("type" to "string", "enum" to Emotion.entries.map { it.key }),
                    "comment" to mapOf("type" to "string"),
                )
            )
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("emotion", "comment")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()
}
