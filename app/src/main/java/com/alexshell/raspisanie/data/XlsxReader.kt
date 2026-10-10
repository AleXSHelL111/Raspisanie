package com.alexshell.raspisanie.data

import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Простая модель листа Excel: текст ячеек и объединённые диапазоны. */
class Sheet(
    val cells: Map<CellRef, String>,
    val merges: List<CellRange>,
) {
    val maxRow: Int = cells.keys.maxOfOrNull { it.row } ?: 0
    val maxCol: Int = cells.keys.maxOfOrNull { it.col } ?: 0

    fun text(row: Int, col: Int): String = cells[CellRef(row, col)].orEmpty()

    /** Диапазон объединения, в который входит ячейка (или null). */
    fun mergeOf(row: Int, col: Int): CellRange? = merges.firstOrNull { it.contains(row, col) }
}

data class CellRef(val row: Int, val col: Int)

data class CellRange(val r1: Int, val c1: Int, val r2: Int, val c2: Int) {
    fun contains(r: Int, c: Int) = r in r1..r2 && c in c1..c2
}

/**
 * Минимальный читатель .xlsx без сторонних библиотек: xlsx — это zip с XML внутри.
 * Читается первый лист книги.
 */
object XlsxReader {

    fun read(input: InputStream): Sheet {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) entries[e.name.removePrefix("/")] = zip.readBytes()
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheetName = firstSheetPath(entries)
            ?: throw IllegalArgumentException("В файле не найден лист с расписанием")
        return parseSheet(entries.getValue(sheetName), shared)
    }

    private fun firstSheetPath(entries: Map<String, ByteArray>): String? {
        // Пытаемся пойти «по правилам»: workbook.xml -> rels -> путь листа.
        runCatching {
            val wb = doc(entries.getValue("xl/workbook.xml"))
            val sheet = wb.getElementsByTagNameNS("*", "sheet").item(0) as Element
            val rid = sheet.getAttributeNS(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"
            ).ifEmpty { sheet.getAttribute("r:id") }
            val rels = doc(entries.getValue("xl/_rels/workbook.xml.rels"))
            val list = rels.getElementsByTagNameNS("*", "Relationship")
            for (i in 0 until list.length) {
                val rel = list.item(i) as Element
                if (rel.getAttribute("Id") == rid) {
                    val target = rel.getAttribute("Target")
                    val path = if (target.startsWith("/")) target.drop(1) else "xl/$target"
                    if (entries.containsKey(path)) return path
                }
            }
        }
        return entries.keys
            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
    }

    private fun doc(bytes: ByteArray) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(bytes.inputStream())

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val d = doc(bytes)
        val items = d.getElementsByTagNameNS("*", "si")
        return List(items.length) { i -> textOf(items.item(i) as Element) }
    }

    /** Склеивает все <t> внутри элемента, кроме фонетических подсказок <rPh>. */
    private fun textOf(el: Element): String {
        val sb = StringBuilder()
        val ts = el.getElementsByTagNameNS("*", "t")
        for (j in 0 until ts.length) {
            val t = ts.item(j)
            if (t.parentNode?.localName == "rPh") continue
            sb.append(t.textContent)
        }
        return sb.toString()
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): Sheet {
        val d = doc(bytes)
        val cells = HashMap<CellRef, String>()
        val cs = d.getElementsByTagNameNS("*", "c")
        for (i in 0 until cs.length) {
            val c = cs.item(i) as Element
            val ref = parseRef(c.getAttribute("r")) ?: continue
            val type = c.getAttribute("t")
            val v = c.getElementsByTagNameNS("*", "v").item(0)?.textContent
            val value = when (type) {
                "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }
                "inlineStr" -> (c.getElementsByTagNameNS("*", "is").item(0) as? Element)?.let(::textOf)
                else -> v
            }
            if (!value.isNullOrBlank()) cells[ref] = value
        }
        val merges = ArrayList<CellRange>()
        val ms = d.getElementsByTagNameNS("*", "mergeCell")
        for (i in 0 until ms.length) {
            val parts = (ms.item(i) as Element).getAttribute("ref").split(":")
            val a = parseRef(parts[0]) ?: continue
            val b = parts.getOrNull(1)?.let(::parseRef) ?: a
            merges += CellRange(a.row, a.col, b.row, b.col)
        }
        return Sheet(cells, merges)
    }

    /** "B12" -> CellRef(12, 2). Столбцы нумеруются с 1. */
    fun parseRef(ref: String): CellRef? {
        val m = Regex("([A-Z]+)(\\d+)").matchEntire(ref.trim().uppercase()) ?: return null
        var col = 0
        for (ch in m.groupValues[1]) col = col * 26 + (ch - 'A' + 1)
        return CellRef(m.groupValues[2].toInt(), col)
    }
}
