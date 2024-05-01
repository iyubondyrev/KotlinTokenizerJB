package org.tokenizer

import com.google.common.base.CharMatcher
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.spec.grammar.tools.KotlinToken

@Serializable
data class PopularLiterals(
    val str: MutableList<String>,
    val char: MutableList<String>,
    val num: MutableList<String>
)

val specialChars = mapOf(" " to "U+0020", "," to "U+002C")

val literalNames = listOf(
    "RealLiteral",
    "FloatLiteral",
    "DoubleLiteral",
    "IntegerLiteral",
    "HexLiteral",
    "BinLiteral",
    "UnsignedLiteral",
    "LongLiteral",
    "BooleanLiteral",
    "NullLiteral",
    "CharacterLiteral"
)

fun processString(tokens: List<KotlinToken>, firstQuoteIdx: Int): Pair<String, Int> {
    val typeOfFirstQuote = tokens[firstQuoteIdx].type
    var tripleQuoteCnt = if (typeOfFirstQuote == "TRIPLE_QUOTE_OPEN") 1 else 0
    var quoteCnt = if (typeOfFirstQuote == "QUOTE_OPEN") 1 else 0
    var i = firstQuoteIdx + 1
    val resStringBuilder = StringBuilder()
    while (true) {
        val curType = tokens[i].type
        val curText = tokens[i].text
        if (curType == "QUOTE_CLOSE") {
            quoteCnt -= 1
        }
        if (curType == "TRIPLE_QUOTE_CLOSE") {
            tripleQuoteCnt -= 1
        }
        if (quoteCnt < 0 || tripleQuoteCnt < 0) {
            throw RuntimeException("Invalid quote sequence")
        }
        if (quoteCnt == 0 && tripleQuoteCnt == 0) {
            break
        }
        if (curType == "QUOTE_OPEN") {
            quoteCnt += 1
        }
        if (curType == "TRIPLE_QUOTE_OPEN") {
            tripleQuoteCnt += 1
        }
        if (tokens[i].channel != 1) {
            // I think it is better this way, because
            // "${lol + kek}" == "${lol   +    kek}"
            resStringBuilder.append(curText)
        }
        i++
    }
    var stringLiteral = resStringBuilder.toString()
    specialChars.forEach { (key, value) ->
        stringLiteral = stringLiteral.replace(key, value)
    }
    return Pair<String, Int>(stringLiteral, i)
}

fun processChar(charToken: KotlinToken): String {
    var charLiteral = charToken.text.trim().trim('\'') // because a char has '' even when we extract the text
    charLiteral = specialChars.getOrDefault(charLiteral, charLiteral)
    return charLiteral
}

fun trimTokenTextAndAdd(tokenText: String, processedTokens: MutableList<String>) {
    // because some tokens has newlines/whitespaces in their text representation
    val withoutNewLines = tokenText.trim()
    if (withoutNewLines.isEmpty()) {
        return
    }
    if (tokenText.startsWith("\n")) {
        addEOL(processedTokens)
    }
    processedTokens.add(withoutNewLines)
    if (tokenText.endsWith("\n")) {
        addEOL(processedTokens)
    }
}

fun addEOL(processedTokens: MutableList<String>) {
    if (processedTokens.isNotEmpty() && processedTokens.last() != "<EOL>") {
        processedTokens.add("<EOL>")
    }
}


fun processTokens(tokens: List<KotlinToken>, popularLiterals: PopularLiterals): String {
    val processedTokens = mutableListOf<String>()
    var i = 0

    while (i < tokens.size) {
        if (tokens[i].channel == 1) { // 1 channel for comments, whitespaces => skip
            i++
            continue
        }
        when (tokens[i].type) {
            "QUOTE_OPEN", "TRIPLE_QUOTE_OPEN" -> {
                val (stringLiteral, newI) = processString(tokens, i)
                if (tokens[i].type == "QUOTE_OPEN") {
                    processedTokens.add(
                        if (stringLiteral in popularLiterals.str) "\"<STR_LIT:${stringLiteral}>\""
                        else "\"<STR_LIT>\""
                    )
                } else {
                    processedTokens.add(
                        if (stringLiteral in popularLiterals.str) "\"\"\"<STR_LIT:${stringLiteral}>\"\"\""
                        else "\"\"\"<STR_LIT>\"\"\""
                    )
                }
                i = newI
            }
            in literalNames -> {
                if (tokens[i].type !in listOf("NullLiteral", "BooleanLiteral")) {
                    if (tokens[i].type == "CharacterLiteral") {
                        val charLiteral = processChar(tokens[i])
                        processedTokens.add(if (charLiteral in popularLiterals.char) "'<CHAR_LIT:${charLiteral}>'" else "'<CHAR_LIT>'")
                    } else {
                        val numLiteral = tokens[i].text
                        processedTokens.add(if (numLiteral in popularLiterals.num) "<NUM_LIT:${numLiteral}>" else "<NUM_LIT>")
                    }
                } else {
                    processedTokens.add(tokens[i].text)
                }
            }
            "NL", "UNICODE_CLASS_NL", "Inside_NL" -> {
                addEOL(processedTokens)
            }
            "ErrorCharacter" -> { // if there is some crazy bytes we most likely want to skip this file
                return ""
            }
            else -> {
                // because some tokens has newlines/whitespaces in their text representation
                trimTokenTextAndAdd(tokens[i].text, processedTokens)
            }
        }
        i++
    }
    var badTokens = false
    for (stringToken in processedTokens) {
        // if it is a literal then ofc it can contain \n and double whitespaces
        if (stringToken.contains("LIT")) {
            continue
        }
        // we will skip empty files as well as files that gave us newlines/double whitespace after processing
        // because in this case they are most likely not good
        // they might be lexically correct, but in most cases they are trash
        if (stringToken.contains("\n") || stringToken.contains("  ")) {
            badTokens = true
        }
        val isAscii = CharMatcher.ascii().matchesAllOf(stringToken)
        // if there are non-ascii chars in the token text representation
        // then we want to skip this file, because with non-ascii chars BPE
        // is going crazy. I checked the datasets for python and java (CodeXGLUE)
        // and there are no non-ascii chars at all, so we won't have them as well
        if (!isAscii) {
            badTokens = true
        }
    }
    if (badTokens || processedTokens.size == 0) {
        return ""
    }
    val resultString = processedTokens.joinToString(" ")
    return resultString

}
