package org.tokenizer

import org.jetbrains.kotlin.spec.grammar.tools.tokenizeKotlinCode
import org.jetbrains.kotlin.spec.grammar.tools.KotlinToken
import org.jetbrains.kotlin.spec.grammar.tools.KotlinTokensList
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UtilsTest {
    @Test
    fun `test processString with normal quotes`() {
        // Setup tokens representing a string in quotes
        val tokens = tokenizeKotlinCode("\"hello\"")
        val result = processString(tokens, 0)
        assertEquals("hello", result.first)
        assertEquals(2, result.second)
    }

    @Test
    fun `test processString with triple quotes`() {
        // Setup tokens representing a string in triple quotes
        val tokens = tokenizeKotlinCode("\"\"\"hello\"\"\"")
        val result = processString(tokens, 0)
        assertEquals("hello", result.first)
        assertEquals(2, result.second)
    }

    @Test
    fun `test processString string templates`() {
        // Setup tokens representing a string in triple quotes
        val templateWithSpaces = tokenizeKotlinCode("\"lol? \${  kek  }\"\nval x = 0")
        val templateWithoutSpaces = tokenizeKotlinCode("\"lol? \${kek}\"\nval x = 0")
        val resWithSpaces = processString(templateWithSpaces, 0)
        val resWithoutSpaces = processString(templateWithoutSpaces, 0)
        print(resWithSpaces)
        assertEquals(resWithSpaces.first, resWithoutSpaces.first)
        assertNotEquals(resWithSpaces.second, resWithoutSpaces.second)
        assertEquals(resWithSpaces.second, 9)
    }

    @Test
    fun `test processString special chars`() {
        // Setup tokens representing a string in triple quotes
        var tokens = tokenizeKotlinCode("\"hello \"")
        var result = processString(tokens, 0)
        assertEquals("helloU+0020", result.first)
        assertEquals(2, result.second)

        tokens = tokenizeKotlinCode("\"hello, guys!\"")
        result = processString(tokens, 0)
        assertEquals("helloU+002CU+0020guys!", result.first)
        assertEquals(2, result.second)
    }

    @Test
    fun `test addEOL does not add when last is EOL`() {
        val tokens = mutableListOf("<STR_LIT>", "<EOL>")
        addEOL(tokens)
        assertEquals(2, tokens.size)
    }

    @Test
    fun `test addEOL does not add when empty`() {
        val tokens = mutableListOf<String>()
        addEOL(tokens)
        assertEquals(0, tokens.size)
    }

    @Test
    fun `test addEOL adds EOL when needed`() {
        val tokens = mutableListOf("<STR_LIT>")
        addEOL(tokens)
        assertTrue(tokens.contains("<EOL>"))
        assertEquals(2, tokens.size)
    }

    @Test
    fun `test processChar removes surrounding single quotes and applies special chars`() {
        val space = KotlinToken("QUOTE_OPEN", "' '", channel = 0)
        var result = processChar(space)
        assertEquals("U+0020", result)
        val comma = KotlinToken("QUOTE_OPEN", "','", channel = 0)
        result = processChar(comma)
        assertEquals("U+002C", result, "Processed character literal should be trimmed and replace spaces with U+0020")
    }

    @Test
    fun `test processChar without any special chars`() {
        val token = KotlinToken("QUOTE_OPEN", "'b'", channel = 0)
        val result = processChar(token)
        assertEquals("b", result)
    }

    @Test
    fun `test trimTokenText`() {
        val tokens = tokenizeKotlinCode("\n@ @ \n@")
        val atBothWs = tokens[0]
        val atPostWs = tokens[1]
        val atPreWs = tokens[2]

        val processedTokens = mutableListOf<String>()
        trimTokenTextAndAdd(atBothWs.text, processedTokens)
        trimTokenTextAndAdd(atPostWs.text, processedTokens)
        trimTokenTextAndAdd(atPreWs.text, processedTokens)
        val resultString = processedTokens.joinToString(" ")
        assertEquals("@ @ <EOL> @", resultString)
    }
}

class ProcessTokensTest {

    @Test
    fun `processTokens test all`() {
        val tokens = tokenizeKotlinCode("val h = \"hello\"\nval c = 'c'\n" +
                "val r = 'r'\nval num = 1.2\n val hex = 0xDEADBEEF\n val b = true\nval n = null\n val lol = \"lol\"\n" +
                "var kek = \"\"\"kek\"\"\"\n var long = 1L")

        val popularLiterals = PopularLiterals(
            mutableListOf("hello"),
            mutableListOf("c"),
            mutableListOf("0xDEADBEEF", "1.2")
        )

        val result = processTokens(tokens, popularLiterals)

        assertEquals("<s> val h = \"<STR_LIT:hello>\" <EOL> val c = '<CHAR_LIT:c>' <EOL> val r = '<CHAR_LIT>'" +
                " <EOL> val num = <NUM_LIT:1.2> <EOL> val hex = <NUM_LIT:0xDEADBEEF> <EOL> val b = true <EOL> val n = null" +
                " <EOL> val lol = \"<STR_LIT>\" <EOL> var kek = \"\"\"<STR_LIT>\"\"\" <EOL> var long = <NUM_LIT>" +
                " </s>\n", result)
    }


    @Test
    fun `processTokens skips bad tokens`() {
        val tokensList = listOf<KotlinToken>(KotlinToken("TEXT", "bad  token", 0))
        val tokens = KotlinTokensList(tokensList)

        val popularLiterals = PopularLiterals(mutableListOf(), mutableListOf(), mutableListOf())
        val result = processTokens(tokens, popularLiterals)
        assertEquals("", result, "Should return an empty string for bad token patterns")
    }

}


class PopularLiteralsTest {

    @Test
    fun `test updatePopularLiterals increments string literals correctly`() {
        val tokens = tokenizeKotlinCode("var h = \"hello\"")

        val stringLiterals = mutableMapOf<String, Int>()
        val charLiterals = mutableMapOf<String, Int>()
        val numLiterals = mutableMapOf<String, Int>()

        updatePopularLiterals(tokens, stringLiterals, charLiterals, numLiterals)

        assertEquals(1, stringLiterals["hello"])
        assert(charLiterals.isEmpty())
        assert(numLiterals.isEmpty())
    }

    @Test
    fun `test updatePopularLiterals skips null and boolean literals`() {
        val tokens = tokenizeKotlinCode("var b = false\nvar n = null")

        val stringLiterals = mutableMapOf<String, Int>()
        val charLiterals = mutableMapOf<String, Int>()
        val numLiterals = mutableMapOf<String, Int>()

        updatePopularLiterals(tokens, stringLiterals, charLiterals, numLiterals)

        assert(stringLiterals.isEmpty())
        assert(charLiterals.isEmpty())
        assert(numLiterals.isEmpty())
    }

    @Test
    fun `test updatePopularLiterals increments character and number literals correctly`() {
        val tokens = tokenizeKotlinCode("var c = 'c'\nvar c_ = 'c'\nvar n = 1.2\nvar n_ = 0xDEADBEEF")

        val stringLiterals = mutableMapOf<String, Int>()
        val charLiterals = mutableMapOf<String, Int>()
        val numLiterals = mutableMapOf<String, Int>()

        updatePopularLiterals(tokens, stringLiterals, charLiterals, numLiterals)

        assertEquals(2, charLiterals["c"])
        assertEquals(1, numLiterals["1.2"])
        assertEquals(1, numLiterals["0xDEADBEEF"])
    }
}
