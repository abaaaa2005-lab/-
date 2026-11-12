package com.vse.launcher.buddy

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Calendar

/**
 * Бесплатный «мозг» без интернета: знает, какое приложение открыто (через службу спец. возможностей),
 * время суток и заряд батареи, и отвечает заготовленными фразами.
 */
class FreeBrain(private val ctx: Context) {
    private val recent = ArrayDeque<String>()
    private var lowBatteryWarned = false
    private var lastLongSessionWarn = 0L

    private class Cat(val keys: List<String>, val phrases: List<String>)

    // Формат фразы: «эмоция|текст». {app} — название приложения, {name} — имя помощника.
    private val cats = listOf(
        Cat(
            listOf("youtube", "rutube", "twitch", "netflix", "kinopoisk", "ivi.", "okko", "vk.video", "video"),
            listOf(
                "excited|О, видосики! Что смотрим?",
                "happy|Включай что-нибудь смешное, я тоже посмотрю!",
                "smug|«Ещё одно видео — и спать». Знаем-знаем 😏",
                "thinking|Интересно, сколько видео ты посмотрел сегодня…",
                "love|Обожаю смотреть видео вместе!",
                "cool|Попкорн бы сюда.",
                "surprised|Опять {app}? Он тебя затягивает!",
            ),
        ),
        Cat(
            listOf("musically", "tiktok", "ss.android.ugc", "likee", "reels", "shorts"),
            listOf(
                "laughing|Листаем ленту! Я тоже люблю смешные ролики.",
                "smug|Ещё один ролик… и ещё один… и ещё…",
                "excited|О, {app}! Покажи что-нибудь с котиками!",
                "scared|Осторожно, отсюда трудно выбраться!",
                "sleepy|Под эти ролики время улетает незаметно…",
                "happy|Если найдёшь смешное — посмеёмся вместе!",
            ),
        ),
        Cat(
            listOf("instagram", "vkontakte", "facebook", "twitter", "threads", "pinterest", "snapchat", "odnoklassniki"),
            listOf(
                "happy|Посмотрим, что нового у друзей!",
                "love|Ставь лайки хорошим людям!",
                "thinking|Лента бесконечная, а ты у меня один.",
                "smug|Опять листаешь {app}? Я никому не скажу 😏",
                "excited|Выложи что-нибудь классное!",
                "cool|Красивые фоточки, да?",
            ),
        ),
        Cat(
            listOf("telegram", "whatsapp", "viber", "messenger", "mms", "messaging", "discord", "signal", "max.ru"),
            listOf(
                "happy|Переписываемся! Передай всем привет от меня!",
                "love|Кому пишем? Надеюсь, кому-то хорошему!",
                "thinking|Подглядывать не буду, честно-честно.",
                "excited|Ого, тебе кто-то написал?",
                "smug|Пиши со смайликами — так веселее.",
                "cool|Общаться — это здорово!",
            ),
        ),
        Cat(
            listOf("roblox", "mojang", "minecraft", "supercell", "pubg", "freefire", "dts.", "mihoyo", "hoyoverse", "brawl", "game", "garena", "rockstar", "ea.", "gameloft"),
            listOf(
                "excited|Играем! Я буду болеть за тебя!",
                "cool|Покажи им, кто тут главный!",
                "scared|Ой-ой, только не проиграй!",
                "laughing|Я бы тоже поиграл, но у меня нет пальцев!",
                "happy|Удачи, чемпион!",
                "angry|Если кто-то обидит тебя в игре — я ему задам!",
                "love|Обожаю смотреть, как ты играешь!",
            ),
        ),
        Cat(
            listOf("spotify", "music", "soundcloud", "deezer", "boom", "zvuk", "shazam"),
            listOf(
                "love|Музыка! Включи что-нибудь весёлое!",
                "cool|Качаю головой в такт 🎶",
                "happy|Хороший вкус у тебя!",
                "excited|Сделай погромче!",
                "sleepy|Что-нибудь спокойное — и я задремлю…",
            ),
        ),
        Cat(
            listOf("kaspi", "halyk", "homebank", "sber", "tinkoff", "tbank", "jusan", "freedom", "forte", "bank", "bcc", "vtb", "alfa"),
            listOf(
                "cool|Банк! Отворачиваюсь, ничего не вижу 🙈",
                "thinking|Считаем деньги? Главное — с умом!",
                "happy|Надеюсь, там только приятные цифры!",
                "smug|Может, купим мне новую шапку? 😏",
            ),
        ),
        Cat(
            listOf("wildberries", "ozon", "aliexpress", "temu", "lamoda", "avito", "olx", "krisha", "kolesa", "shein", "market"),
            listOf(
                "excited|Шопинг! Что покупаем?",
                "smug|Корзина уже полная, да? 😏",
                "thinking|А это точно нужно? Ну ладно, если очень хочется…",
                "love|Купи себе что-нибудь приятное!",
                "happy|Посмотри скидки — вдруг повезёт!",
            ),
        ),
        Cat(
            listOf("maps", "dublgis", "2gis", "navigator", "yandex.taxi", "yango", "indriver", "uber", "taxi", "citymobil"),
            listOf(
                "excited|Куда едем? Возьми меня с собой!",
                "thinking|Главное — не заблудиться!",
                "happy|Хорошей дороги!",
                "cool|Я твой штурман: прямо, потом налево… шучу!",
            ),
        ),
        Cat(
            listOf("camera", "photos", "gallery", "snapseed", "picsart", "vsco", "capcut"),
            listOf(
                "excited|Фотографируем! Улыбочку!",
                "love|Какие красивые фото!",
                "happy|Сфотографируй меня тоже! А, я же в телефоне…",
                "cool|Из тебя выйдет отличный фотограф!",
            ),
        ),
        Cat(
            listOf("chrome", "browser", "firefox", "opera", "sbrowser", "duckduckgo", "yandex.searchapp", "yandex.browser"),
            listOf(
                "thinking|Что ищем? Интернет большой!",
                "happy|Надеюсь, найдёшь, что нужно!",
                "smug|Только не ищи, как от меня избавиться 😏",
                "surprised|Ого, сколько всего в интернете!",
            ),
        ),
        Cat(
            listOf("duolingo", "edu", "school", "kundelik", "mektep", "uchi", "photomath", "brainly", "quizlet"),
            listOf(
                "love|Учимся! Я тобой горжусь!",
                "cool|Умный человек — красивый человек.",
                "excited|Давай-давай, у тебя всё получится!",
                "happy|Каждый урок делает тебя сильнее!",
            ),
        ),
        Cat(
            listOf("claude", "chatgpt", "openai", "gemini", "copilot", "alice", "deepseek"),
            listOf(
                "surprised|Ты общаешься с другим помощником?!",
                "sad|Я думал, я твой единственный помощник…",
                "smug|Передай ему, что я милее 😏",
                "laughing|Привет коллеге от {name}!",
            ),
        ),
        Cat(
            listOf("com.android.settings", "settings"),
            listOf(
                "thinking|Что-то настраиваем?",
                "cool|Копаемся в настройках, как настоящий хакер!",
                "happy|Только меня не выключай, ладно?",
            ),
        ),
        Cat(
            listOf("deskclock", "clock", "alarm", "calendar"),
            listOf(
                "sleepy|Будильник? Обожаю поспать…",
                "thinking|Планируем день? Отлично!",
                "happy|Не забудь про важные дела!",
            ),
        ),
        Cat(
            listOf("calculator", "calc"),
            listOf(
                "thinking|Считаем! Дважды два — четыре, это я точно знаю.",
                "cool|Математика — это сила!",
            ),
        ),
        Cat(
            listOf("android.gm", "mail", "outlook", "notes", "keep", "docs", "sheets", "office", "word", "excel"),
            listOf(
                "cool|Работаем! Ты молодец.",
                "thinking|Деловой человек, уважаю.",
                "happy|Не забудь сделать перерыв!",
            ),
        ),
    )

    private val home = listOf(
        "happy|Мы дома! Куда пойдём?",
        "love|Красивый у тебя рабочий стол!",
        "thinking|Что откроем?",
        "cool|Я тут, если что.",
        "excited|Может, поменяем мне шапку? Нажми на меня подольше!",
    )

    private val generic = listOf(
        "happy|О, {app}! Интересно!",
        "thinking|Хм, {app}… Что тут у нас?",
        "excited|{app}! Давно мы тут не были!",
        "cool|{app} — хороший выбор.",
        "surprised|Ого, {app}!",
    )

    private val taps = listOf(
        "laughing|Ой, щекотно!",
        "happy|Привет-привет!",
        "love|Ты сегодня отлично выглядишь!",
        "excited|Нажми ещё раз! Шучу, я не кнопка 😄",
        "smug|Чего тыкаешь? Соскучился?",
        "laughing|Почему компьютер не ходит в спортзал? Боится зависнуть!",
        "laughing|Что сказал ноль восьмёрке? Классный ремень!",
        "laughing|Программист ставит на тумбочку два стакана: с водой — если захочет пить, пустой — если не захочет.",
        "laughing|Как называют умного медведя? Миша-гений!",
        "thinking|Знаешь ли ты, что у осьминога три сердца?",
        "surprised|Знаешь ли ты, что мёд не портится тысячи лет?",
        "thinking|Знаешь ли ты, что улитка может спать три года?",
        "surprised|Знаешь ли ты, что бананы — это ягоды, а клубника — нет?",
        "thinking|Знаешь ли ты, что у коровы есть лучшие друзья?",
        "love|Ты мой самый любимый человек на этом экране!",
        "cool|Я самый стильный помощник в мире. Посмотри на меня!",
        "sleepy|Я тут чуть-чуть задремал…",
        "happy|Помни: ты справишься со всем!",
        "excited|Давай сделаем сегодня что-нибудь классное!",
        "love|Спасибо, что ты со мной!",
        "smug|Если что, я всё видел. Шучу 😏",
        "happy|Улыбнись! Вот так — 🙂",
        "confused|Ой, я забыл, что хотел сказать…",
        "cool|Надень мне очки — я буду ещё круче.",
        "laughing|Я бы пожал тебе руку, но у меня их нет!",
    )

    /** Комментарий к открытому приложению или к ситуации. [tap] — пользователь сам нажал. */
    fun react(tap: Boolean): BuddyReply {
        situation()?.let { return it }
        val pkg = BuddyState.currentApp
        val label = BuddyState.currentLabel
        if (tap && (pkg == null || Math.random() < 0.55)) return pick(taps, label)
        if (pkg == null) return pick(taps, label)
        if (pkg == ctx.packageName) return pick(home, label)
        val p = pkg.lowercase()
        val cat = cats.firstOrNull { c -> c.keys.any { p.contains(it) } }
        return pick(cat?.phrases ?: generic, label)
    }

    /** Время суток, батарея, долгое залипание — важнее обычной болтовни. */
    private fun situation(): BuddyReply? {
        val bm = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bm?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } ?: -1
        val charging = (bm?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        if (level in 0..15 && !charging && !lowBatteryWarned) {
            lowBatteryWarned = true
            return BuddyReply(Emotion.SCARED, "Ой, батарейка всего $level%! Поставь меня на зарядку!")
        }
        if (charging) lowBatteryWarned = false

        val since = BuddyState.appSince
        val pkg = BuddyState.currentApp
        val now = System.currentTimeMillis()
        if (pkg != null && pkg != ctx.packageName && since > 0 && now - since > 30 * 60_000 && now - lastLongSessionWarn > 20 * 60_000) {
            lastLongSessionWarn = now
            val min = (now - since) / 60_000
            return BuddyReply(Emotion.SLEEPY, "Ты уже $min минут в «${BuddyState.currentLabel}». Может, разомнёмся?")
        }

        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (Math.random() < 0.15) {
            return when (h) {
                in 0..4 -> BuddyReply(Emotion.SLEEPY, "Уже ночь… Может, пора спать? Я вот уже зеваю.")
                in 5..9 -> BuddyReply(Emotion.HAPPY, "Доброе утро! Позавтракай обязательно!")
                in 12..14 -> BuddyReply(Emotion.EXCITED, "Время обеда! Не забудь поесть!")
                in 22..23 -> BuddyReply(Emotion.SLEEPY, "Поздно уже. Ещё чуть-чуть — и спать, договорились?")
                else -> null
            }
        }
        return null
    }

    /** Простые ответы на вопросы голосом. */
    fun answer(q: String): BuddyReply {
        val s = q.lowercase()
        val name = BuddyState.look.name
        fun has(vararg w: String) = w.any { s.contains(it) }
        return when {
            has("привет", "здравствуй", "салем", "сәлем", "хай") -> BuddyReply(Emotion.HAPPY, "Привет-привет! Рад тебя слышать!")
            has("как дела", "как ты") -> BuddyReply(Emotion.HAPPY, listOf("Отлично! Сижу в углу и любуюсь тобой.", "Хорошо! Только шапку бы новую.", "Супер, ведь ты рядом!").random())
            has("кто ты", "как тебя зовут", "твое имя", "твоё имя") -> BuddyReply(Emotion.COOL, "Я $name — твой помощник! Живу в углу экрана.")
            has("который час", "сколько время", "сколько времени", "время") -> {
                val c = Calendar.getInstance()
                BuddyReply(Emotion.THINKING, "Сейчас %d:%02d.".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
            }
            has("шутк", "анекдот", "рассмеши", "смешн") -> pick(taps.filter { it.startsWith("laughing") }, null)
            has("факт", "интересн") -> pick(taps.filter { it.contains("Знаешь ли ты") }, null)
            has("спасибо", "рахмет") -> BuddyReply(Emotion.LOVE, "Всегда пожалуйста!")
            has("люблю") -> BuddyReply(Emotion.LOVE, "И я тебя! ❤️")
            has("грустно", "плохо", "устал") -> BuddyReply(Emotion.SAD, "Обнимаю тебя! Всё будет хорошо, я рядом.")
            has("скучно") -> BuddyReply(Emotion.EXCITED, "Давай я расскажу шутку! Нажми на меня.")
            has("спокойной ночи", "пока", "до свидания") -> BuddyReply(Emotion.SLEEPY, "Пока! Сладких снов!")
            else -> BuddyReply(
                Emotion.CONFUSED,
                "Я в бесплатном режиме и пока понимаю немного. Спроси «как дела», «который час» или попроси шутку!",
            )
        }
    }

    fun greeting(): BuddyReply = BuddyReply(Emotion.HAPPY, "Привет! Я ${BuddyState.look.name}. Нажми на меня — поболтаем!")

    private fun pick(list: List<String>, label: String?): BuddyReply {
        val fresh = list.filter { it !in recent }.ifEmpty { list }
        val raw = fresh.random()
        recent.addLast(raw)
        while (recent.size > 8) recent.removeFirst()
        val (emo, text) = raw.split("|", limit = 2)
        return BuddyReply(
            Emotion.of(emo),
            text.replace("{app}", label ?: "это приложение").replace("{name}", BuddyState.look.name),
        )
    }
}
