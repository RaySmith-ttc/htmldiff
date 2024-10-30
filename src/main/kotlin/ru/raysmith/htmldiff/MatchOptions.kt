package ru.raysmith.htmldiff

class MatchOptions {
    /** Match granularity, defines how many words are joined into single block */
    var BlockSize: Int = 0
    var RepeatingWordsAccuracy: Double = 0.0
    var IgnoreWhitespaceDifferences: Boolean = false
}