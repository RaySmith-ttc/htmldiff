package ru.raysmith.htmldiff

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser
import java.io.File
import java.util.*
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

class HtmlDiff(private var _oldText: String, private var _newText: String) {

    /** Returns true if [word] is `<script>` block */
    private fun isScriptBlock(word: String): Boolean {
        return word.trim().startsWith("<script") && word.endsWith("</script>")
    }


    /** This value defines balance between speed and memory utilization. The higher it is the faster it works and more memory consumes. */
    private val matchGranularityMaximum = 4

    //by default all repeating words should be compared
    private var _content = StringBuilder()


    /** Tracks opening and closing formatting tags to ensure that we don't inadvertently generate invalid html during the diff process. */
    private var _specialTagDiffStack = Stack<String>()

    private lateinit var _newWords: List<String>
    private lateinit var _oldWords: List<String>
    private var _matchGranularity = 0
    private var _blockExpressions: MutableList<Pattern> = mutableListOf()

    /**
     * Defines how to compare repeating words. Valid values are from 0 to 1.
     * This value allows to exclude some words from comparison that eventually
     * reduces the total time of the diff algorithm.
     * 0 means that all words are excluded so the diff will not find any matching words at all.
     * 1 (default value) means that all words participate in comparison so this is the most accurate case.
     * 0.5 means that any word that occurs more than 50% times may be excluded from comparison. This doesn't
     * mean that such words will definitely be excluded but only gives a permission to exclude them if necessary.
     * */
    var repeatingWordsAccuracy: Double = 1.0

    /**
     * If true all whitespaces are considered as equal
     * */
    var ignoreWhitespaceDifferences: Boolean = false

    /**
     * If some match is too small and located far from its neighbors then it is considered as orphan
     * and removed. For example:
     * ```
     * aaaaa bb ccccccccc dddddd ee
     * 11111 bb 222222222 dddddd ee
     * ```
     * will find two matches `bb` and `dddddd ee` but the first will be considered
     * as orphan and ignored, as result it will consider texts `aaaaa bb ccccccccc` and
     * `11111 bb 222222222` as single replacement:
     * ```
     * <del>aaaaa bb ccccccccc</del><ins>11111 bb 222222222</ins> dddddd ee
     * ```
     * This property defines relative size of the match to be considered as orphan, from 0 to 1.
     * 1 means that all matches will be considered as orphans.
     * 0 (default) means that no match will be considered as orphan.
     * 0.2 means that if match length is less than 20% of distance between its neighbors it is considered as orphan.
     * */
    var orphanMatchThreshold: Double = 0.0

    init {
        repeatingWordsAccuracy = 1.0 //by default all repeating words should be compared

        _content = java.lang.StringBuilder()
        _specialTagDiffStack = Stack()
        _blockExpressions = mutableListOf()
    }

    /**
     * Builds the HTML diff output
     *
     * @return HTML diff markup
     * */
    fun build(): String {
        // If there is no difference, don't bother checking for differences
        if (_oldText === _newText) {
            return _newText
        }

        splitInputsToWords()

        _matchGranularity =
            min(matchGranularityMaximum.toDouble(), min(_oldWords.size.toDouble(), _newWords.size.toDouble()))
                .toInt()

        val operations: List<Operation> = operations()

        for (item in operations) {
            performOperation(item)
        }

        return _content.toString()
    }

    /**
     * Uses [expression] to group text together so that any change detected within the group is treated as a single
     * block
     * */
    fun addBlockExpression(expression: Pattern) {
        _blockExpressions.add(expression)
    }

    private fun splitInputsToWords() {
        _oldWords = WordSplitter.convertHtmlToListOfWords(_oldText, _blockExpressions)
        _newWords = WordSplitter.convertHtmlToListOfWords(_newText, _blockExpressions)
    }

    private fun performOperation(operation: Operation) {
        // Skip modification if the content is a script block
        if (operation.startInOld < _oldWords.size && isScriptBlock(_oldWords[operation.startInOld])) {
            _content.append(_newWords.slice(operation.startInNew until operation.endInNew).joinToString(""))
            return
        }

        when (operation.action) {
            Action.Equal -> processEqualOperation(operation)
            Action.Delete -> processDeleteOperation(operation, "diffdel")
            Action.Insert -> processInsertOperation(operation, "diffins")
            Action.None -> {}
            Action.Replace -> processReplaceOperation(operation)
        }
    }

    private fun processReplaceOperation(operation: Operation) {
        processDeleteOperation(operation, "diffmod")
        processInsertOperation(operation, "diffmod")
    }


    private fun processInsertOperation(operation: Operation, cssClass: String) {
        val text: MutableList<String> = ArrayList()
        for (i in operation.startInNew until operation.endInNew) {
            text.add(_newWords[i])
        }
        newString.add(StringUtil.getString(text))
        insertTag("ins", cssClass, text)
    }


    private fun processDeleteOperation(operation: Operation, cssClass: String) {
        val text: MutableList<String> = ArrayList()
        for (i in operation.startInOld until operation.endInOld) {
            text.add(_oldWords[i])
        }
        oldString.add(StringUtil.getString(text))
        insertTag("del", cssClass, text)
    }

    private fun processEqualOperation(operation: Operation) {
        val result: MutableList<String> = ArrayList()
        for (i in operation.startInNew until operation.endInNew) {
            result.add(_newWords[i])
        }

        _content.append(result.joinToString(""))
    }


    /**
     * This method encloses words within a specified tag (ins or del), and adds this into "content",
     * with a twist: if there are words contain tags, it actually creates multiple ins or del,
     * so that they don't include any ins or del. This handles cases like
     *
     * old: `<p>a</p>`
     *
     * new: `<p>ab</p><p>c</b>`
     *
     * diff result:
     * ```
     * <p>a<ins>b</ins></p><p><ins>c</ins></p>
     * ```
     * this still doesn't guarantee valid HTML (hint: think about diffing a text containing ins or
     * del tags), but handles correctly more cases than the earlier version.
     * P.S.: Spare a thought for people who write HTML browsers. They live in this ... every day.
     *
     * @param tag
     * @param cssClass
     * @param words
     * */
    private fun insertTag(tag: String, cssClass: String, words: MutableList<String>) {
//        println(StringUtil.getString(words.toTypedArray<String>()))
//        println("$tag=====================")
        while (true) {
            if (words.size == 0) {
                break
            }

            val nonTags: List<String> = extractConsecutiveWords(words, 0)

            var specialCaseTagInjection = ""
            var specialCaseTagInjectionIsBefore = false

            if (nonTags.isNotEmpty()) {
                val text = Utils.WrapText(nonTags.joinToString(""), tag, cssClass)

                _content.append(text)
            } else {
                // Check if the tag is a special case
                if (_specialCaseOpeningTagRegex.matcher(words[0]).matches()) {
                    _specialTagDiffStack.push(words[0])
                    specialCaseTagInjection = "<ins class='mod'>"
                    if (tag === "del") {
                        words.removeAt(0)

                        // following tags may be formatting tags as well, follow through
                        while (words.size > 0 && _specialCaseOpeningTagRegex.matcher(words[0]).matches()) {
                            words.removeAt(0)
                        }
                    }
                } else if (_specialCaseClosingTags.containsKey(words[0])) {
                    val openingTag = if (_specialTagDiffStack.size == 0) null else _specialTagDiffStack.pop()

                    // If we didn't have an opening tag, and we don't have a match with the previous tag used
                    if (openingTag == null || openingTag !== words[words.size - 1].replace("/", "")) {
                        // do nothing
                    } else {
                        specialCaseTagInjection = "</ins>"
                        specialCaseTagInjectionIsBefore = true
                    }

                    if (tag === "del") {
                        words.removeAt(0)

                        // following tags may be formatting tags as well, follow through
                        while (words.size > 0 && _specialCaseClosingTags.containsKey(words[0])) {
                            words.removeAt(0)
                        }
                    }
                }
            }

            if (words.size == 0 && specialCaseTagInjection.isEmpty()) {
                break
            }

            if (specialCaseTagInjectionIsBefore) {
                _content.append(specialCaseTagInjection + extractConsecutiveWords(words, 1).joinToString(""))
            } else {
                val arr = extractConsecutiveWords(words, 1)
                arr.add(specialCaseTagInjection)
                _content.append(arr.joinToString(""))
            }
        }
    }

    private fun extractConsecutiveWords(words: MutableList<String>, type: Int): MutableList<String> {
        var indexOfFirstTag: Int? = null

        for (i in words.indices) {
            val word = words[i]

            if (i == 0 && word === " ") {
                words[i] = "&nbsp;"
            }

            if (type == 1) {
                if (!Utils.IsTag(word)) {
                    indexOfFirstTag = i
                    break
                }
            } else {
                if (Utils.IsTag(word)) {
                    indexOfFirstTag = i
                    break
                }
            }
        }

        if (indexOfFirstTag != null) {
            val items: MutableList<String> = ArrayList()
            for (i in 0 until indexOfFirstTag) {
                items.add(words[i])
            }
            if (indexOfFirstTag > 0) {
                words.removeRange(0, indexOfFirstTag)
            }
            return items
        } else {
            val items: MutableList<String> = ArrayList()
            for (i in words.indices) {
                items.add(words[i])
            }
            words.removeRange(0, words.size)
            return items
        }
    }

    private fun operations(): List<Operation> {
        var positionInOld = 0
        var positionInNew = 0
        val operations: MutableList<Operation> = ArrayList<Operation>()

        val matches: MutableList<Match> = matchingBlocks()

        matches.add(Match(_oldWords.size, _newWords.size, 0))

        //Remove orphans from matches.
        //If distance between left and right matches is 4 times longer than length of current match then it is considered as orphan
        val mathesWithoutOrphans: List<Match?> = removeOrphans(matches)

        for (match in mathesWithoutOrphans) {
            val matchStartsAtCurrentPositionInOld = (positionInOld == match!!.StartInOld)
            val matchStartsAtCurrentPositionInNew = (positionInNew == match.StartInNew)

            val action = when {
                !matchStartsAtCurrentPositionInOld && !matchStartsAtCurrentPositionInNew -> Action.Replace
                matchStartsAtCurrentPositionInOld && !matchStartsAtCurrentPositionInNew -> Action.Insert
                !matchStartsAtCurrentPositionInOld -> Action.Delete

                // This occurs if the first few words are the same in both versions
                else -> Action.None
            }

            if (action != Action.None) {
                operations.add(
                    Operation(
                        action,
                        positionInOld,
                        match.StartInOld,
                        positionInNew,
                        match.StartInNew
                    )
                )
            }

            if (match.Size != 0) {
                operations.add(
                    Operation(
                        Action.Equal,
                        match.StartInOld,
                        match.EndInOld,
                        match.StartInNew,
                        match.EndInNew
                    )
                )
            }

            positionInOld = match.EndInOld
            positionInNew = match.EndInNew
        }

        return operations
    }

    private fun removeOrphans(matches: List<Match>): List<Match?> {
        var prev: Match? = null
        var curr: Match? = null
        val tmp: MutableList<Match?> = ArrayList<Match?>()
        for (next in matches) {
            if (curr == null) {
                prev = Match(0, 0, 0)
                curr = next
                continue
            }


            if (prev?.EndInOld == curr.StartInOld && prev.EndInNew == curr.StartInNew
                || curr.EndInOld == next.StartInOld && curr.EndInNew == next.StartInNew
            )  //if match has no diff on the left or on the right
            {
                tmp.add(curr)
                prev = curr
                curr = next
                continue
            }

//            int oldDistanceInChars = Enumerable.Range(prev.EndInOld, next.StartInOld - prev.EndInOld)
//                    .Sum(i => _oldWords[i].length);
            var oldDistanceInChars = 0
            var temp: Int = prev!!.EndInOld
            for (i in 0..(next.StartInOld - prev.EndInOld)) {
                if (temp >= _oldWords.size) continue
                oldDistanceInChars += _oldWords[temp].length
                temp++
            }

//            int newDistanceInChars = Enumerable.Range(prev.EndInNew, next.StartInNew - prev.EndInNew)
//                    .Sum(i => _newWords[i].length);
            var newDistanceInChars = 0
            val temp2: Int = prev.EndInNew
            for (i in 0..(next.StartInNew - prev.EndInNew)) {
                newDistanceInChars += _newWords[temp2].length
                temp++
            }
            var currMatchLengthInChars = 0
            val temp3: Int = curr.StartInNew
            for (i in 0..(curr.EndInNew - curr.StartInNew)) {
                currMatchLengthInChars += _newWords[temp3].length
                temp++
            }
            if (currMatchLengthInChars > max(
                    oldDistanceInChars.toDouble(),
                    newDistanceInChars.toDouble()
                ) * orphanMatchThreshold
            ) {
                tmp.add(curr)
            }
            prev = curr
            curr = next
        }

        tmp.add(curr)
        return tmp
    }

    private fun matchingBlocks(): MutableList<Match> {
        val matchingBlocks: MutableList<Match> = ArrayList<Match>()
        findMatchingBlocks(0, _oldWords.size, 0, _newWords.size, matchingBlocks)
        return matchingBlocks
    }

    private fun findMatchingBlocks(
        startInOld: Int,
        endInOld: Int,
        startInNew: Int,
        endInNew: Int,
        matchingBlocks: MutableList<Match>
    ) {
        val match: Match? = findMatch(startInOld, endInOld, startInNew, endInNew)

        if (match != null) {
            if (startInOld < match.StartInOld && startInNew < match.StartInNew) {
                findMatchingBlocks(startInOld, match.StartInOld, startInNew, match.StartInNew, matchingBlocks)
            }

            matchingBlocks.add(match)

            if (match.EndInOld < endInOld && match.EndInNew < endInNew) {
                findMatchingBlocks(match.EndInOld, endInOld, match.EndInNew, endInNew, matchingBlocks)
            }
        }
    }

    private fun findMatch(startInOld: Int, endInOld: Int, startInNew: Int, endInNew: Int): Match? {
        // For large texts it is more likely that there is a Match of size bigger than maximum granularity.
        // If not then go down and try to find it with smaller granularity.
        for (i in _matchGranularity downTo 1) {
            val options = MatchOptions()

            options.BlockSize = i
            options.RepeatingWordsAccuracy = repeatingWordsAccuracy
            options.IgnoreWhitespaceDifferences = ignoreWhitespaceDifferences

            val finder = MatchFinder(_oldWords, _newWords, startInOld, endInOld, startInNew, endInNew, options)
            val match: Match? = finder.findMatch()
            if (match != null) return match
        }
        return null
    }

    companion object {
        private val _specialCaseClosingTags = HashMap<String, Int>()

        private val _specialCaseOpeningTagRegex: Pattern = Pattern.compile(
            "<((strong)|(b)|(i)|(em)|(big)|(small)|(u)|(sub)|(sup)|(strike)|(s))[>\\s]+"
        )

        fun execute(oldFile: File, siteUrl: String, resourcesPath: String): String? {
            val html = Jsoup.connect(siteUrl).get()
            return execute(oldFile.readText(), html.outerHtml(), reparseNewText = false)
        }

        fun Document.applyOutputSettingsPreset(): Document {
            outputSettings()
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(true)
                .charset(Charsets.UTF_16)

            return this
        }

        fun execute(oldText: String, newText: String, reparseNewText: Boolean = true): String? {
            val html1 = Jsoup.parse(oldText, Parser.htmlParser()).applyOutputSettingsPreset().outerHtml()
            val html2 = if (reparseNewText) Jsoup.parse(newText, Parser.htmlParser()).applyOutputSettingsPreset().outerHtml() else newText

            val html = HtmlDiff(html1, html2).build()
                ?.replace("<del class='diffmod'>\\r?\\n?\\s*</del>".toRegex(), "") // TODO
                ?.replace("<del class='diffdel'>\\r?\\n?\\s*</del>".toRegex(), "")
                ?.replace("<ins class='diffmod'>\\r?\\n?\\s*</ins>".toRegex(), "")
                ?.replace("<ins class='diffins'>\\r?\\n?\\s*</ins>".toRegex(), "")
                ?.toByteArray()
                ?: return null

            return Jsoup.parse(String(html), Parser.htmlParser()).apply {
                applyOutputSettingsPreset()
                head().append(ClassLoader.getSystemClassLoader().getResource("head.html")?.readText() ?: "")
            }.outerHtml()
        }

        var oldString: MutableList<String> = ArrayList()
        var newString: MutableList<String> = ArrayList()

        val hashMap: HashMap<String, String>
            //zxl返回最终结果
            get() {
                val res = HashMap<String, String>()
                for (i in newString.indices) {
                    res[newString[i].trim { it <= ' ' }] = oldString[i]
                }
                return res
            }
    }
}