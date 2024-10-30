package ru.raysmith.htmldiff

import java.util.*
import java.util.regex.Pattern

object WordSplitter {

    /** Converts Html text into a list of words */
    fun convertHtmlToListOfWords(text: String, blockExpressions: List<Pattern>?): Array<String> {
        var mode: Mode = Mode.Character
        val currentWord: MutableList<Char> = ArrayList()
        val words: MutableList<String> = ArrayList()

        val blockLocations = findBlocks(text, blockExpressions)

        val isBlockCheckRequired = blockLocations.size() > 0
        var isGrouping = false
        var groupingUntil = -1

        for (index in text.indices) {
            val character = text[index]

            // Don't bother executing block checks if we don't have any blocks to check for!
            if (isBlockCheckRequired) {
                // Check if we have completed grouping a text sequence/block
                if (groupingUntil == index) {
                    groupingUntil = -1
                    isGrouping = false
                }

                // Check if we need to group the next text sequence/block
                val until = 0
                if (blockLocations[index] != null) {
                    isGrouping = true
                    groupingUntil = until
                }

                // if we are grouping, then we don't care about what type of character we have, it's going to be treated as a word
                if (isGrouping) {
                    currentWord.add(character)
                    mode = Mode.Character
                    continue
                }
            }

            when (mode) {
                Mode.Character -> when {
                    Utils.IsStartOfTag(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }

                        currentWord.clear()
                        currentWord.add('<')
                        mode = Mode.Tag
                    }
                    Utils.IsStartOfEntity(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }

                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Entity
                    }
                    Utils.IsWhiteSpace(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }
                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Whitespace
                    }
                    Utils.IsWord(character)
                            && (currentWord.size == 0 || Utils.IsWord(currentWord[currentWord.size - 1])) -> {
                        currentWord.add(character)
                    }
                    else -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }
                        currentWord.clear()
                        currentWord.add(character)
                    }
                }

                Mode.Tag -> when {
                    Utils.IsEndOfTag(character) -> {
                        currentWord.add(character)
                        words.add(StringUtil.getString(currentWord))
                        currentWord.clear()

                        mode = if (Utils.IsWhiteSpace(character)) Mode.Whitespace else Mode.Character
                    }
                    else -> currentWord.add(character)
                }

                Mode.Whitespace -> when {
                    Utils.IsStartOfTag(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }
                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Tag
                    }
                    Utils.IsStartOfEntity(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }

                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Entity
                    }
                    Utils.IsWhiteSpace(character) -> {
                        currentWord.add(character)
                    }
                    else -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }

                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Character
                    }
                }

                Mode.Entity -> when {
                    Utils.IsStartOfTag(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }

                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Tag
                    }
                    Character.isWhitespace(character) -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }
                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Whitespace
                    }
                    Utils.IsEndOfEntity(character) -> {
                        var switchToNextMode = true
                        if (currentWord.size != 0) {
                            currentWord.add(character)
                            words.add(StringUtil.getString(currentWord))

                            //join &nbsp; entity with last whitespace
                            if (words.size > 2 && Utils.IsWhiteSpace(words[words.size - 2]) && Utils.IsWhiteSpace(words[words.size - 1])) {
                                val w1 = words[words.size - 2]
                                val w2 = words[words.size - 1]
                                words.removeRange(words.size - 2, 2)
                                currentWord.clear()
                                for (tmp in w1.toCharArray()) {
                                    currentWord.add(tmp)
                                }
                                for (tmp in w2.toCharArray()) {
                                    currentWord.add(tmp)
                                }
                                mode = Mode.Whitespace
                                switchToNextMode = false
                            }
                        }
                        if (switchToNextMode) {
                            currentWord.clear()
                            mode = Mode.Character
                        }
                    }
                    Utils.IsWord(character) -> currentWord.add(character)
                    else -> {
                        if (currentWord.size != 0) {
                            words.add(StringUtil.getString(currentWord))
                        }
                        currentWord.clear()
                        currentWord.add(character)
                        mode = Mode.Character
                    }
                }
            }
        }
        if (currentWord.size != 0) {
            words.add(StringUtil.getString(currentWord))
        }

        return words.toTypedArray<String>()
    }

    /** Finds any blocks that need to be grouped */
    private fun findBlocks(text: String, blockExpressions: List<Pattern>?): Dictionary<Int, Int?> {
        val blockLocations: Dictionary<Int, Int?> = Hashtable()

        if (blockExpressions == null) {
            return blockLocations
        }

        for (exp in blockExpressions) {
            try {
                val match = exp.matcher(text)
                while (match.find()) {
                    blockLocations.put(match.start(), match.end())
                }
            } catch (_: Exception) { }
        }
        return blockLocations
    }
}
