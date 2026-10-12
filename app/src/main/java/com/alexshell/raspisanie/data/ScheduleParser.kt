package com.alexshell.raspisanie.data

import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Разбирает расписание вуза в «свободном» формате Excel:
 *  - строка с «группа 311», «группа 312», ... задаёт столбцы групп;
 *  - в столбце A — дни недели (ПН, ВТ, ...), ячейки объединены по высоте;
 *  - ячейка, объединённая на все группы, — общее занятие (обычно лекция);
 *  - внутри ячейки может быть несколько занятий подряд:
 *    «10.40-12.10, 12.20-13.05 Фармакология 07.09-28.12 8.00-11.10 Общая хирургия 14.12».
 *
 * Поддерживаемые записи дат: диапазон «07.09-28.12» (каждую неделю в этот день),
 * «1 неделя / 2 неделя» (через неделю), «по 23.12», списки «07.12, 21.12»,
 * особое время «16.12-11.50-13.20» или «9.30-11.00-16.12», «01.09-онлайн».
 */
object ScheduleParser {

    private const val TIME = """\d{1,2}[.:]\d{2}-\d{1,2}[.:]\d{2}"""
    private val HEADER = Regex("""(?<!\S)($TIME(?:\s*,\s*$TIME)*)\s+(?=[А-ЯЁA-Z])""")
    private val TIME_RANGE = Regex("""(\d{1,2})[.:](\d{2})-(\d{1,2})[.:](\d{2})""")
    private val TRIPLE = Regex("""(?<![\d.])(\d{1,2}\.\d{2})-(\d{1,2}\.\d{2})-(\d{1,2}\.\d{2})(?![\d])""")
    private val PAIR = Regex("""(?<![\d.])(\d{1,2}\.\d{2})\s*-\s*(\d{1,2}\.\d{2})(?![\d])""")
    private val UNTIL = Regex("""(?i)(?<![А-Яа-яЁё])по\s+(\d{1,2}\.\d{2})(?![\d])""")
    private val SINGLE = Regex("""(?<![\d.])(\d{1,2}\.\d{2})(?![\d])""")
    private val ONLINE = Regex("""(?i)(\d{1,2}\.\d{2})\s*-\s*онлайн""")
    private val PARITY = Regex("""(?i)(?<!\d)([12])\s*недел[яи]""")
    private val GROUP = Regex("""(?i)группа\s*(\d+)""")
    private val ROOM = Regex("""(?i)(\d+)\s*корпус,?\s*аудитори[яи]\s*(\d+)""")
    private val SHORT_ROOM = Regex("""(?<![\d.]|ссылка |ссылка\s)(\d)-(\d{3})(?!\d)""")
    private val OPTION_NAME = Regex("""(?<![А-Яа-яЁё.])[А-ЯЁ][а-яё]+(?:\s+[а-яё]+|\s*,\s*[а-яё]+)*""")
    private val OPTION_TAIL = Regex("""(\s+на\s+кафедре.*|,?\s*гр(?![а-яё]).*)$""")
    private val STREET = Regex("""ул\.\s*[А-ЯЁ][а-яё]+,?\s*\d+[а-я]?""")
    private val DAY_CODES = mapOf(
        "ПН" to DayOfWeek.MONDAY, "ВТ" to DayOfWeek.TUESDAY, "СР" to DayOfWeek.WEDNESDAY,
        "ЧТ" to DayOfWeek.THURSDAY, "ПТ" to DayOfWeek.FRIDAY, "СБ" to DayOfWeek.SATURDAY,
        "ВС" to DayOfWeek.SUNDAY,
    )
    private val MONTHS = mapOf(
        "январ" to 1, "феврал" to 2, "март" to 3, "апрел" to 4, "ма" to 5, "июн" to 6, "июл" to 7,
        "август" to 8, "сентябр" to 9, "октябр" to 10, "ноябр" to 11, "декабр" to 12,
    )

    fun parse(input: InputStream, today: LocalDate = LocalDate.now()): Schedule =
        parse(XlsxReader.read(input), today)

    fun parse(sheet: Sheet, today: LocalDate = LocalDate.now()): Schedule {
        // Цикловое расписание (столбцы — даты, строки — группы) разбирается отдельно.
        if (CycleScheduleParser.matches(sheet)) return CycleScheduleParser.parse(sheet, today)
        val all = sheet.cells.values.joinToString("\n") { clean(it) }

        // --- Год и начало семестра ---
        val yearM = Regex("""(\d{4})\s*-\s*(\d{4})\s*учебн""").find(all)
        val startYear = yearM?.groupValues?.get(1)?.toInt()
            ?: if (today.monthValue >= 8) today.year else today.year - 1
        val semStartM = Regex("""(?i)начало\s+семестра\s+(\d{1,2})\s+([а-яё]+)\s+(\d{4})""").find(all)
        val semesterStart = semStartM?.let { m ->
            val month = MONTHS.entries.firstOrNull { m.groupValues[2].lowercase().startsWith(it.key) }?.value
            month?.let { runCatching { LocalDate.of(m.groupValues[3].toInt(), it, m.groupValues[1].toInt()) }.getOrNull() }
        } ?: LocalDate.of(startYear, 9, 1)

        fun toDate(s: String): LocalDate? {
            val (d, m) = s.split(".").map { it.toInt() }
            if (m !in 1..12 || d !in 1..31) return null
            // Семестр начинается осенью: сентябрь–декабрь — первый год, январь–июль — второй.
            val y = if (m >= 8) startYear else startYear + 1
            return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        }

        // --- Столбцы групп ---
        var headerRow = -1
        val groupCols = sortedMapOf<Int, String>()
        for ((ref, text) in sheet.cells) {
            val m = GROUP.find(text) ?: continue
            if (text.trim().length > 20) continue
            if (headerRow == -1 || ref.row == headerRow) {
                headerRow = ref.row
                groupCols[ref.col] = m.groupValues[1]
            }
        }
        if (groupCols.isEmpty()) throw IllegalArgumentException("Не найдены столбцы «группа ...»")
        val allGroups = groupCols.values.toSet()

        // --- Чередование недель ---
        val week1 = ArrayList<ClosedRange<LocalDate>>()
        val week2 = ArrayList<ClosedRange<LocalDate>>()
        sheet.cells.values.map(::clean).firstOrNull { Regex("""1\s*неделя\s*-""").containsMatchIn(it) }?.let { txt ->
            val i2 = Regex("""2\s*неделя\s*-""").find(txt)?.range?.first ?: txt.length
            fun ranges(s: String, out: MutableList<ClosedRange<LocalDate>>) {
                PAIR.findAll(s).forEach { m ->
                    val a = toDate(m.groupValues[1]); val b = toDate(m.groupValues[2])
                    if (a != null && b != null) out += a..(if (b < a) b.plusYears(1) else b)
                }
            }
            ranges(txt.substring(0, i2), week1)
            ranges(txt.substring(i2), week2)
        }
        val tmp = Schedule("", emptyList(), emptyList(), semesterStart, week1, week2, emptyList())

        // --- Дни недели в столбце A ---
        val lessons = ArrayList<Lesson>()
        var day: DayOfWeek? = null
        for (row in headerRow + 1..sheet.maxRow) {
            val a = clean(sheet.text(row, 1)).uppercase()
            if (a.startsWith("ДИСЦИПЛИНА")) break
            DAY_CODES[a.take(2)]?.let { day = it }
            if (a.isEmpty()) {
                // Объединённая по высоте ячейка дня
                sheet.mergeOf(row, 1)?.let { mr ->
                    DAY_CODES[clean(sheet.text(mr.r1, mr.c1)).uppercase().take(2)]?.let { day = it }
                }
            }
            val dow = day ?: continue
            for ((col, group) in groupCols) {
                val merge = sheet.mergeOf(row, col)
                if (merge != null && (merge.r1 != row || merge.c1 != col)) continue
                val raw = clean(sheet.text(row, col))
                if (raw.isEmpty()) continue
                if (Regex("""1\s*неделя\s*-""").containsMatchIn(raw)) continue
                val groups = if (merge != null && merge.c2 > merge.c1) {
                    groupCols.filterKeys { it in merge.c1..merge.c2 }.values.toSet()
                } else setOf(group)
                if (groups.isEmpty()) continue
                lessons += parseCell(raw, dow, groups, semesterStart, tmp, ::toDate)
            }
        }

        // --- Таблица дисциплин и кафедр внизу листа ---
        val disciplines = parseDisciplines(sheet)

        // Отдельные строки вроде «Статистические методы ... группа №2» — тоже дисциплины по выбору.
        val electives = lessons.mapNotNull { it.elective }.distinct()
        val marked = lessons.map { l ->
            if (l.elective != null) return@map l
            val key = normKey(l.subject)
            val opt = electives.firstOrNull { e ->
                val ek = normKey(e)
                ek.length >= 15 && (key.startsWith(ek) || ek.startsWith(key) && key.length >= 15)
            } ?: return@map l
            l.copy(
                subject = opt,
                elective = opt,
                kind = if (l.kind == LessonKind.PRACTICE) LessonKind.ELECTIVE else l.kind,
            )
        }

        // Место проведения для практик берём из таблицы кафедр, если в ячейке его нет.
        val withPlaces = marked.map { l ->
            if (l.location != null) l else {
                val d = tmp.copy(disciplines = disciplines).disciplineFor(l.subject)
                l.copy(location = d?.department?.let(::placeFromDepartment))
            }
        }

        val title = sheet.cells.values.map(::clean).firstOrNull { it.uppercase().startsWith("РАСПИСАНИЕ") }
            ?.let { sentence(it) } ?: "Расписание"
        return Schedule(
            title = title,
            groups = groupCols.values.toList(),
            lessons = withPlaces.distinctBy { it.key + it.groups.sorted() },
            semesterStart = semesterStart,
            week1Ranges = week1,
            week2Ranges = week2,
            disciplines = disciplines,
            electives = electives,
        ).also { require(allGroups.isNotEmpty()) }
    }

    /** Разбивает текст ячейки на отдельные занятия по заголовкам «время Предмет». */
    private fun parseCell(
        text: String,
        dow: DayOfWeek,
        groups: Set<String>,
        semesterStart: LocalDate,
        weeks: Schedule,
        toDate: (String) -> LocalDate?,
    ): List<Lesson> {
        // Заголовок засчитываем, только если все интервалы в нём — корректное время
        // (иначе это диапазон дат вроде «16.10-25.12 Молекулярные ...»).
        val headers = HEADER.findAll(text).mapNotNull { h ->
            val parts = TIME_RANGE.findAll(h.groupValues[1]).toList()
            val slots = parts.mapNotNull { m ->
                val (h1, m1, h2, m2) = m.destructured
                slot(h1.toInt(), m1.toInt(), h2.toInt(), m2.toInt())
            }
            if (slots.isNotEmpty() && slots.size == parts.size) h to slots else null
        }.toList()
        if (headers.isEmpty()) return emptyList()
        val out = ArrayList<Lesson>()
        headers.forEachIndexed { i, (h, slots) ->
            val bodyEnd = headers.getOrNull(i + 1)?.first?.range?.first ?: text.length
            val body = text.substring(h.range.last + 1, bodyEnd).trim()
            out += parseSegment(body, slots, dow, groups, semesterStart, weeks, toDate)
        }
        return out
    }

    private fun parseSegment(
        bodyRaw: String,
        slots: List<TimeSlot>,
        dow: DayOfWeek,
        groups: Set<String>,
        semesterStart: LocalDate,
        weeks: Schedule,
        toDate: (String) -> LocalDate?,
    ): List<Lesson> {
        var body = bodyRaw
        var kind = LessonKind.PRACTICE
        if (body.uppercase().startsWith("ЛЕКЦИЯ")) {
            kind = LessonKind.LECTURE
            body = body.drop("ЛЕКЦИЯ".length).trim()
        }
        val up = body.uppercase()
        if (up.startsWith("ДИСЦИПЛИНА ПО ВЫБОРУ") || up.startsWith("ЭЛЕКТИВН")) {
            kind = if (kind == LessonKind.LECTURE) LessonKind.LECTURE else LessonKind.ELECTIVE
        }

        // Название предмета — всё до первой даты/«N неделя».
        val firstDate = listOfNotNull(SINGLE.find(body)?.range?.first, PARITY.find(body)?.range?.first).minOrNull()
            ?: body.length
        val fullSubject = sentence(body.substring(0, firstDate).trim().trimEnd(',', ';', '-').trim())
        val subject = shortName(fullSubject)
        var rest = body.substring(firstDate)
        val details = rest.trim()

        val spec = extractDates(rest, dow, semesterStart, weeks, toDate)
        val location = locationOf(details)

        // Дисциплина по выбору: раскладываем ячейку на отдельные дисциплины.
        if (subject.startsWith("Дисциплина по выбору", ignoreCase = true)) {
            val options = parseElectiveOptions(rest, slots, kind, dow, groups, semesterStart, weeks, toDate, details)
            if (options.isNotEmpty()) return options
        }

        return spec.toLessons(slots) { d, own ->
            Lesson(
                date = d,
                slots = own ?: slots,
                subject = subject,
                fullSubject = fullSubject,
                kind = kind,
                groups = groups,
                location = location,
                details = details,
                online = d in spec.online,
                specialTime = own != null,
            )
        }
    }

    private class DateSpec(
        val dates: Set<LocalDate>,
        val overrides: Map<LocalDate, List<TimeSlot>>,
        val online: Set<LocalDate>,
    ) {
        fun toLessons(slots: List<TimeSlot>, make: (LocalDate, List<TimeSlot>?) -> Lesson): List<Lesson> =
            dates.sorted().map { make(it, overrides[it]) }
    }

    /** Достаёт из текста все даты занятия с учётом дня недели и чередования недель. */
    private fun extractDates(
        text: String,
        dow: DayOfWeek,
        semesterStart: LocalDate,
        weeks: Schedule,
        toDate: (String) -> LocalDate?,
        fallbackToSemester: Boolean = true,
    ): DateSpec {
        var rest = text
        val onlineDates = ONLINE.findAll(rest).mapNotNull { toDate(it.groupValues[1]) }.toSet()
        rest = ONLINE.replace(rest) { it.groupValues[1] + " " }

        // 1) Особое время на дату: «16.12-11.50-13.20» или «9.30-11.00-16.12».
        val overrides = LinkedHashMap<LocalDate, List<TimeSlot>>()
        rest = TRIPLE.replace(rest) { m ->
            val (x, y, z) = m.destructured
            val asDateTime = toDate(x)?.let { d -> timeSlot(y, z)?.let { d to it } }
            val asTimeDate = toDate(z)?.let { d -> timeSlot(x, y)?.let { d to it } }
            val pick = if (isDateLike(x)) asDateTime ?: asTimeDate else asTimeDate ?: asDateTime
            if (pick != null) {
                overrides[pick.first] = listOf(pick.second); " ; "
            } else m.value
        }

        val parity = PARITY.find(rest)?.groupValues?.get(1)?.toInt()
        val dates = sortedSetOf<LocalDate>()

        fun addRange(a: LocalDate, b: LocalDate) {
            var d = a
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            while (!d.isAfter(b)) {
                if (parity == null || weeks.weekType(d) == parity) dates += d
                d = d.plusWeeks(1)
            }
        }

        // 2) «по 23.12» — с начала семестра.
        rest = UNTIL.replace(rest) { m ->
            toDate(m.groupValues[1])?.let { addRange(semesterStart, it); " ; " } ?: m.value
        }
        // 3) Диапазоны дат; пары, похожие только на время, выбрасываем.
        rest = PAIR.replace(rest) { m ->
            val a = toDate(m.groupValues[1]); val b = toDate(m.groupValues[2])
            when {
                a != null && b != null && isDateLike(m.groupValues[1]) && isDateLike(m.groupValues[2]) -> {
                    addRange(a, if (b < a) b.plusYears(1) else b); " ; "
                }
                timeSlot(m.groupValues[1], m.groupValues[2]) != null -> " ; "
                else -> m.value
            }
        }
        // 4) Отдельные даты (без учёта чётности недели — они перечислены явно).
        SINGLE.findAll(rest).forEach { m ->
            if (isDateLike(m.groupValues[1])) toDate(m.groupValues[1])?.let { dates += it }
        }
        dates += overrides.keys
        // Если дат нет вовсе — считаем занятием на весь семестр.
        if (dates.isEmpty() && fallbackToSemester) addRange(semesterStart, semesterStart.plusMonths(4))
        return DateSpec(dates, overrides, onlineDates)
    }

    private fun locationOf(text: String): String? =
        ROOM.find(text)?.let { m ->
            val street = STREET.find(text)?.value
            "${m.groupValues[1]} корпус, ауд. ${m.groupValues[2]}" + (street?.let { ", $it" } ?: "")
        } ?: SHORT_ROOM.find(text)?.let { m -> "${m.groupValues[1]} корпус, ауд. ${m.groupValues[2]}" }

    /**
     * Ячейка «Дисциплина по выбору» перечисляет несколько дисциплин через «;».
     * Даты, стоящие после названия, относятся только к этой дисциплине; остальные даты —
     * общие для всех. Время и аудитория в той же части тоже относятся к дисциплине.
     */
    private fun parseElectiveOptions(
        rest: String,
        slots: List<TimeSlot>,
        kind: LessonKind,
        dow: DayOfWeek,
        groups: Set<String>,
        semesterStart: LocalDate,
        weeks: Schedule,
        toDate: (String) -> LocalDate?,
        details: String,
    ): List<Lesson> {
        class Option(val name: String, val own: String, val chunk: String)
        val options = ArrayList<Option>()
        val shared = StringBuilder()
        for (chunk in rest.split(';')) {
            val names = OPTION_NAME.findAll(chunk).mapNotNull { m ->
                val name = m.value.replace(OPTION_TAIL, "").trim().trimEnd(',', ' ')
                val first = name.substringBefore(' ')
                if (name.length < 9 || first.length < 4 || first.startsWith("Дисциплин")) null
                else Triple(name, m.range.first, m.range.last + 1)
            }.toList()
            if (names.isEmpty()) {
                shared.append(chunk).append(" ; ")
                continue
            }
            shared.append(chunk.substring(0, names.first().second)).append(" ; ")
            names.forEachIndexed { i, (name, _, endIdx) ->
                val ownEnd = names.getOrNull(i + 1)?.second ?: chunk.length
                options += Option(name, chunk.substring(endIdx, ownEnd), chunk)
            }
        }
        if (options.isEmpty()) return emptyList()
        val sharedSpec = extractDates(shared.toString(), dow, semesterStart, weeks, toDate)
        val kindOut = if (kind == LessonKind.LECTURE) LessonKind.LECTURE else LessonKind.ELECTIVE
        return options.distinctBy { it.name }.flatMap { o ->
            val ownSpec = extractDates(o.own, dow, semesterStart, weeks, toDate, fallbackToSemester = false)
            val spec = if (ownSpec.dates.isNotEmpty()) ownSpec else sharedSpec
            // Своё время дисциплины: интервал времени в её части текста (не диапазон дат).
            val ownTime = PAIR.findAll(TRIPLE.replace(o.chunk, " ")).firstNotNullOfOrNull { m ->
                if (isDateLike(m.groupValues[1]) && isDateLike(m.groupValues[2])) null
                else timeSlot(m.groupValues[1], m.groupValues[2])
            }
            val baseSlots = ownTime?.let { listOf(it) } ?: slots
            val location = locationOf(o.chunk.replace(Regex("""ссылка\s*\d-\d{3}"""), ""))
            spec.toLessons(baseSlots) { d, own ->
                Lesson(
                    date = d,
                    slots = own ?: baseSlots,
                    subject = o.name,
                    fullSubject = o.name,
                    kind = kindOut,
                    groups = groups,
                    location = location,
                    details = "Дисциплина по выбору. $details",
                    online = d in spec.online,
                    specialTime = own != null,
                    elective = o.name,
                )
            }
        }
    }

    private fun parseDisciplines(sheet: Sheet): List<DisciplineInfo> {
        val header = sheet.cells.entries.firstOrNull { clean(it.value).equals("Дисциплина", true) && it.key.col == 1 }
            ?: return emptyList()
        // Заголовки столбцов в строке шапки.
        val cols = (1..sheet.maxCol).associateWith { clean(sheet.text(header.key.row, it)).lowercase() }
        val nameCols = cols.filterValues { it == "дисциплина" }.keys.sorted()
        val out = ArrayList<DisciplineInfo>()
        for (row in header.key.row + 1..sheet.maxRow) {
            if (clean(sheet.text(row, 1)).startsWith("Начальник")) break
            for ((idx, nc) in nameCols.withIndex()) {
                val next = nameCols.getOrNull(idx + 1) ?: (sheet.maxCol + 1)
                val name = clean(valueAt(sheet, row, nc))
                if (name.isEmpty() || name.length < 4) continue
                var dep = ""
                var att: String? = null
                for (c in nc + 1 until next) {
                    // Ячейки, объединённые по ширине, читаем один раз.
                    val mc = sheet.mergeOf(row, c)
                    if (mc != null && mc.c1 != c && mc.c1 > nc) continue
                    val h = cols[c].orEmpty()
                    val v = clean(valueAt(sheet, row, c))
                    if (v.isEmpty()) continue
                    when {
                        h.contains("аттестац") -> att = v
                        h.contains("кафедр") || h.contains("баз") -> dep = if (dep.isEmpty()) v else "$dep; $v"
                    }
                }
                if (dep.isEmpty() && att == null) continue
                out += DisciplineInfo(name, dep, att)
            }
        }
        return out
    }

    /** Значение с учётом объединённых ячеек. */
    private fun valueAt(sheet: Sheet, row: Int, col: Int): String {
        val m = sheet.mergeOf(row, col) ?: return sheet.text(row, col)
        return sheet.text(m.r1, m.c1)
    }

    private fun placeFromDepartment(dep: String): String {
        val inBrackets = Regex("""\(([^()]*(?:корпус|ул\.)[^()]*)\)""").find(dep)?.groupValues?.get(1)
        return (inBrackets ?: dep).trim()
    }

    private fun timeSlot(a: String, b: String): TimeSlot? {
        val (h1, m1) = a.split(".").map { it.toInt() }
        val (h2, m2) = b.split(".").map { it.toInt() }
        return slot(h1, m1, h2, m2)
    }

    private fun slot(h1: Int, m1: Int, h2: Int, m2: Int): TimeSlot? {
        if (h1 !in 6..22 || h2 !in 6..23 || m1 > 59 || m2 > 59) return null
        val s = h1 * 60 + m1
        val e = h2 * 60 + m2
        return if (e > s) TimeSlot(s, e) else null
    }

    /** Похоже ли «DD.MM» на дату учебного полугодия (а не на время). */
    private fun isDateLike(s: String): Boolean {
        val (d, m) = s.split(".").map { it.toInt() }
        return d in 1..31 && (m in 8..12 || m in 1..2)
    }

    fun clean(s: String): String = s.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

    /** «ГИГИЕНА» -> «Гигиена». Смешанный регистр не трогаем. */
    fun sentence(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty()) return s
        val upper = letters.count { it.isUpperCase() }
        if (upper < letters.length * 0.7) {
            val first = s.trim().substringBefore(' ')
            if (first.length < 4 || first.any { it.isLowerCase() }) return s
            // «ДИСЦИПЛИНА ПО ВЫБОРУ (пр. занятия)» — понижаем только слова капслоком.
            return s.replace(Regex("""[А-ЯЁA-Z]{2,}""")) { it.value.lowercase() }.replaceFirstChar { it.uppercase() }
        }
        return s.lowercase().replaceFirstChar { it.uppercase() }
            .replace(Regex("""([а-яё]{4,}[.!?]\s+)([а-яё])""")) { it.groupValues[1] + it.groupValues[2].uppercase() }
    }

    /** «Патофизиология, клиническая патофизиология. ...» -> «Патофизиология». */
    fun shortName(full: String): String {
        val f = full.replace(Regex("""(?i)\s*\(пр\.?\s*занятия\)"""), "").trim()
        if (f.contains(", клиническ", ignoreCase = true)) return f.substringBefore(",").trim()
        if (f.length > 60 && f.contains("(")) return f.substringBefore("(").trim()
        return f
    }

    fun normKey(s: String): String =
        s.lowercase().replace('ё', 'е').replace(Regex("""[^а-яa-z ]"""), " ").replace(Regex("""\s+"""), " ").trim()
}
