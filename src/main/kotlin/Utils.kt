package org.tokenizer

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.spec.grammar.tools.KotlinToken
import org.jetbrains.kotlin.spec.grammar.tools.KotlinTokensList

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

fun processString(tokens: KotlinTokensList, firstQuoteIdx: Int): Pair<String, Int> {
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
