package ru.raysmith.htmldiff

import java.util.regex.Pattern

object Utils {
    private val openingTagRegex: Pattern = Pattern.compile("(^\\s*<[^>]+>\\s*$)")
    private val closingTagTexRegex: Pattern = Pattern.compile("(^\\s*</[^>]+>\\s*$)")
    private val tagWordRegex: Pattern = Pattern.compile("(<[^\\s>]+)")
    private val whitespaceRegex: Pattern = Pattern.compile("^(\\s|&nbsp;)+$")
    private val wordRegex: Pattern = Pattern.compile("([\\w#@]+)")

    private val SpecialCaseWordTags = arrayOf("<img")

    fun IsTag(item: String): Boolean {
        if (item.startsWith(SpecialCaseWordTags[0])) {
            return false
        }
        return IsOpeningTag(item) || IsClosingTag(item)
    }

    private fun IsOpeningTag(item: String): Boolean {
        try {
            return openingTagRegex.matcher(item).find()
        } catch (e: Exception) {
            return false
        }
    }

    private fun IsClosingTag(item: String): Boolean {
        return closingTagTexRegex.matcher(item).find()
    }

    fun StripTagAttributes(word: String): String {
        var word = word
        val matcher = tagWordRegex.matcher(word)
        if (matcher.find()) {
            val tag = matcher.group(1)
            word = tag + (if (word.endsWith("/>")) "/>" else ">")
        }
        return word
    }

    fun WrapText(text: String, tagName: String, cssClass: String): String {
        return "<$tagName class='$cssClass'>$text</$tagName>"
    }

    fun IsStartOfTag(`val`: Char): Boolean {
        return `val` == '<'
    }

    fun IsEndOfTag(`val`: Char): Boolean {
        return `val` == '>'
    }

    fun IsStartOfEntity(`val`: Char): Boolean {
        return `val` == '&'
    }

    fun IsEndOfEntity(`val`: Char): Boolean {
        return `val` == ';'
    }

    fun IsWhiteSpace(value: String): Boolean {
        return whitespaceRegex.matcher(value).find()
    }

    fun IsWhiteSpace(value: Char): Boolean {
        return Character.isWhitespace(value)
    }

    fun StripAnyAttributes(word: String): String {
        if (IsTag(word)) {
            return StripTagAttributes(word)
        }
        return word
    }

    fun IsWord(text: Char): Boolean {
        return wordRegex.matcher(text.toString()).find()
    }
}