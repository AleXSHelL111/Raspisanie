package com.alexshell.raspisanie.ai

import com.alexshell.raspisanie.AppViewModel
import com.alexshell.raspisanie.data.Homework
import com.alexshell.raspisanie.data.Lesson
import com.alexshell.raspisanie.data.LessonKind
import com.alexshell.raspisanie.data.TimeSlot
import com.alexshell.raspisanie.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * KGMU AI — простой помощник без интернета: разбирает вопрос по ключевым словам
 * и отвечает по расписанию, ДЗ и данным группы. Те же правила — на сайте (aiAnswer в index.html).
 */
object Assistant {

    /** Действие, которое чат выполняет вместе с ответом. */
    sealed class Act {
        data class AddHw(val lessonKey: String, val text: String) : Act()
        data class DoneHw(val ids: List<Long>) : Act()
        data class Mode(val mode: ThemeMode) : Act()
        data class Open(val target: String) : Act()
    }

    /** Ответ: текст (с **жирным**), подпись «размышления» и необязательное действие. */
    data class Reply(val text: String, val think: String? = null, val act: Act? = null)

    private sealed class When {
        data class Day(val d: LocalDate) : When()
        data class Range(val from: LocalDate, val to: LocalDate) : When()
    }

    private const val NW = "(?![а-яa-z0-9])"
    private const val PW = "(?<![а-яa-z0-9])"

    private val STOP = listOf(
        "пар", "занят", "расписан", "когда", "како", "какая", "какие", "сколько", "завтра", "сегодня", "послезавтра", "вчера",
        "следующ", "домашк", "задан", "задал", "время", "времен", "начина", "начнет", "закончи", "конча", "будет", "есть", "меня", "надо",
        "нужно", "добав", "запиш", "отмет", "сделал", "выполн", "покаж", "скаж", "подскаж", "неделе", "недел", "сейчас", "идет", "потом",
        "дальше", "лекци", "практик", "аудитор", "кабинет", "где", "через", "осталось", "привет", "спасибо", "пожалуйста", "там", "что",
        "это", "эта", "этой", "дежурн", "объявлен", "отработ", "пропуск", "первая", "последн", "утра", "вечер", "день", "дня", "дней",
        "понедельн", "вторн", "сред", "четверг", "пятниц", "суббот", "воскресен", "январ", "феврал", "март", "апрел", "мая", "июн", "июл",
        "август", "сентябр", "октябр", "ноябр", "декабр", "выполненн", "сделанн", "готов", "все", "всё", "мне", "нас", "нам", "про", "для",
    )
    private val WD = listOf("понедельн", "вторн", "сред", "четверг", "пятниц", "суббот", "воскресен")
    private val WD_SHORT_Q = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val MON = listOf("январ", "феврал", "март", "апрел", "ма[йя]", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")
    private val WD_ACC = listOf("понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье")
    private val WD_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val WD_FULL = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    private val MON_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

    /** Студенческие сокращения → слова из названий дисциплин. */
    private val SYN = listOf(
        Regex("физ-?ра|физкульт\\S*|физо\\S*") to "физической культуре",
        Regex("латын\\S*|латин\\S*") to "латинская",
        Regex("патан\\S*|патанатом\\S*") to "патологическая анатомия",
        Regex("патфиз\\S*|патофиз\\S*") to "патофизиология",
        Regex("топан\\S*|топочк\\S*|${PW}топ[ауеы]?$NW") to "топографическая анатомия",
        Regex("фарм[ау]$NW|фармак$NW") to "фармакология",
        Regex("микр[ауеы]$NW|микроб$NW") to "микробиология",
        Regex("пропед\\S*") to "пропедевтика",
        Regex("хирург\\S*") to "хирургия",
        Regex("англ\\S*|иностран\\S*|инглиш") to "межкультурная коммуникация",
    )

    private val FAQ = listOf(
        "(загруз|обнов|нов)\\S* (файл|расписан)|файл\\S* расписан|xlsx|эксел" to
            "Новый файл загружается так: ⚙️ **Настройки** (шестерёнка на главном экране) → «Файл расписания» → **«Загрузить новый .xlsx»**. Подойдёт файл с сайта учебного отдела или из чата группы. Можно и открыть .xlsx из Telegram через «Открыть с помощью». ДЗ и правки сохранятся 👌",
        "(смен|помен|выбр|друг)\\S* (групп|поток)|(групп|поток)\\S* (смен|помен)" to
            "Группа и поток меняются в ⚙️ **Настройках** — разделы «Номер группы» и «Поток». Там же выбираются дисциплины по выбору.",
        "по выбору|электив" to "Откройте ⚙️ **Настройки → «Дисциплины по выбору»** и отметьте свои — в расписании останутся только они.",
        "уведомл|напомина|оповещ" to
            "Уведомления — в ⚙️ **Настройках → «Уведомления»**: напоминание перед парой (за сколько минут — выбираете сами), вечерняя сводка на завтра, напоминания о ДЗ и новости группы 🔔 Если уведомления опаздывают, разрешите приложению работать без ограничений батареи — кнопка там же.",
        "виджет" to "Добавьте виджет: долгое нажатие на рабочий стол → «Виджеты» → «КГМУ расписание». Он показывает текущую и следующую пару.",
        "(профил|аккаунт|регистр|войти|вход|логин|парол)" to
            "Профиль создаётся в ⚙️ **Настройках → вкладка «Профиль»**: полное имя, юзернейм и пароль. С профилем ДЗ, правки и настройки хранятся в облаке, а на новом устройстве достаточно войти — всё загрузится само ☁️",
        "(синхрон|облак|перенос|перенест|резерв|копи|бэкап|backup)" to
            "Синхронизация работает через профиль: изменения сохраняются в облако автоматически. Перенести данные можно и файлом — ⚙️ **Настройки → «Профиль» → «Перенос через файл»**.",
        "(созда|сделат|завест)\\S* групп|групп\\S* (созда|сделат)|одногрупп|добав\\S* (в групп|участ|человек|одногрупп)" to
            "Группа создаётся во вкладке **«Моя группа»** (нужен профиль). Создатель 👑 и староста ⭐ добавляют одногруппников по юзернейму, назначают роли и дежурных.",
        "старост" to "Старосту назначает создатель группы во вкладке **«Моя группа» → «Участники»**. Староста может добавлять людей, назначать роли и дежурных.",
        "(объявлен|новост)" to "Объявления пишут все участники во вкладке **«Моя группа» → «Объявления»**. Они хранятся 3 недели, а потом удаляются сами 📢",
        "(отработ|пропуск)" to "Пропуски и отработки — во вкладке **«Моя группа» → «Отработки»**: отмечайте пропущенную пару, время и место отработки. Их видите только вы.",
        "дежурн" to "Дежурного на пару назначают создатель или староста: откройте пару в расписании → «Дежурный на пару». Дежурного видят все участники группы 🧹",
        "(редакт|измен|правк|добав|удал|перенес)\\S* (пар|занят|урок)|(пар|занят)\\S* (редакт|измен|добав|удал)" to
            "Чтобы изменить пару, нажмите на неё в расписании → «Изменить». Новую пару можно добавить на вкладке **«Неделя»** кнопкой «+». Правки можно применить ко всем таким же парам.",
        "(дз|домашк|задани)" to "ДЗ добавляется так: нажмите на пару → впишите задание (к этой или следующей паре). Или попросите меня: «**добавь дз по анатомии: выучить кости черепа**» ✍️",
        "(язык|language|англ)" to "Язык меняется кнопкой с буквами языка (RU) на главном экране — рядом с шестерёнкой. Доступно 7 языков 🌍",
        "(тем[аыуе]|цвет|оформлен|темн|светл)" to "Тема и цвета — в ⚙️ **Настройках → «Тема оформления»**: тёмная, светлая или как в системе, плюс несколько неоновых палитр 🎨 Могу и сам: «включи светлую тему».",
        "(сайт|веб|браузер|айфон|iphone|ios)" to "Сайт приложения — **kgmuraspisanie.ru**. На iPhone откройте его в Safari → «Поделиться» → «На экран Домой», и он будет работать как приложение 📱",
        "(андроид|android|apk|скача|установ|обновлен)" to "Свежую версию для Android публикуем в Telegram-канале **t.me/kgmuraspisanie** — там же новости обновлений.",
        "(pro|про верси|премиум|подписк)" to "PRO-версия в разработке: будут умные функции ИИ для расписания и не только 😉 Следите за новостями в **t.me/kgmuraspisanie**.",
    ).map { (re, text) -> Regex(re) to text }

    const val HELP = "Я **KGMU AI** — ваш помощник по расписанию 🤖 Вот что я умею:\n\n" +
        "📅 «Какие пары завтра?», «Что в пятницу?», «Расписание на неделю»\n" +
        "⏱ «Сколько до пары?», «Когда анатомия?», «Когда закончатся пары?»\n" +
        "📝 «Что задали на завтра?», «ДЗ по биохимии»\n" +
        "✍️ «Добавь дз по анатомии: выучить кости черепа», «Отметь дз по анатомии сделанным»\n" +
        "⚙️ «Открой настройки», «Включи светлую тему»\n" +
        "❓ И отвечу на вопросы о приложении: «Как включить уведомления?», «Как создать группу?»"

    val SUGGEST = listOf("Какие пары завтра?", "Сколько до пары?", "Что задали?", "Расписание на неделю", "Что ты умеешь?")

    /** Части ответа: текст, **жирный** и _(курсив)_. */
    data class Part(val text: String, val bold: Boolean, val italic: Boolean)

    fun parts(text: String): List<Part> {
        val out = mutableListOf<Part>()
        var last = 0
        Regex("\\*\\*([^*]+)\\*\\*|_(\\([^)]*\\))_").findAll(text).forEach { m ->
            if (m.range.first > last) out += Part(text.substring(last, m.range.first), false, false)
            out += if (m.groupValues[1].isNotEmpty()) Part(m.groupValues[1], true, false) else Part(m.groupValues[2], false, true)
            last = m.range.last + 1
        }
        if (last < text.length) out += Part(text.substring(last), false, false)
        return out
    }

    fun plainLength(text: String) = parts(text).sumOf { it.text.length }

    fun norm(s: String): String = s.lowercase().replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}:.\\-\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    // ---------- Форматирование ----------

    private fun fmt(m: Int) = TimeSlot.fmt(m)
    private fun wdIdx(d: LocalDate) = d.dayOfWeek.value - 1
    private fun dm(d: LocalDate) = "${d.dayOfMonth} ${MON_GEN[d.monthValue - 1]}"
    private fun kindRu(k: LessonKind) = when (k) { LessonKind.LECTURE -> "лекция"; LessonKind.PRACTICE -> "практика"; LessonKind.ELECTIVE -> "по выбору" }
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun plural(n: Long, one: String, few: String, many: String): String {
        val n10 = n % 10; val n100 = n % 100
        return if (n10 == 1L && n100 != 11L) one else if (n10 in 2..4 && n100 !in 12..14) few else many
    }
    private fun pars(n: Int) = "$n ${plural(n.toLong(), "пара", "пары", "пар")}"

    private fun dayRu(d: LocalDate, t: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(t, d)
        return when (diff) {
            0L -> "сегодня"; 1L -> "завтра"; 2L -> "послезавтра"; -1L -> "вчера"
            else -> (if (diff in 1L..6L) "в " + WD_ACC[wdIdx(d)] else WD_SHORT[wdIdx(d)]) + ", " + dm(d)
        }
    }

    private fun dayTitle(d: LocalDate, t: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(t, d)
        val head = cap(dayRu(d, t).substringBefore(','))
        return when {
            diff in 0L..2L -> "$head, ${WD_SHORT[wdIdx(d)]} ${dm(d)}"
            diff in 3L..6L -> "$head, ${dm(d)}"
            else -> head
        }
    }

    private fun place(l: Lesson) = if (l.online) " · онлайн" else l.location?.takeIf { it.isNotBlank() }?.let { " · 📍 $it" } ?: ""
    private fun line(l: Lesson) = "• ${l.timeText} **${l.subject}** · ${kindRu(l.kind)}${place(l)}"

    /** «через 2 ч 15 мин». */
    private fun inTime(from: LocalDateTime, to: LocalDateTime): String {
        val m = Math.round(Duration.between(from, to).seconds / 60.0)
        if (m < 1) return "меньше чем через минуту"
        val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
        val parts = buildList {
            if (d > 0) add("$d ${plural(d, "день", "дня", "дней")}")
            if (h > 0) add("$h ч")
            if (mm > 0 && d == 0L) add("$mm мин")
        }
        return "через " + parts.joinToString(" ")
    }
    private fun left(from: LocalDateTime, to: LocalDateTime) = inTime(from, to).removePrefix("через ")

    // ---------- Разбор вопроса ----------

    private fun parseDate(t: String, today: LocalDate): When? {
        if ("послезавтра" in t) return When.Day(today.plusDays(2))
        if ("завтр" in t) return When.Day(today.plusDays(1))
        if ("вчера" in t) return When.Day(today.minusDays(1))
        if ("сегодня" in t) return When.Day(today)
        Regex("через (\\d+|один|два|три|неделю) ?(дн|день|дня|недел)").find(t)?.let { m ->
            val n = mapOf("один" to 1, "два" to 2, "три" to 3, "неделю" to 1)[m.groupValues[1]] ?: m.groupValues[1].toInt()
            return When.Day(today.plusDays(if (m.groupValues[2].startsWith("недел")) 7L * n else n.toLong()))
        }
        Regex("\\b(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?\\b").find(t)?.let { m ->
            val (dd, mm, yy) = m.destructured
            val y = if (yy.isNotEmpty()) yy.toInt().let { if (it < 100) 2000 + it else it } else today.year
            val d = runCatching { LocalDate.of(y, mm.toInt(), dd.toInt()) }.getOrNull() ?: return@let
            return When.Day(if (yy.isEmpty() && d < today.minusDays(180)) d.plusYears(1) else d)
        }
        Regex("(\\d{1,2}) (${MON.joinToString("|")})").find(t)?.let { m ->
            val mon = MON.indexOfFirst { Regex("^$it").containsMatchIn(m.groupValues[2]) } + 1
            val d = runCatching { LocalDate.of(today.year, mon, m.groupValues[1].toInt()) }.getOrNull() ?: return@let
            return When.Day(if (d < today.minusDays(180)) d.plusYears(1) else d)
        }
        val next = Regex("следующ|будущ").containsMatchIn(t)
        var wd = WD.indexOfFirst { it in t }
        if (wd < 0) wd = WD_SHORT_Q.indexOfFirst { Regex("(^| )$it( |$)").containsMatchIn(t) }
        if (wd >= 0) {
            var d = today.plusDays(((wd + 1 - today.dayOfWeek.value + 7) % 7).toLong())
            val nextMonday = today.with(DayOfWeek.MONDAY).plusWeeks(1)
            if (next && d < nextMonday) d = d.plusWeeks(1)
            return When.Day(d)
        }
        val mon = today.with(DayOfWeek.MONDAY)
        if ("недел" in t && Regex("(эт|текущ|на неделе|всю неделю|неделю$)").containsMatchIn(t) && !next) return When.Range(mon, mon.plusDays(6))
        if ("недел" in t && next) return When.Range(mon.plusWeeks(1), mon.plusDays(13))
        return null
    }

    /** Предмет из вопроса — по началу слов (анатом → Анатомия). */
    private fun subjectOf(text: String, lessons: List<Lesson>): String? {
        var t = text
        SYN.forEach { (re, w) -> t = re.replace(t, w) }
        val toks = t.split(' ').filter { w -> w.length >= 3 && STOP.none { w.startsWith(it) } }
        if (toks.isEmpty()) return null
        val list = LinkedHashMap<String, String>()
        lessons.forEach { l -> list.getOrPut(l.subject) { norm(l.subject + " " + l.fullSubject + " " + (l.elective ?: "")) } }
        var best: String? = null
        var bestScore = 0
        for ((subj, st) in list) {
            val words = st.split(' ').filter { it.length >= 3 }
            var score = 0
            for (tk in toks) {
                var b = 0
                for (w in words) {
                    var i = 0
                    while (i < tk.length && i < w.length && tk[i] == w[i]) i++
                    if (i >= 4 || (i >= 3 && (i == tk.length || i == w.length))) b = maxOf(b, i)
                }
                score += b
            }
            if (score > bestScore) { bestScore = score; best = subj }
        }
        return best
    }

    // ---------- Ответы ----------

    private class Ctx(val vm: AppViewModel, val now: LocalDateTime) {
        val today: LocalDate = now.toLocalDate()
        val lessons: List<Lesson> = vm.lessons
        fun on(d: LocalDate) = lessons.filter { it.date == d }
        val hw: List<Homework> get() = vm.groupHomework()
    }

    private fun dayAnswer(c: Ctx, d: LocalDate, kind: LessonKind?): String {
        var list = c.on(d)
        if (kind != null) list = list.filter { it.kind == kind }
        val title = dayTitle(d, c.today)
        if (list.isEmpty()) {
            val any = c.on(d).size
            if (kind != null && any > 0) return "$title: ${if (kind == LessonKind.LECTURE) "лекций" else "практик"} нет, но есть ${pars(any)}."
            val nxt = c.lessons.firstOrNull { it.date > d }
            return "$title пар нет 🎉 Можно отдыхать!" + (nxt?.let { "\nСледующие занятия — ${dayRu(it.date, c.today)}, первая в ${fmt(it.start)}." } ?: "")
        }
        val hw = c.hw.count { !it.done && it.dueDate == d }
        return "📅 **$title** — ${pars(list.size)}:\n${list.joinToString("\n") { line(it) }}\n\n" +
            "Начало в ${fmt(list.first().start)}, конец в ${fmt(list.last().end)}." +
            (if (hw > 0) "\n📝 К этому дню есть ДЗ: $hw. Спросите «что задали ${dayRu(d, c.today).substringBefore(',')}?»" else "")
    }

    private fun weekAnswer(c: Ctx, from: LocalDate, to: LocalDate): String {
        val lines = mutableListOf<String>()
        var total = 0
        var d = from
        while (d <= to) {
            val list = c.on(d)
            total += list.size
            if (!(d.dayOfWeek.value >= 6 && list.isEmpty())) {
                lines += if (list.isNotEmpty()) "**${WD_SHORT[wdIdx(d)]} ${d.dayOfMonth}** — ${pars(list.size)}, ${fmt(list.first().start)}–${fmt(list.last().end)}"
                else "**${WD_SHORT[wdIdx(d)]} ${d.dayOfMonth}** — выходной 🎉"
            }
            d = d.plusDays(1)
        }
        val head = if (c.today in from..to) "Эта неделя" else "Следующая неделя"
        if (total == 0) return "🗓 $head: пар нет 🎉"
        val sch = c.vm.schedule
        val wt = if (sch != null && sch.hasWeekParity) " · ${sch.weekType(from)}-я неделя" else ""
        return "🗓 **$head** (${dm(from)} – ${dm(to)})$wt:\n${lines.joinToString("\n")}\n\nВсего ${pars(total)}. Спросите про любой день — расскажу подробно."
    }

    private fun hwAnswer(c: Ctx, w: When?, subj: String?): String {
        var list = c.hw.filter { !it.done }.sortedWith(compareBy({ it.dueDate }, { it.dueStart }))
        val over = list.filter { it.dueDate < c.today }
        list = list.filter { it.dueDate >= c.today }
        when (w) {
            is When.Day -> list = list.filter { it.dueDate == w.d }
            is When.Range -> list = list.filter { it.dueDate in w.from..w.to }
            null -> {}
        }
        if (subj != null) list = list.filter { it.subject == subj }
        val what = (subj?.let { " по предмету **$it**" } ?: "") +
            when (w) { is When.Day -> " на ${dayRu(w.d, c.today)}"; is When.Range -> " на неделю"; null -> "" }
        val overNote = over.isNotEmpty() && subj == null && w == null
        if (list.isEmpty()) {
            return "Ничего не задано$what ✨" + (if (overNote) "\nНо есть просроченные: ${over.size} — загляните во вкладку ДЗ." else "") +
                "\nДобавить задание можно так: «добавь дз по анатомии: выучить кости черепа»."
        }
        return "📝 Задания$what:\n" + list.take(10).joinToString("\n") { h ->
            "• **${h.subject}** — ${dayRu(h.dueDate, c.today)}, ${fmt(h.dueStart)}:\n   ${h.text}" + (h.author?.let { " _(от $it)_" } ?: "")
        } + (if (list.size > 10) "\n…и ещё ${list.size - 10}" else "") + (if (overNote) "\n⚠️ Просрочено: ${over.size}" else "")
    }

    fun answer(vm: AppViewModel, raw: String, now: LocalDateTime = LocalDateTime.now()): Reply {
        val t = norm(raw)
        val c = Ctx(vm, now)
        val today = c.today
        fun has(re: String) = Regex(re).containsMatchIn(t)
        val name = vm.cloud.user?.fullName?.substringBefore(' ').orEmpty()
        if (t.isEmpty()) return Reply(HELP)
        if (c.lessons.isEmpty()) return Reply("Пока не вижу вашего расписания 🤔 Загрузите файл .xlsx в настройках и выберите группу — тогда смогу отвечать про пары.")
        val w = parseDate(t, today)
        val day = (w as? When.Day)?.d
        val subj = subjectOf(t, c.lessons)
        val cur = c.on(today).firstOrNull { it.startAt <= now && now < it.endAt }
        val next = c.lessons.firstOrNull { it.startAt > now }
        val pick = { a: List<String> -> a.random() }

        // --- Действия ---
        if (has("(добав|запиш|запомн|созда|сохран)") && has("(дз|домашк|задани|задал)") && !has("^(а )?(как|где|можно ли)")) {
            if (subj == null) return Reply("По какому предмету записать задание? Напишите, например: «**добавь дз по анатомии: выучить кости черепа**».")
            val colon = Regex("[:—–]|\\s-\\s").find(raw)
            var text = colon?.let { raw.substring(it.range.last + 1).trimStart(' ', '-', '—', '–', ':') }.orEmpty()
            if (text.isBlank()) {
                val words = raw.trim().split(Regex("\\s+"))
                val si = words.indexOfFirst { subjectOf(norm(it), c.lessons) == subj }
                text = if (si >= 0) words.drop(si + 1).filter { wd -> !Regex("^(на|к)$", RegexOption.IGNORE_CASE).matches(wd) && parseDate(norm(wd), today) == null }.joinToString(" ") else ""
            }
            if (text.isBlank()) return Reply("Что задали по предмету **$subj**? Напишите текст после двоеточия, например: «добавь дз по фарме: выучить 10 препаратов».")
            val target = day?.let { d -> c.lessons.firstOrNull { it.subject == subj && it.date == d } } ?: vm.nextBySubject(subj, now)
                ?: return Reply("Не нашёл впереди пар по предмету **$subj** 🤷 Задание некуда привязать.")
            return Reply(
                "✅ Записал ДЗ по предмету **${target.subject}** к паре ${dayRu(target.date, today)} в ${fmt(target.start)}:\n«${text.trim()}»\n\nНапомню о нём, если включены уведомления о ДЗ 🔔",
                think = "Записываю", act = Act.AddHw(target.key, text.trim()),
            )
        }
        if (has("(отмет|сделал|выполнил|закрой|готово)") && (has("(дз|домашк|задани)") || subj != null) && !has("^(а )?(как|где)")) {
            var list = c.hw.filter { !it.done }.sortedWith(compareBy({ it.dueDate }, { it.dueStart }))
            if (subj != null) list = list.filter { it.subject == subj }
            if (day != null) list = list.filter { it.dueDate == day }
            if (list.isEmpty()) return Reply(if (subj != null) "По предмету **$subj** невыполненных заданий нет 👍" else "Невыполненных заданий нет 👍")
            val all = has("${PW}все$NW")
            val done = if (all) list else list.take(1)
            return Reply(
                if (all) "✅ Отметил выполненными все задания${subj?.let { " по предмету **$it**" } ?: ""}: ${done.size}. Так держать! 💪"
                else "✅ Отметил выполненным: **${done[0].subject}** — «${done[0].text}». Молодец! 💪" + (if (list.size > 1) "\nОсталось ещё ${list.size - 1}." else ""),
                think = "Отмечаю", act = Act.DoneHw(done.map { it.id }),
            )
        }
        if (has("(включ|постав|сдела|смен|переключ|хочу)") && has("(тем[ауеыо]|режим|оформлен)") || has("^(светл|темн)\\S* тем")) {
            val mode = when {
                has("светл|бел|дневн") -> ThemeMode.LIGHT
                has("темн|черн|ночн") -> ThemeMode.DARK
                has("систем|авто") -> ThemeMode.SYSTEM
                else -> null
            }
            if (mode != null) return Reply(
                when (mode) { ThemeMode.LIGHT -> "Готово ☀️ Включил светлую тему."; ThemeMode.DARK -> "Готово 🌙 Включил тёмную тему."; else -> "Готово 🌓 Тема теперь как в системе." },
                act = Act.Mode(mode),
            )
        }
        if (has("(открой|перейди|зайди|запусти|покажи вкладку|включи вкладку)")) {
            val map = listOf(
                "настройк" to ("settings" to "Открываю настройки ⚙️"), "профил" to ("profile" to "Открываю профиль 👤"),
                "групп" to ("group" to "Открываю «Мою группу» 👥"), "(дз|домашк|задан)" to ("hw" to "Открываю домашние задания 📝"),
                "недел" to ("week" to "Открываю неделю 🗓"), "(сегодня|главн)" to ("today" to "Открываю главный экран 🏠"),
                "(pro|про)" to ("pro" to "Открываю PRO ✨"), "(телеграм|telegram|канал)" to ("tg" to "Открываю наш Telegram-канал ✈️"),
            )
            map.firstOrNull { has(it.first) }?.let { (_, v) -> return Reply(v.second, act = Act.Open(v.first)) }
        }

        // --- Вопросы о приложении ---
        if (has("^(а )?(как|где|можно ли|можно|что такое|зачем|почему|что делать|что значит)$NW") || has("${PW}(как (мне|можно|я)|где (найти|включить|поменять))$NW")) {
            FAQ.firstOrNull { it.first.containsMatchIn(t) }?.let { return Reply(it.second, think = "Вспоминаю") }
        }

        // --- Время и пары ---
        if (has("котор\\S* (сейчас )?час|сколько (сейчас )?времени$|сколько время$")) {
            return Reply("Сейчас ${"%02d:%02d".format(now.hour, now.minute)} ⏰" + (next?.let { "\nСледующая пара — **${it.subject}** ${inTime(now, it.startAt)}." } ?: ""))
        }
        if (has("(какое|какой) (сегодня |сейчас )?(число|день|дата)|какая (сегодня )?дата|сегодня какое")) {
            val sch = vm.schedule
            return Reply("Сегодня ${WD_FULL[wdIdx(today)]}, ${dm(today)} 📆" + (if (sch != null && sch.hasWeekParity) " · ${sch.weekType(today)}-я учебная неделя" else ""))
        }
        if (has("(как\\S*|котор\\S*|номер|чет\\S*|нечет\\S*) (сейчас |сегодня |эта |учебн\\S* )?недел")) {
            val sch = vm.schedule
            if (sch == null || !sch.hasWeekParity) return Reply("В вашем расписании нет деления на 1-ю и 2-ю недели.")
            val d = when (w) { is When.Day -> w.d; is When.Range -> w.from; null -> today }
            return Reply("${if (d == today) "Сейчас" else cap(dayRu(d, today))} — **${sch.weekType(d)}-я неделя** 📘")
        }
        if (has("до конца (пары|занят)|сколько (еще )?осталось|когда (закончится|кончится) (пара|эта|занят)|скоро (конец|перемена|перерыв)")) {
            if (cur == null) return Reply("Сейчас пары нет ☕" + (next?.let { "\nСледующая — **${it.subject}** ${inTime(now, it.startAt)}, в ${fmt(it.start)}." } ?: ""))
            return Reply("Сейчас идёт **${cur.subject}** (${kindRu(cur.kind)}). До конца — ${left(now, cur.endAt)}, закончится в ${fmt(cur.end)} ⏳")
        }
        if (has("(когда|во сколько|в котором часу)$NW.*(законч|конча|заканчива|освобо|домой|последн)")) {
            var d = day ?: today
            var list = c.on(d)
            if (d == today && w == null && (list.isEmpty() || list.last().endAt < now)) c.lessons.firstOrNull { it.date > today }?.let { d = it.date; list = c.on(d) }
            if (list.isEmpty()) return Reply("${cap(dayRu(d, today))} пар нет 🎉")
            val last = list.last()
            return Reply("${cap(dayRu(d, today))} последняя пара — **${last.subject}**, закончится в **${fmt(last.end)}** 🏠")
        }
        if (has("(когда|во сколько|к скольки|в котором часу)$NW.*(начина|начнут|первая|вставать|приходить|идти|к первой)") && subj == null) {
            var d = day ?: today
            var list = c.on(d)
            if (d == today && w == null && (list.isEmpty() || list.first().startAt < now)) c.lessons.firstOrNull { it.date > today }?.let { d = it.date; list = c.on(d) }
            if (list.isEmpty()) return Reply("${cap(dayRu(d, today))} пар нет — можно выспаться 😴")
            val f = list.first()
            return Reply("${cap(dayRu(d, today))} первая пара — **${f.subject}** в **${fmt(f.start)}**${place(f)}" + (if (d == today && f.startAt > now) " (${inTime(now, f.startAt)})" else ""))
        }
        if (subj != null && has("(когда|во сколько|сколько|через сколько|где|в какой|аудитор|кабинет|корпус|будет|следующ|ближайш)") && !has("(дз|домашк|задал|задани)")) {
            val from = if (day != null) c.lessons.filter { it.subject == subj && it.date == day } else c.lessons.filter { it.subject == subj && it.endAt > now }
            val l = from.firstOrNull()
            if (l == null) {
                if (day == null) return Reply("Впереди пар по предмету **$subj** не нашёл 🤷")
                val n2 = c.lessons.firstOrNull { it.subject == subj && it.startAt > now }
                return Reply("${cap(dayRu(day, today))} **$subj** нет." + (n2?.let { "\nБлижайшая — ${dayRu(it.date, today)} в ${fmt(it.start)}." } ?: ""))
            }
            if (l.startAt <= now) return Reply("**$subj** идёт прямо сейчас 🔴 До конца — ${left(now, l.endAt)}${place(l)}")
            val later = if (day == null) from.drop(1).take(2) else emptyList()
            return Reply(
                "**$subj** (${kindRu(l.kind)}) — ${dayRu(l.date, today)} в **${fmt(l.start)}**, ${inTime(now, l.startAt)}${place(l)}" +
                    (l.details.takeIf { it.isNotBlank() }?.let { "\nℹ️ " + if (it.length > 140) it.take(140) + "…" else it } ?: "") +
                    (if (later.isNotEmpty()) "\nПотом: ${later.joinToString("; ") { "${dayRu(it.date, today)} в ${fmt(it.start)}" }}." else ""),
                think = "Смотрю расписание",
            )
        }
        if (has("(следующ\\S*|ближайш\\S*|потом|дальше|после)$NW.*(пар|занят|урок)|(пар|занят)\\S* (следующ|дальше|потом)|что (дальше|потом|следующ)") && !has("недел")) {
            next ?: return Reply("Впереди пар нет 🎉")
            return Reply("Следующая пара — **${next.subject}** (${kindRu(next.kind)}) ${dayRu(next.date, today)} в **${fmt(next.start)}**, ${inTime(now, next.startAt)}${place(next)}", think = "Смотрю расписание")
        }
        if (has("(сейчас|щас|now)$NW.*(пар|занят|идет|какая|что)|(какая|что за) (сейчас )?пара|что (сейчас|щас) (идет|за)")) {
            if (cur != null) return Reply("Сейчас идёт **${cur.subject}** (${kindRu(cur.kind)}) до ${fmt(cur.end)}${place(cur)}" + (next?.let { "\nПотом — **${it.subject}** в ${fmt(it.start)}." } ?: ""))
            return Reply("Сейчас пары нет ☕" + (next?.let { "\nСледующая — **${it.subject}** ${dayRu(it.date, today)} в ${fmt(it.start)}, ${inTime(now, it.startAt)}." } ?: ""))
        }
        if (has("(сколько|через сколько|скоро|долго)$NW.*(до|пар|начал|начн)|до (пары|начала|занят)") && !has("сколько (у меня |у нас )?(пар|занят)")) {
            if (cur != null) return Reply("Пара уже идёт: **${cur.subject}**, до конца ${left(now, cur.endAt)} ⏳" + (next?.let { "\nСледующая — **${it.subject}** ${inTime(now, it.startAt)}." } ?: ""))
            next ?: return Reply("Впереди пар нет 🎉")
            return Reply("До пары **${next.subject}** — ${left(now, next.startAt)} ⏱\nНачало ${dayRu(next.date, today)} в **${fmt(next.start)}**${place(next)}")
        }
        if (has("(дз|домашк|задал|задани|задано|сделать|выучить|подготовить)")) return Reply(hwAnswer(c, w, subj), think = "Проверяю ДЗ")
        val cloud = vm.cloud
        if (has("дежурн")) {
            if (!cloud.inGroup) return Reply("Дежурные есть только в группе. Создайте её или вступите во вкладке **«Моя группа»** 👥")
            val from = day ?: today
            val ds = cloud.groupState.duties.mapNotNull { d -> runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { it to d } }
                .filter { (d, _) -> if (day != null) d == from else d >= from }.sortedWith(compareBy({ it.first }, { it.second.start }))
            if (ds.isEmpty()) return Reply("Дежурных ${if (day != null) "на " + dayRu(from, today) else "на ближайшие пары"} пока не назначили 🧹")
            return Reply("🧹 Дежурные:\n" + ds.take(6).joinToString("\n") { (d, x) -> "• ${dayRu(d, today)}, ${fmt(x.start)} — **${x.fullName}** (${x.subject})" })
        }
        if (has("(объявлен|новост)")) {
            if (!cloud.inGroup) return Reply("Объявления есть только в группе — вкладка **«Моя группа»** 👥")
            val ns = cloud.groupState.news.sortedByDescending { it.created }.take(3)
            if (ns.isEmpty()) return Reply("Новых объявлений нет 📭")
            return Reply("📢 Последние объявления:\n" + ns.joinToString("\n") { "• **${it.author.ifBlank { "Участник" }}**: ${it.text}" })
        }
        if (has("(отработ|пропуск|пропустил)")) {
            if (cloud.token == null) return Reply("Отработки хранятся в профиле. Войдите в ⚙️ **Настройках → «Профиль»**, и во вкладке «Моя группа» появятся «Отработки».")
            val ab = cloud.groupState.absences.filter { !it.done }
            if (ab.isEmpty()) return Reply("Несданных отработок нет 🎉")
            fun dd(s: String) = runCatching { dayRu(LocalDate.parse(s), today) }.getOrDefault(s)
            return Reply("🩺 Отработки (${ab.size}):\n" + ab.take(6).joinToString("\n") { a ->
                "• **${a.subject.ifBlank { "Пара" }}** — пропуск ${if (a.date.isNotBlank()) dd(a.date) else ""}" +
                    (if (a.makeupDate.isNotBlank()) "; отработка ${dd(a.makeupDate)}" + (if (a.makeupTime.isNotBlank()) " в ${a.makeupTime}" else "") + (if (a.makeupPlace.isNotBlank()) ", ${a.makeupPlace}" else "") else "")
            })
        }
        if (has("сколько (у меня |у нас )?(пар|занят)")) {
            if (w is When.Range) return Reply(weekAnswer(c, w.from, w.to))
            val d = day ?: today
            val list = c.on(d)
            return Reply(if (list.isNotEmpty()) "${cap(dayRu(d, today))} ${pars(list.size)}: с ${fmt(list.first().start)} до ${fmt(list.last().end)}." else "${cap(dayRu(d, today))} пар нет 🎉")
        }
        if (w != null || has("(пар[аыу]?$NW|расписан|занят|учим|учеба|лекци|практик|выходн|что (у меня|у нас)|какие)")) {
            val kind = if (has("лекци")) LessonKind.LECTURE else if (has("практик")) LessonKind.PRACTICE else null
            if (w is When.Range) return Reply(weekAnswer(c, w.from, w.to), think = "Смотрю расписание")
            if (subj != null && w == null) {
                c.lessons.firstOrNull { it.subject == subj && it.endAt > now }?.let { l ->
                    return Reply("Ближайшая **$subj** — ${dayRu(l.date, today)} в **${fmt(l.start)}**, ${inTime(now, l.startAt)}${place(l)}")
                }
            }
            var d = day ?: today
            if (day == null && c.on(today).all { it.endAt < now }) d = c.lessons.firstOrNull { it.date > today }?.date ?: today
            return Reply((if (day == null && d != today) "Сегодня пары уже закончились. " else "") + dayAnswer(c, d, kind), think = "Смотрю расписание")
        }

        // --- Общение ---
        if (has("^(привет|здравств|хай|хей|йо|добр\\S* (утро|день|вечер)|салют|hello|hi)$NW")) {
            val tl = c.on(today).filter { it.endAt > now }
            return Reply(
                "${pick(listOf("Привет", "Здравствуйте", "Хей"))}${if (name.isNotEmpty()) ", $name" else ""}! 👋 " +
                    when {
                        tl.isNotEmpty() -> "Сегодня ещё ${pars(tl.size)}, ближайшая — **${tl[0].subject}** в ${fmt(tl[0].start)}."
                        next != null -> "Сегодня пар больше нет. Следующая — **${next.subject}** ${dayRu(next.date, today)} в ${fmt(next.start)}."
                        else -> ""
                    } + "\nЧем помочь?"
            )
        }
        if (has("(спасиб|спс|благодар|thank|пасиб)")) return Reply(pick(listOf("Всегда пожалуйста! 😊", "Обращайтесь! 🤖", "Рад помочь! Удачи на парах 🍀")))
        if (has("(как дела|как ты|как поживаешь|что нового)")) return Reply(pick(listOf("Отлично! Слежу за расписанием, чтобы вы ничего не пропустили 😎", "Всё хорошо, работаю без перерывов на обед 🤖 А у вас как?")))
        if (has("(кто ты|ты кто|что ты такое|что ты умеешь|что умеешь|помощь|помоги|команды|help|^\\?$|что можешь|функци)")) return Reply(HELP)
        if (has("(кто (тебя )?(создал|сделал|разработ)|создатель|автор|разработчик)")) return Reply("Меня создал **AleXSHelL** для студентов КГМУ 💙 Новости и обновления — в канале t.me/kgmuraspisanie")
        if (has("(шутк|анекдот|пошути|рассмеши)")) return Reply(pick(listOf(
            "Студент-медик спит на лекции. Преподаватель: «Вам неинтересно?» — «Что вы, я изучаю физиологию сна на практике!» 😴",
            "Почему анатомию учат по ночам? Потому что днём кости ещё не сложились 💀",
            "Лучшее лекарство от сессии — расписание, открытое заранее 😉",
        )))
        if (has("(пока|до свидан|спокойной ночи|до завтра|бай)")) return Reply(pick(listOf("До встречи! 👋", "Пока! Не забудьте про ДЗ 😉", "Спокойной ночи! Будильник — ваш лучший друг ⏰")))
        if (has("(молодец|умница|круто|класс|супер|лучший|люблю)")) return Reply(pick(listOf("Спасибо, стараюсь! 🥰", "Вы тоже супер! 💪")))
        if (has("(дурак|тупой|глуп|бесполез|плохой)")) return Reply("Я ещё учусь 🙈 Попробуйте спросить по-другому — например, «какие пары завтра?»")

        FAQ.firstOrNull { it.first.containsMatchIn(t) }?.let { return Reply(it.second, think = "Вспоминаю") }
        if (subj != null) {
            c.lessons.firstOrNull { it.subject == subj && it.endAt > now }?.let { l ->
                return Reply("Ближайшая **$subj** — ${dayRu(l.date, today)} в **${fmt(l.start)}**, ${inTime(now, l.startAt)}${place(l)}")
            }
        }
        return Reply(
            pick(listOf("Хм, не совсем понял 🤔", "Я пока не умею отвечать на такое 🙈", "Интересный вопрос, но я ещё учусь 🤖")) +
                "\nПопробуйте так: «какие пары завтра?», «сколько до пары?», «что задали?» или «что ты умеешь?»"
        )
    }
}
