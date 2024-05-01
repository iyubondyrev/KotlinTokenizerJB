package org.tokenizer

import com.google.common.base.CharMatcher
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.spec.grammar.tools.*


fun getFunDeclarations(node: KotlinParseTree, funcDeclarations: MutableList<KotlinParseTree>) {
    if (node.name == "functionDeclaration") {
        funcDeclarations.add(node)
        return
    }
    for (child in node.children) {
        getFunDeclarations(child, funcDeclarations)
    }
}

fun getFunctionBodyNode(funcDeclarationNode: KotlinParseTree): KotlinParseTree {
    assertEquals(funcDeclarationNode.name, "functionDeclaration")
    for (child in funcDeclarationNode.children) {
        if (child.name == "functionBody") {
            return child
        }
    }
    return funcDeclarationNode
}

fun getNodeTokens(node: KotlinParseTree, tokens: MutableList<KotlinToken>, declaration: Boolean = false) {
    if (node.text != null && node.children.size == 0) {
        val token = KotlinToken(text = node.text!!, type = node.name, channel = 0)
        tokens.add(token)
        return
    }
    for (child in node.children) {
        if (node.name == "functionBody" && declaration) {
            continue
        }
        getNodeTokens(child, tokens, declaration)
    }
}

fun getIndexOfDeclarationStart(filteredTokens: List<KotlinToken>, initialPosOfTokens: List<Int>, funcTokens: List<KotlinToken>): Int {
    if (filteredTokens.isEmpty() || filteredTokens.size < funcTokens.size) return -1

    for (i in 0..filteredTokens.size - funcTokens.size) {
        var match = true
        for (j in funcTokens.indices) {
            if (!(filteredTokens[i + j].text == funcTokens[j].text &&
                        filteredTokens[i + j].type == funcTokens[j].type &&
                        filteredTokens[i + j].channel == funcTokens[j].channel)) {
                match = false
                break
            }
        }
        if (match) return initialPosOfTokens[i]
    }
    return -1
}

fun getFilteredTokens(tokens: KotlinTokensList): Pair<List<KotlinToken>, List<Int>> {
    val filteredTokens = mutableListOf<KotlinToken>()
    val initialPosOfTokens = mutableListOf<Int>()
    for (i in tokens.indices) {
        if (tokens[i].channel == 0) {
            filteredTokens.add(tokens[i])
            initialPosOfTokens.add(i)
        }
    }
    return Pair(filteredTokens, initialPosOfTokens)
}

fun getDocstring(tokens: KotlinTokensList, indexOfDeclarationStart: Int): String {
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

fun findFunctionsAndDocstrings(root: KotlinParseTree, tokens: KotlinTokensList, popularLiterals: PopularLiterals): List<Map<String, String>> {
    val result = mutableListOf<Map<String, String>>()

    val functions: MutableList<KotlinParseTree> = mutableListOf()
    val (filteredTokens, initialPosOfTokens) = getFilteredTokens(tokens)

    getFunDeclarations(root, functions)
    for (funcDeclaration in functions) {
        val funcTokens: MutableList<KotlinToken> = mutableListOf()
        getNodeTokens(funcDeclaration, funcTokens, declaration = true)

        val funcBody = getFunctionBodyNode(funcDeclaration)
        val bodyTokens: MutableList<KotlinToken> = mutableListOf()
        getNodeTokens(funcBody, bodyTokens, declaration = false)

        val declarationStart = getIndexOfDeclarationStart(filteredTokens, initialPosOfTokens, funcTokens)

        val signature = processTokens(funcTokens, popularLiterals)
        val body = processTokens(bodyTokens, popularLiterals)
        var docstring = getDocstring(tokens, declarationStart)

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
    }
    return result
}