package com.alexshell.raspisanie.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * «Цикловое» расписание (например, 4 курс):
 *  - строки «месяц» / «число» / «день недели» задают столбцы-даты;
 *  - строки групп (401, 402, …): объединённая ячейка = цикл дисциплины на эти даты;
 *  - «*» в названии — в первый день цикла занятия во вторую смену;
 *  - таблица внизу: дисциплина → кафедра, база, адрес, время 1-й и 2-й смены;
 *  - строки «Лекции» и физкультура — по потокам и дням недели;
 *  - отдельная строка с датами учебной практики (НИР).
 */
object CycleScheduleParser {

    private val TIME = Regex("""(\d{1,2})[.:](\d{2})\s*-\s*(\d{1,2})[.:](\d{2})""")
    private val SHORT_ROOM = Regex("""(?<![\d.])(\d)-(\d{3})(?!\d)""")
    private val DAY_NAMES = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY,
    )

    private fun clean(s: String) = ScheduleParser.clean(s)

    private fun monthOf(word: String): Int? {
        val w = word.lowercase()
        val keys = listOf("янв", "фев", "мар", "апр", "ма", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        // «ма» — только «май/мая», не «март».
        if (w.startsWith("мар")) return 3
        return keys.indexOfFirst { w.startsWith(it) }.takeIf { it >= 0 }?.plus(1)
    }

    private fun slotOf(text: String): TimeSlot? = TIME.find(text)?.let { m ->
        val (h1, m1, h2, m2) = m.destructured
        val s = h1.toInt() * 60 + m1.toInt()
        val e = h2.toInt() * 60 + m2.toInt()
        if (h1.toInt() in 6..22 && e > s) TimeSlot(s, e) else null
    }

    /** Похоже ли на цикловое расписание: есть строки «число» и «день недели» в первом столбце. */
    fun matches(sheet: Sheet): Boolean {
        val firstCol = (1..sheet.maxRow).map { clean(sheet.text(it, 1)).lowercase() }
        return firstCol.any { it == "число" } && firstCol.any { it.startsWith("день недели") }
    }

    private fun rowOf(sheet: Sheet, label: String): Int =
        (1..sheet.maxRow).first { clean(sheet.text(it, 1)).lowercase().startsWith(label) }

    private fun valueAt(sheet: Sheet, row: Int, col: Int): String {
        val m = sheet.mergeOf(row, col) ?: return sheet.text(row, col)
        return sheet.text(m.r1, m.c1)
    }

    /** «Профессиона-льные болезни*» -> «Профессиональные болезни». */
    private fun tidyName(s: String): String =
        clean(s.replace("*", "")).replace(Regex("""([а-яё])-([а-яё])"""), "$1$2")

    private data class Disc(
        val name: String,
        val attestation: String?,
        val department: String,
        val base: String,
        val address: String,
        val shift1: String,
        val shift2: String,
    ) {
        val place: String get() = listOf(base, address).filter { it.isNotBlank() }.joinToString(", ")
    }

    fun parse(sheet: Sheet, today: LocalDate): Schedule {
        val all = sheet.cells.values.joinToString("\n") { clean(it) }
        val startYear = Regex("""(\d{4})\s*/\s*(\d{2,4})""").find(all)?.groupValues?.get(1)?.toInt()
            ?: Regex("""(\d{4})\s*-\s*(\d{4})""").find(all)?.groupValues?.get(1)?.toInt()
            ?: if (today.monthValue >= 8) today.year else today.year - 1

        // --- Столбцы-даты ---
        val monthRow = rowOf(sheet, "месяц")
        val dayRow = rowOf(sheet, "число")
        val dowRow = rowOf(sheet, "день недели")
        val dates = sortedMapOf<Int, LocalDate>()
        var month: Int? = null
        for (col in 2..sheet.maxCol) {
            monthOf(clean(valueAt(sheet, monthRow, col)))?.let { month = it }
            val day = clean(sheet.text(dayRow, col)).substringBefore('.').toIntOrNull() ?: continue
            val m = month ?: continue
            val y = if (m >= 8) startYear else startYear + 1
            runCatching { LocalDate.of(y, m, day) }.getOrNull()?.let { dates[col] = it }
        }
        require(dates.isNotEmpty()) { "Не удалось прочитать даты в шапке расписания" }

        // --- Строки групп ---
        val groupRows = sortedMapOf<Int, String>()
        for (row in dowRow + 1..sheet.maxRow) {
            val g = clean(sheet.text(row, 2)).substringBefore('.')
            if (g.matches(Regex("""\d{3,4}"""))) groupRows[row] = g
            else if (groupRows.isNotEmpty() && g.isNotEmpty()) break
        }
        require(groupRows.isNotEmpty()) { "Не найдены строки групп" }
        val groups = groupRows.values.toList()

        // --- Таблица дисциплин ---
        val discs = parseTable(sheet)

        fun discFor(cycle: String): Disc? {
            val key = ScheduleParser.normKey(cycle)
            val practical = discs.filter { !it.name.contains("лекци", true) }
            // Раздел «Гематология» и т. п.
            practical.firstOrNull { d ->
                Regex("""раздел\s*["«]([^"»]+)["»]""", RegexOption.IGNORE_CASE).find(d.name)
                    ?.let { ScheduleParser.normKey(it.groupValues[1]) == key } == true
            }?.let { return it }
            val words = key.split(' ').take(2).joinToString(" ")
            return practical.filter { !Regex("""раздел\s*["«]""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
                .firstOrNull { ScheduleParser.normKey(it.name).startsWith(words) }
        }

        val lessons = ArrayList<Lesson>()
        val studyDays = dates.values.toSet()
        // Конец учёбы — последний день циклов (дальше зачёты, экзамены, каникулы).
        var lastStudyDay = dates.values.min()

        // --- Циклы практических занятий ---
        for ((row, group) in groupRows) {
            for (col in dates.keys) {
                val raw = sheet.text(row, col)
                if (raw.isBlank()) continue
                val merge = sheet.mergeOf(row, col)
                if (merge != null && (merge.r1 != row || merge.c1 != col)) continue
                val c2 = merge?.c2 ?: col
                val r2 = merge?.r2 ?: row
                val rowGroups = groupRows.filterKeys { it in row..r2 }.values.toSet()
                val text = clean(raw)
                val low = text.lowercase()
                if (low.contains("каникул") || low.contains("экзамен")) continue
                val cycleDates = dates.filterKeys { it in col..c2 }.values.sorted()
                if (cycleDates.isEmpty()) continue

                if (low.startsWith("зач")) {
                    // Зачёт по практике — общий для всех групп.
                    val at = slotOf(all.lines().firstOrNull { it.lowercase().startsWith("зачет") || it.lowercase().startsWith("зачёт") } ?: "")
                        ?: TimeSlot(8 * 60 + 30, 10 * 60)
                    cycleDates.forEach { d ->
                        lessons += Lesson(
                            date = d, slots = listOf(at), subject = tidyName(text), fullSubject = tidyName(text),
                            kind = LessonKind.PRACTICE, groups = rowGroups, location = null,
                            details = "Зачёт", online = false, specialTime = false,
                        )
                    }
                    continue
                }

                val star = text.contains('*')
                if (cycleDates.last() > lastStudyDay) lastStudyDay = cycleDates.last()
                val name = tidyName(text)
                val disc = discFor(name)
                // Время: «гр 404 10:30-13:35» во 2-й смене — особое время этой группы.
                val groupSpecific = disc?.shift2?.let { s2 ->
                    Regex("""гр\.?\s*(\d{3,4})""").find(s2)?.groupValues?.get(1)?.takeIf { it == group }?.let { slotOf(s2) }
                }
                val slot1 = groupSpecific ?: disc?.shift1?.let(::slotOf) ?: TimeSlot(8 * 60 + 30, 12 * 60 + 25)
                val slot2 = disc?.shift2?.takeIf { !it.contains(Regex("""гр\.?\s*\d""")) }?.let(::slotOf)
                val range = "${fmt(cycleDates.first())}–${fmt(cycleDates.last())}"
                val details = buildString {
                    append("Цикл: $range (${cycleDates.size} дн.)")
                    if (star) append(". В первый день цикла — во вторую смену")
                    disc?.department?.takeIf { it.isNotBlank() }?.let { append(". Кафедра: $it") }
                }
                cycleDates.forEachIndexed { i, d ->
                    val secondShift = star && i == 0 && groupSpecific == null && slot2 != null
                    lessons += Lesson(
                        date = d,
                        slots = listOf(if (secondShift) slot2!! else slot1),
                        subject = name,
                        fullSubject = disc?.name?.let { tidyName(it) }?.takeIf { it.length > name.length } ?: name,
                        kind = LessonKind.PRACTICE,
                        groups = rowGroups.ifEmpty { setOf(group) },
                        location = disc?.place?.ifBlank { null },
                        details = details,
                        online = false,
                        specialTime = secondShift,
                    )
                }
            }
        }

        // --- Лекции и физкультура по потокам ---
        for (d in discs) {
            val isLecture = d.name.contains("лекци", true)
            val isSport = d.name.contains("физическ", true)
            if (!isLecture && !isSport) continue
            for (cell in listOf(d.shift1, d.shift2)) {
                val stream = Regex("""(\d)\s*поток""").find(cell)?.groupValues?.get(1)?.toInt() ?: continue
                val dow = DAY_NAMES.entries.firstOrNull { cell.lowercase().contains(it.key) }?.value ?: continue
                val slot = slotOf(cell.replace(Regex("""с\s*\d{1,2}\.\d{2}\s*-\s*\d{1,2}\.\d{2}"""), "")) ?: continue
                val rangeM = Regex("""с\s*(\d{1,2})\.(\d{2})\s*-\s*(\d{1,2})\.(\d{2})""").find(cell)
                fun dateOf(dd: String, mm: String): LocalDate {
                    val m = mm.toInt()
                    return LocalDate.of(if (m >= 8) startYear else startYear + 1, m, dd.toInt())
                }
                val from = rangeM?.let { dateOf(it.groupValues[1], it.groupValues[2]) } ?: dates.values.min()
                val to = rangeM?.let { dateOf(it.groupValues[3], it.groupValues[4]) } ?: lastStudyDay
                val room = SHORT_ROOM.find(cell)?.let { "${it.groupValues[1]} корпус, ауд. ${it.groupValues[2]}" }
                val subject = tidyName(d.name.replace(Regex("""\s*[Лл]екции"""), ""))
                var day = from
                while (day.dayOfWeek != dow) day = day.plusDays(1)
                while (!day.isAfter(minOf(to, lastStudyDay))) {
                    // Только учебные дни из шапки: праздники и выходные пропускаем.
                    if (day !in studyDays) { day = day.plusWeeks(1); continue }
                    lessons += Lesson(
                        date = day,
                        slots = listOf(slot),
                        subject = ScheduleParser.shortName(subject),
                        fullSubject = subject,
                        kind = if (isLecture) LessonKind.LECTURE else LessonKind.ELECTIVE,
                        groups = groups.toSet(),
                        stream = stream,
                        location = listOfNotNull(room, d.address.ifBlank { null }).joinToString(", ").ifBlank { d.place.ifBlank { null } },
                        details = "$stream поток",
                        online = false,
                        specialTime = false,
                    )
                    day = day.plusWeeks(1)
                }
            }
        }

        // --- Учебная практика (НИР) по субботам ---
        sheet.cells.values.map(::clean).firstOrNull { it.contains("учебная практика", true) && it.contains(Regex("""\d""")) }
            ?.let { lessons += parsePractice(it, startYear, groups.toSet()) }

        val title = listOf("РАСПИСАНИЕ", "ДЛЯ")
            .mapNotNull { p -> sheet.cells.values.map(::clean).firstOrNull { it.uppercase().startsWith(p) } }
            .joinToString(" ")
            .let { ScheduleParser.sentence(it.replace(Regex("""\s+"""), " ")) }
            .ifBlank { "Расписание" }

        val semStart = Regex("""(?i)начало[^\d]{0,40}(\d{1,2})\s+([а-яё]+)\s+(\d{4})""").find(all)?.let { m ->
            monthOf(m.groupValues[2])?.let { runCatching { LocalDate.of(m.groupValues[3].toInt(), it, m.groupValues[1].toInt()) }.getOrNull() }
        } ?: dates.values.min()

        return Schedule(
            title = title,
            groups = groups,
            lessons = lessons.distinctBy { it.key + it.groups.sorted() },
            semesterStart = semStart,
            week1Ranges = emptyList(),
            week2Ranges = emptyList(),
            disciplines = discs.filter { !it.name.contains("лекци", true) }.map { d ->
                DisciplineInfo(
                    name = tidyName(d.name),
                    department = listOf(d.department, d.place).filter { it.isNotBlank() }.joinToString(" · "),
                    attestation = d.attestation,
                )
            },
            electives = emptyList(),
            hasWeekParity = false,
            streams = lessons.mapNotNull { it.stream }.distinct().sorted(),
        )
    }

    private fun fmt(d: LocalDate) = "%02d.%02d".format(d.dayOfMonth, d.monthValue)

    /** Таблица под сеткой: «Дисциплина | Форма аттестации | Кафедра | База | Адрес | Время (1 смена, 2 смена)». */
    private fun parseTable(sheet: Sheet): List<Disc> {
        val header = sheet.cells.entries.firstOrNull { clean(it.value).equals("Дисциплина", true) } ?: return emptyList()
        val hr = header.key.row
        val cols = (1..sheet.maxCol).associateWith { clean(sheet.text(hr, it)).lowercase() }.filterValues { it.isNotEmpty() }
        fun colWith(word: String) = cols.entries.firstOrNull { it.value.contains(word) }?.key
        val cName = header.key.col
        val cAtt = colWith("аттестац")
        val cDep = colWith("кафедр")
        val cBase = colWith("баз")
        val cAddr = colWith("адрес")
        val cTime = colWith("время")
        // Подзаголовки «1 смена» / «2 смена» — в следующей строке.
        val sub = (1..sheet.maxCol).associateWith { clean(sheet.text(hr + 1, it)).lowercase() }
        val cShift1 = sub.entries.firstOrNull { it.value.startsWith("1 смен") }?.key ?: cTime
        val cShift2 = sub.entries.firstOrNull { it.value.startsWith("2 смен") }?.key
        val out = ArrayList<Disc>()
        for (row in hr + 1..sheet.maxRow) {
            val name = clean(sheet.text(row, cName))
            if (name.isEmpty() || name.lowercase().contains("смена")) continue
            if (sheet.mergeOf(row, cName)?.let { it.r1 != row } == true) continue
            fun at(c: Int?) = c?.let { clean(valueAt(sheet, row, it)) }.orEmpty()
            out += Disc(
                name = name,
                attestation = at(cAtt).ifBlank { null },
                department = at(cDep),
                base = at(cBase),
                address = at(cAddr),
                shift1 = cShift1?.let { sheet.text(row, it) }.orEmpty().replace('\n', ' '),
                shift2 = cShift2?.let { sheet.text(row, it) }.orEmpty().replace('\n', ' '),
            )
            if (name.startsWith("Лекции по дисциплинам", true)) break
        }
        return out.filter { it.department.isNotBlank() || it.shift1.isNotBlank() }
    }

    /** «5, 12, 19, 26 сентября, 3 октября … - учебная практика … 8:30-13.10 (6 часов), 14 ноября 8:30-10:00». */
    private fun parsePractice(text: String, startYear: Int, groups: Set<String>): List<Lesson> {
        val datesPart = text.substringBefore(" - ").substringBefore(" – ")
        val dates = ArrayList<LocalDate>()
        val pending = ArrayList<Int>()
        Regex("""(\d{1,2})|([А-Яа-яЁё]+)""").findAll(datesPart).forEach { m ->
            if (m.groupValues[1].isNotEmpty()) pending += m.groupValues[1].toInt()
            else monthOf(m.groupValues[2])?.let { mon ->
                val y = if (mon >= 8) startYear else startYear + 1
                pending.forEach { d -> runCatching { LocalDate.of(y, mon, d) }.getOrNull()?.let(dates::add) }
                pending.clear()
            }
        }
        val rest = text.substring(datesPart.length)
        val main = slotOf(rest) ?: return emptyList()
        // Особое время для отдельных дат: «14 ноября 8:30-10:00».
        val overrides = HashMap<LocalDate, TimeSlot>()
        Regex("""(\d{1,2})\s+([а-яё]+)\s+(\d{1,2}[.:]\d{2}\s*-\s*\d{1,2}[.:]\d{2})""").findAll(rest).forEach { m ->
            val mon = monthOf(m.groupValues[2]) ?: return@forEach
            val y = if (mon >= 8) startYear else startYear + 1
            val d = runCatching { LocalDate.of(y, mon, m.groupValues[1].toInt()) }.getOrNull() ?: return@forEach
            slotOf(m.groupValues[3])?.let { overrides[d] = it }
        }
        val subject = Regex("""«([^»]+)»|"([^"]+)"""").find(rest)?.let { (it.groupValues[1] + it.groupValues[2]).substringBefore("(").trim() }
            ?: "Учебная практика"
        return dates.distinct().map { d ->
            Lesson(
                date = d,
                slots = listOf(overrides[d] ?: main),
                subject = "Практика: $subject",
                fullSubject = "Учебная практика «$subject»",
                kind = LessonKind.ELECTIVE,
                groups = groups,
                location = null,
                details = clean(text),
                online = false,
                specialTime = overrides.containsKey(d),
            )
        }
    }
}
