package com.falakpatel.stridelocal.data

/** Minimal CSV helpers for the backup file (quotes only when needed). Pure Kotlin, unit tested. */
object Csv {
    /** One field. A line break would split the backup line in two, so it becomes a space. */
    fun field(s: String): String {
        val clean = s.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
        return if (clean.any { it == ',' || it == '"' }) "\"" + clean.replace("\"", "\"\"") + "\"" else clean
    }

    /**
     * Joins file lines back into records. Older backups wrote a food name with a line break
     * as it was (inside quotes), which split that record over two lines. A line whose quotes are
     * still open is joined to the next one with a space, unless the next line clearly starts a new
     * record, so one damaged line can never swallow the rest of the file.
     */
    fun records(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var open: String? = null
        for (line in lines) {
            val current = if (open != null && !startsRecord(line)) "$open $line" else {
                open?.let { out += it } // a damaged line: kept as it is, restore skips it
                line
            }
            open = if (current.count { it == '"' } % 2 == 1) current else {
                out += current
                null
            }
        }
        open?.let { out += it }
        return out
    }

    private fun startsRecord(line: String) =
        line.startsWith("#") || (line.length >= 2 && line[1] == ',' && line[0] in "PDF")

    fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
