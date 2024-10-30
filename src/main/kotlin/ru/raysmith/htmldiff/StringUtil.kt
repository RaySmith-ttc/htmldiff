package ru.raysmith.htmldiff

object StringUtil {
    fun getString(input: Iterable<*>): String {
        val sb = StringBuilder()

        for (c in input) {
            sb.append(c)
        }

        return sb.toString()
    }
}
