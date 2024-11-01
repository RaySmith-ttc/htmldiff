package ru.raysmith.htmldiff

import java.util.*
import java.util.regex.Pattern

object WordSplitter {

    /** Converts Html text into a list of words */

    /**
     * Converts HTML text into a list of words, treating <script>...</script> blocks as single units.
     */
    fun convertHtmlToListOfWords(text: String, blockExpressions: List<Pattern>?): List<String> {
        var mode: Mode = Mode.Character
        val currentWord: MutableList<Char> = ArrayList()
        val words: MutableList<String> = ArrayList()

        val scriptPattern = Pattern.compile("<script.*?>.*?</script>", Pattern.DOTALL)
        val allBlockExpressions = (blockExpressions ?: emptyList()) + listOf(scriptPattern)

        val blockLocations = findBlocks(text, allBlockExpressions)

        val isBlockCheckRequired = blockLocations.isNotEmpty()
        var isGrouping = false
        var groupingUntil = -1
        var currentBlock: Pair<Int, Int?>? = null

        for (index in text.indices) {
            val character = text[index]

            // Don't bother executing block checks if we don't have any blocks to check for!
            if (isBlockCheckRequired) {
                // Check if we have completed grouping a text sequence/block
                if (groupingUntil == index) {
                    groupingUntil = -1
                    isGrouping = false
                }

                if (currentBlock == null) {
                    currentBlock = blockLocations.find { it.first == index }
                }

                if (currentBlock != null) {
                    isGrouping = true
                    groupingUntil = currentBlock.second!!
                }

                // if we are grouping, then we don't care about what type of character we have, it's going to be treated as a word

                // If we are grouping and it's a <script> block, close it as a distinct element
                if (isGrouping && /*inv?.second == groupingUntil*/ index == groupingUntil) {
                    currentWord.add(character)
                    words.add(StringUtil.getString(currentWord))
                    currentWord.clear()
                    mode = Mode.Character
                    isGrouping = false
                    groupingUntil = -1
                    currentBlock = null
                    continue

//                    currentWord.add(character)
//                    mode = Mode.Character
//                    continue
                } else if (isGrouping) {
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

        return words
    }

    /** Finds any blocks that need to be grouped */
    private fun findBlocks(text: String, blockExpressions: List<Pattern>?): List<Pair<Int, Int?>> {
        val blockLocations = mutableListOf<Pair<Int, Int?>>()

        if (blockExpressions == null) {
            return blockLocations
        }

        for (exp in blockExpressions) {
            try {
                val match = exp.matcher(text)
                while (match.find()) {
                    blockLocations.add(match.start() to match.end() - 1)
                }
            } catch (_: Exception) { }
        }
        return blockLocations
    }
}
