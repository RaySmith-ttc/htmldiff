package ru.raysmith.htmldiff

class MatchFinder(
    private val _oldWords: Array<String>,
    private val _newWords: Array<String>,
    private val _startInOld: Int,
    private val _endInOld: Int,
    private val _startInNew: Int,
    private val _endInNew: Int,
    options: MatchOptions
) {
    private var _wordIndices: HashMap<String, MutableList<Int>?>? = null
    private val _options: MatchOptions = options

    private fun indexNewWords() {
        _wordIndices = HashMap()
        val block: MutableList<String> = ArrayList(_options.BlockSize)
        for (i in _startInNew until _endInNew) {
            // if word is a tag, we should ignore attributes as attribute changes are not supported (yet)
            val word = normalizeForIndex(_newWords[i])
            val key = putNewWord(block, word, _options.BlockSize) ?: continue //TODO check


            try {
                if (_wordIndices!![key] != null) {
                    val indicies = _wordIndices!![key]
                    indicies!!.add(i)
                } else {
                    val tmp: MutableList<Int> = ArrayList()
                    tmp.add(i)
                    _wordIndices!![key] = tmp
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Converts the word to index-friendly value so it can be compared with other similar words
     * */
    private fun normalizeForIndex(word: String): String {
        var res: String? = word
        res = Utils.StripAnyAttributes(res!!)
        if (_options.IgnoreWhitespaceDifferences && Utils.IsWhiteSpace(res)) return " "

        return res
    }

    fun findMatch(): Match? {
        indexNewWords()
        removeRepeatingWords()
        findmatchcalled++
        if (_wordIndices!!.size == 0) return null

        var bestMatchInOld = _startInOld
        var bestMatchInNew = _startInNew
        var bestMatchSize = 0

        var matchLengthAt = HashMap<Int?, Int>()
        val block: MutableList<String> = ArrayList(_options.BlockSize)

        for (indexInOld in _startInOld until _endInOld) {
            val word = normalizeForIndex(_oldWords[indexInOld])

            val index = putNewWord(block, word, _options.BlockSize) ?: continue //TODO check

            val newMatchLengthAt = HashMap<Int?, Int>()

            if (!_wordIndices!!.containsKey(index)) {
                matchLengthAt = newMatchLengthAt
                continue
            }

            for (indexInNew in _wordIndices!![index]!!) {
                val newMatchLength = (if (matchLengthAt.containsKey(indexInNew - 1)) matchLengthAt[indexInNew - 1]!! else 0) + 1
                newMatchLengthAt[indexInNew] = newMatchLength
                if (newMatchLength > bestMatchSize) {
                    bestMatchInOld = indexInOld - newMatchLength + 1 - _options.BlockSize + 1
                    bestMatchInNew = indexInNew - newMatchLength + 1 - _options.BlockSize + 1
                    bestMatchSize = newMatchLength
                }
            }

            matchLengthAt = newMatchLengthAt
        }

        return if (bestMatchSize != 0) {
            Match(bestMatchInOld, bestMatchInNew, bestMatchSize + _options.BlockSize - 1)
        } else null
    }

    /**
     * This method removes words that occur too many times. This way it reduces total count of comparison operations
     * and as result the diff algorithm takes less time. But the side effect is that it may detect false differences of
     * the repeating words.
     * */
    private fun removeRepeatingWords() {
        val threshold = _newWords.size * _options.RepeatingWordsAccuracy
        val res = hashMapOf<String, MutableList<Int>?>()
        _wordIndices!!.entries.forEachIndexed { _, (key, value) ->
            if (value!!.size <= threshold) {
                res[key] = value
            }
        }

        _wordIndices = res
    }

    companion object {
        private fun putNewWord(block: MutableList<String>, word: String, blockSize: Int): String? {
            block.add(word)
            if (block.size > blockSize) {
                block.removeAt(0)
            }

            if (block.size != blockSize) return null

            val result = StringBuilder(blockSize)
            for (s in block) {
                result.append(s)
            }
            return result.toString()
        }

        var findmatchcalled: Int = 0
    }
}
