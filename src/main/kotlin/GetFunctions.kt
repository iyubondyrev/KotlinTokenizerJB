package org.tokenizer

import com.google.common.base.CharMatcher
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.spec.grammar.tools.*


fun listFromNode(node: KotlinParseTree, resultList: MutableList<KotlinParseTree>) {
    resultList.add(node)
    for (child in node.children) {
        listFromNode(child, resultList)
    }
}

fun findDocString(tokens: KotlinTokensList, indexOfDeclarationStart: Int): String {
    if (indexOfDeclarationStart == 0) {
        return ""
    }
    var i = indexOfDeclarationStart - 1
    while (i >= 0) {
        if (tokens[i].type == "DelimitedComment") {
            // we need only docstrings, as far as I understand only /** is a valid docstring
            if (tokens[i].text.startsWith("/**")) {
                return tokens[i].text
            }
            return ""
        }
        if (tokens[i].channel == 1 || tokens[i].type == "NL") {
            i--
            continue
        }
        return ""
    }
    return ""
}

fun adjustIdx(node: KotlinParseTree, tokens: KotlinTokensList, idx: Int): Int {
    if (!(node.text != null && node.children.isEmpty()) || node.name == "EOF") {
        return idx
    }
    val text = node.text
    val type = node.name
    var newIdx = idx
    while (!(tokens[newIdx].text == text &&
                tokens[newIdx].type == type &&
                tokens[newIdx].channel == 0)) {
        newIdx += 1
    }
    return newIdx
}


fun findFunctionsAndDocstrings(listOfNodes: List<KotlinParseTree>, tokens: KotlinTokensList, popularLiterals: PopularLiterals): List<Map<String, String>> {
    val result: MutableList<MutableMap<String, String>> = mutableListOf()
    val curFunctionTokens = mutableListOf<KotlinToken>()
    val curBodyTokens = mutableListOf<KotlinToken>()

    var tokensIdx = 0
    var nodeIdx = 0

    while (nodeIdx < listOfNodes.size) {
        var node = listOfNodes[nodeIdx]
        tokensIdx = adjustIdx(node, tokens, tokensIdx)
        if (node.name == "functionDeclaration") {
            var startOfSignature = -1
            while (node.name != "functionBody") {
                if (node.text != null && node.children.isEmpty()) {
                    if (curFunctionTokens.isEmpty()) {
                        startOfSignature = tokensIdx
                    }
                    curFunctionTokens.add((tokens[tokensIdx]))
                }
                nodeIdx++
                node = listOfNodes[nodeIdx]
                tokensIdx = adjustIdx(node, tokens, tokensIdx)
            }
            while (node.name != "LCURL") {
                nodeIdx++
                node = listOfNodes[nodeIdx]
                tokensIdx = adjustIdx(node, tokens, tokensIdx)
            }
            curBodyTokens.add((tokens[tokensIdx]))

            nodeIdx++
            node = listOfNodes[nodeIdx]
            tokensIdx = adjustIdx(node, tokens, tokensIdx)

            var curlCnt = 1
            while (curlCnt != 0) {
                if (node.text != null && node.children.isEmpty()) {
                    if (node.name == "LCURL") {
                        curlCnt += 1
                    }
                    if (node.name == "RCURL") {
                        curlCnt -= 1
                    }
                    curBodyTokens.add((tokens[tokensIdx]))
                }
                nodeIdx++
                node = listOfNodes[nodeIdx]
                tokensIdx = adjustIdx(node, tokens, tokensIdx)
            }

            val signature = processTokens(curFunctionTokens, popularLiterals)
            val body = processTokens(curBodyTokens, popularLiterals)
            var docstring = findDocString(tokens, startOfSignature)

            if (!CharMatcher.ascii().matchesAllOf(docstring)) {
                // we do not need nonAscii docstring because BPE will go crazy
                docstring = ""
            }

            // let's get rid of unnecessary whitespaces
            val regex = Regex("[ \t\r]+")
            docstring = docstring.replace(regex, " ")

            if (body.isNotEmpty() && signature.isNotEmpty()) {
                result.add(mutableMapOf(("signature" to signature), ("body" to body), ("docstring" to docstring)))
            }
            curFunctionTokens.clear()
            curBodyTokens.clear()
        }
        nodeIdx++
    }
    return result
}