package com.vse.launcher.buddy

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Эмоции помощника. [key] — значение, которое возвращает Claude. */
enum class Emotion(val key: String, val title: String, val emoji: String) {
    NEUTRAL("neutral", "Спокойствие", "🙂"),
    HAPPY("happy", "Радость", "😊"),
    LAUGHING("laughing", "Смех", "😆"),
    EXCITED("excited", "Восторг", "🤩"),
    LOVE("love", "Любовь", "😍"),
    SURPRISED("surprised", "Удивление", "😮"),
    THINKING("thinking", "Задумчивость", "🤔"),
    CONFUSED("confused", "Недоумение", "😕"),
    SAD("sad", "Грусть", "😢"),
    ANGRY("angry", "Злость", "😠"),
    SCARED("scared", "Испуг", "😱"),
    SLEEPY("sleepy", "Сонливость", "😴"),
    COOL("cool", "Крутость", "😎"),
    SMUG("smug", "Ехидство", "😏");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NEUTRAL
    }
}

/** Каталоги гардероба. Порядок важен: в настройках хранится индекс. */
object Wardrobe {
    val shapes = listOf("Круглый", "Капелька", "Кубик", "Призрак", "Котик", "Робот", "Слизень", "Звёздочка")
    val eyes = listOf("Мультяшные (как на картинке)", "Обычные", "Аниме", "Точки", "Огромные", "Сонные", "Робо-экран", "Овальные", "Пуговки")
    val hats = listOf(
        "Нет", "Кепка", "Кепка задом", "Шапка с помпоном", "Цилиндр", "Корона", "Колпак волшебника",
        "Ковбойская шляпа", "Праздничный колпак", "Поварской колпак", "Берет", "Шапка Санты",
        "Шлем викинга", "Кепка с пропеллером", "Нимб", "Рожки", "Бантик", "Ушки зайки", "Сомбреро",
        "Пиратская треуголка", "Панама", "Строительная каска", "Венок из цветов", "Тюбетейка",
        "Шапка-ушанка", "Котелок", "Диадема", "Ирокез", "Антенна-сердечко", "Капюшон-лягушка",
    )
    val headphones = listOf(
        "Нет", "Классические", "Студийные", "С кошачьими ушками", "Геймерские с микрофоном",
        "Капельки", "Ретро", "Диджейские", "Светящиеся неон", "Меховые наушники",
    )
    val glasses = listOf(
        "Нет", "Круглые", "Квадратные", "Солнечные", "Авиаторы", "Сердечки", "Звёздочки",
        "Монокль", "VR-шлем", "Пиксельные", "Маска героя", "Лыжная маска", "Кошачий глаз",
    )
    val accessories = listOf(
        "Нет", "Бабочка", "Галстук", "Шарф", "Жемчужное ожерелье", "Медаль", "Золотая цепь",
        "Бант", "Значок-звезда", "Бандана", "Колокольчик", "Плащ супергероя", "Ошейник с шипами",
    )
    val faces = listOf("Ничего", "Усы", "Веснушки", "Родинка", "Пластырь", "Клыки", "Язычок", "Борода")
    val auras = listOf("Нет", "Звёздочки", "Свечение", "Искорки", "Сердечки", "Снежинки", "Ноты", "Пузыри", "Огоньки")
    val personalities = listOf(
        "Добряк" to "добрый, заботливый и поддерживающий друг; радуешься за пользователя и мягко шутишь",
        "Саркастичный" to "саркастичный остряк с сухим юмором; подкалываешь по-дружески, но никогда не грубишь",
        "Ботаник" to "умник-ботаник, обожаешь факты и делишься короткими интересными фактами о том, что видишь",
        "Тусовщик" to "весёлый энергичный тусовщик, говоришь на молодёжном сленге и всему радуешься",
        "Мудрец" to "спокойный мудрец, говоришь коротко и философски, иногда как восточная притча",
        "Пират" to "пират, говоришь как морской волк: «Йо-хо-хо», «тысяча чертей», «юнга»",
        "Котик" to "котик: мурлычешь, говоришь «мяу», интересуешься едой и сном",
        "Тренер" to "строгий, но мотивирующий тренер; подбадриваешь и следишь, чтобы пользователь не залипал",
    )
    val models = listOf(
        "claude-opus-5-5" to "Claude Opus 5.5 — самый умный",
        "claude-sonnet-5-5" to "Claude Sonnet 5.5 — быстрее и дешевле",
        "claude-haiku-5-5" to "Claude Haiku 5.5 — самый быстрый и дешёвый",
    )
    val intervals = listOf(15, 30, 60, 120, 300, 0) // 0 — только по нажатию
    /** Режимы голоса в порядке показа: индекс режима → подпись. */
    val voiceModes = listOf(3 to "Нейроголос «Ирина»", 0 to "Мультяшный лепет", 1 to "Голос телефона", 2 to "Без голоса")
    val corners = listOf("Справа внизу", "Слева внизу", "Справа вверху", "Слева вверху")

    /** 0 — радуга (переливается). */
    const val RAINBOW = 0L
    val palette = listOf(
        0xFF4A4A4A, 0xFF6C8CFF, 0xFF4F7DFF, 0xFF3BB2F6, 0xFF2EC4B6, 0xFF34C759, 0xFF9BE15D, 0xFFFFD93D,
        0xFFFFB347, 0xFFFF8A5B, 0xFFFF5E5E, 0xFFFF6FB5, 0xFFE38CFF, 0xFFA66CFF, 0xFF7B5CE0,
        0xFF8D6E63, 0xFFB0BEC5, 0xFFFFFFFF, 0xFF2B2B35, 0xFFFFC0CB, 0xFFC8F7DC, RAINBOW,
    )
}

/** Внешний вид и поведение помощника. */
data class BuddyLook(
    val name: String = "Дружок",
    val bodyColor: Long = 0xFF4A4A4A,
    val belly: Boolean = false,
    val bellyColor: Long = 0xFFFFFFFF,
    val faceColor: Long = 0xFFFFFFFF,
    val shape: Int = 0,
    val eyes: Int = 0,
    val eyeColor: Long = 0xFF23233A,
    val blush: Boolean = false,
    val hat: Int = 0,
    val hatColor: Long = 0xFFFF5E5E,
    val headphones: Int = 0,
    val phonesColor: Long = 0xFF2B2B35,
    val glasses: Int = 0,
    val glassesColor: Long = 0xFF2B2B35,
    val accessory: Int = 0,
    val accColor: Long = 0xFFFFD93D,
    val face: Int = 0,
    val aura: Int = 1,
    val sizeDp: Int = 84,
    val corner: Int = 0,
    val opacity: Float = 1f,
    // Звук: 0 — мультяшный лепет, 1 — голос телефона, 2 — без голоса, 3 — нейроголос «Ирина»
    val voiceMode: Int = 3,
    val voiceName: String = "",
    val voicePitch: Float = 1.0f,
    val voiceRate: Float = 1.0f,
    val chirps: Boolean = true,
    // Поведение. smart — думает через Claude (нужен ключ), иначе бесплатный режим
    val smart: Boolean = false,
    val personality: Int = 0,
    val interval: Int = 60,
    val model: Int = 0,
    val apiKey: String = "",
    val bubbleSeconds: Int = 8,
)

/** Общее состояние помощника: его видят и экран настроек, и сервис-оверлей (один процесс). */
object BuddyState {
    var look by mutableStateOf(BuddyLook())
    var running by mutableStateOf(false)
    var seeing by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var emotion by mutableStateOf(Emotion.HAPPY)
    var talking by mutableStateOf(false)
    var bubble by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    /** Какое приложение сейчас открыто (сообщает служба спец. возможностей). */
    var currentApp by mutableStateOf<String?>(null)
    var currentLabel by mutableStateOf<String?>(null)
    var appSince = 0L
    /** Служба спец. возможностей включена — значит, Дружок знает открытое приложение. */
    var watchingApps by mutableStateOf(false)
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        look = BuddyStore.load(ctx)
    }

    fun update(ctx: Context, f: (BuddyLook) -> BuddyLook) {
        look = f(look)
        BuddyStore.save(ctx, look)
    }
}

object BuddyStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun load(ctx: Context): BuddyLook {
        val s = prefs(ctx).getString("look", null) ?: return BuddyLook()
        return runCatching {
            val o = JSONObject(s)
            val d = BuddyLook()
            BuddyLook(
                name = o.optString("name", d.name),
                bodyColor = o.optLong("bodyColor", d.bodyColor),
                belly = o.optBoolean("belly", d.belly),
                bellyColor = o.optLong("bellyColor", d.bellyColor),
                faceColor = o.optLong("faceColor", d.faceColor),
                shape = o.optInt("shape", d.shape),
                eyes = o.optInt("eyes", d.eyes),
                eyeColor = o.optLong("eyeColor", d.eyeColor),
                blush = o.optBoolean("blush", d.blush),
                hat = o.optInt("hat", d.hat),
                hatColor = o.optLong("hatColor", d.hatColor),
                headphones = o.optInt("headphones", d.headphones),
                phonesColor = o.optLong("phonesColor", d.phonesColor),
                glasses = o.optInt("glasses", d.glasses),
                glassesColor = o.optLong("glassesColor", d.glassesColor),
                accessory = o.optInt("accessory", d.accessory),
                accColor = o.optLong("accColor", d.accColor),
                face = o.optInt("face", d.face),
                aura = o.optInt("aura", d.aura),
                sizeDp = o.optInt("sizeDp", d.sizeDp),
                corner = o.optInt("corner", d.corner),
                opacity = o.optDouble("opacity", d.opacity.toDouble()).toFloat(),
                voiceMode = o.optInt("voiceMode2", d.voiceMode),
                voiceName = o.optString("voiceName", d.voiceName),
                // Ключи «2»: старые высота 1.3 и скорость 1.05 звучали плохо — начинаем с нормальных
                voicePitch = o.optDouble("voicePitch2", d.voicePitch.toDouble()).toFloat(),
                voiceRate = o.optDouble("voiceRate2", d.voiceRate.toDouble()).toFloat(),
                smart = o.optBoolean("smart", o.optString("apiKey").isNotBlank()),
                chirps = o.optBoolean("chirps", d.chirps),
                personality = o.optInt("personality", d.personality),
                interval = o.optInt("interval", d.interval),
                model = o.optInt("model", d.model),
                apiKey = o.optString("apiKey", d.apiKey),
                bubbleSeconds = o.optInt("bubbleSeconds", d.bubbleSeconds),
            )
        }.getOrDefault(BuddyLook())
    }

    fun save(ctx: Context, l: BuddyLook) {
        val o = JSONObject()
            .put("name", l.name).put("bodyColor", l.bodyColor).put("belly", l.belly).put("bellyColor", l.bellyColor).put("faceColor", l.faceColor)
            .put("shape", l.shape).put("eyes", l.eyes).put("eyeColor", l.eyeColor).put("blush", l.blush)
            .put("hat", l.hat).put("hatColor", l.hatColor)
            .put("headphones", l.headphones).put("phonesColor", l.phonesColor)
            .put("glasses", l.glasses).put("glassesColor", l.glassesColor)
            .put("accessory", l.accessory).put("accColor", l.accColor)
            .put("face", l.face).put("aura", l.aura).put("sizeDp", l.sizeDp).put("corner", l.corner)
            .put("opacity", l.opacity.toDouble())
            .put("voiceMode2", l.voiceMode).put("voiceName", l.voiceName).put("smart", l.smart)
            .put("voicePitch2", l.voicePitch.toDouble()).put("voiceRate2", l.voiceRate.toDouble())
            .put("chirps", l.chirps).put("personality", l.personality).put("interval", l.interval)
            .put("model", l.model).put("apiKey", l.apiKey).put("bubbleSeconds", l.bubbleSeconds)
        prefs(ctx).edit().putString("look", o.toString()).apply()
    }

    /** Случайный образ — для кнопки «Удиви меня». */
    fun random(base: BuddyLook): BuddyLook {
        val colors = Wardrobe.palette
        fun c() = colors.random().let { if (it == Wardrobe.RAINBOW) 0xFFFFD93D else it }
        return base.copy(
            bodyColor = colors.random(),
            belly = Math.random() < 0.5,
            bellyColor = listOf(0xFFFFFFFF, 0xFFFFF3D6, 0xFFE8F0FF, 0xFFFFE4EC).random(),
            shape = Wardrobe.shapes.indices.random(),
            eyes = Wardrobe.eyes.indices.random(),
            hat = Wardrobe.hats.indices.random(),
            hatColor = c(),
            headphones = Wardrobe.headphones.indices.random(),
            phonesColor = c(),
            glasses = if (Math.random() < 0.5) 0 else Wardrobe.glasses.indices.random(),
            glassesColor = c(),
            accessory = Wardrobe.accessories.indices.random(),
            accColor = c(),
            face = if (Math.random() < 0.6) 0 else Wardrobe.faces.indices.random(),
            aura = if (Math.random() < 0.5) 0 else Wardrobe.auras.indices.random(),
        )
    }
}
