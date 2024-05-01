package org.tokenizer

import org.jetbrains.kotlin.spec.grammar.tools.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStream
import kotlin.text.Charsets.UTF_8

class UtilsGeneralTest {
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

        assertEquals("val h = \"<STR_LIT:hello>\" <EOL> val c = '<CHAR_LIT:c>' <EOL> val r = '<CHAR_LIT>'" +
                " <EOL> val num = <NUM_LIT:1.2> <EOL> val hex = <NUM_LIT:0xDEADBEEF> <EOL> val b = true <EOL> val n = null" +
                " <EOL> val lol = \"<STR_LIT>\" <EOL> var kek = \"\"\"<STR_LIT>\"\"\" <EOL> var long = <NUM_LIT>", result)
    }

    @Test
    fun `processTokens ErrorCharacters`() {
        var tokens = tokenizeKotlinCode("val x = 1\n" +
                "\u2028\n" +
                "val y = 2")

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf("1", "2")
        )

        var result = processTokens(tokens, popularLiterals)
        assertEquals("", result)

        tokens = tokenizeKotlinCode("val x = 1\n" +
                "\u000Bval y = 2")
        result = processTokens(tokens, popularLiterals)
        assertEquals("", result)
    }



    @Test
    fun `processTokens skips bad tokens`() {
        val tokensList = listOf<KotlinToken>(KotlinToken("TEXT", "bad  token", 0))
        val tokens = KotlinTokensList(tokensList)

        val popularLiterals = PopularLiterals(mutableListOf(), mutableListOf(), mutableListOf())
        val result = processTokens(tokens, popularLiterals)
        assertEquals("", result, "Should return an empty string for bad token patterns")
    }

    @Test
    fun `processTokens skips non-ascii`() {
        var tokens = tokenizeKotlinCode("val с = 1") // russian c

        val popularLiterals = PopularLiterals(
            mutableListOf("привет"),
            mutableListOf(),
            mutableListOf("1", "2")
        )

        var result = processTokens(tokens, popularLiterals)
        assertEquals("", result)

        tokens = tokenizeKotlinCode("val h = \"привет\"")
        result = processTokens(tokens, popularLiterals)
        // we can keep non-ascii in str_lit because bpe does not touch special symbols like this
        assertEquals("val h = \"<STR_LIT:привет>\"", result)
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

    @Test
    fun `test updatePopularLiterals empty string`() {
        val tokens = tokenizeKotlinCode("var a = \"\"\nvar c_ = \"\"")

        val stringLiterals = mutableMapOf<String, Int>()
        val charLiterals = mutableMapOf<String, Int>()
        val numLiterals = mutableMapOf<String, Int>()

        updatePopularLiterals(tokens, stringLiterals, charLiterals, numLiterals)

        assertEquals(null, charLiterals[""])
    }

    @Test
    fun `test simple`() {
        val tokens = tokenizeKotlinCode("/**\n" +
                " * Allocates variable with given value type and initializes it with given value.\n" +
                " */\n" +
                "@Suppress(\"FINAL_UPPER_BOUND\")\n" +
                "@ExperimentalForeignApi\n" +
                "public fun <T : Byte> NativePlacement.alloc(value: T): ByteVarOf<T> =\n" +
                "        alloc<ByteVarOf<T>> { this.value = value }")
        print(tokens)
    }

    @Test
    fun `test simple_`() {
        println(tokenizeKotlinCode("/**\n"))
        println(tokenizeKotlinCode("/*\n"))
    }

}

class GetFunctionsTest {

    private val simpleExample = """
        /**
        * simple
        */
        fun foo(
        
        ) {
            println("bar")
        }
    """.trimIndent()

    private val simpleExample2Functions = """
        /**
        * simple
        */
        fun foo() {
            println("bar")
        }
        
        /**
        * simple
        */
        fun bar() {
            println("foo")
        }
    """.trimIndent()

    private val exampleWithNonAsciiBody = """
        /**
        * simple
        */
        fun foo() {
            val ф = "привет"
        }
    """.trimIndent()

    private val exampleWithNonAsciiSignature = """
        /**
        * simple
        */
        fun foo(лол: String) {
            println("bar")
        }
    """.trimIndent()

    private val exampleWithNonAsciiDocstring = """
        /**
        * simple докстринг
        */
        fun foo() {
            println("bar")
        }
    """.trimIndent()

    private val exampleWithNestedFunction = """
        /**
        * simple
        */
        fun foo() {
            fun kek() {
                println("nested")
            }
        }
    """.trimIndent()

    private val exampleWithNestedFunction2 = """
        /**
        * simple
        */
        fun foo() {
            fun kek() {
                fun oneMore() {
                    println("nested")
                }
            }
        }
    """.trimIndent()

    private val exampleWithNestedFunctionDocstring = """
        /**
        * simple
        */
        fun foo() {
            /**
            * nested docstring
            */
            fun kek() {
                println("nested")
            }
        }
    """.trimIndent()

    private val exampleWithNestedFunctionDocstring2 = """
        /**
        * simple
        */
        fun foo() {
            /**
            * nested docstring
            */
            fun kek() {
                /**
                * nested nested docstring
                */
                fun oneMore() {
                    println("nested")
                }
            }
        }
    """.trimIndent()

    private val exampleWithOtherTokensAfterDoc = """
        /**
        * Docstring with bad tokens after
        */    
        
        val lol = "kek"
        
        fun foo() {
            println("bar")
        }
    """.trimIndent()

    val exampleWithComplicatedDoc = """
        /**
        * Not so simple docstring
        * @param param
        * @param param2
        * @return 
        */
        fun notMain(param: Int, param2: String): String {
            print("Lol")
            return "Cheburek"
        }
    """.trimIndent()

    private val exampleWithNewLinesAfterDoc = """
        /**
        * Simple docstring with new lines after
        */     
        
        
        fun main() {
            print("Kek")
        }
    """.trimIndent()

    private val complicatedExample = """
        /**
         * Returns 4th *element* from the list.
         * 
         * Throws an [IndexOutOfBoundsException] if the size of this list is less than 4.
         */
        @kotlin.internal.InlineOnly
        public inline operator fun <T> List<T>.component4(): T {
            return get(3)
        }

        /**
         * Returns 5th *element* from the list.
         * 
         * Throws an [IndexOutOfBoundsException] if the size of this list is less than 5.
         */
        @kotlin.internal.InlineOnly
        public inline operator fun <T> List<T>.component5(): T {
            return get(4)
        }

        /**
         * Returns `true` if [element] is found in the collection.
         */
        public operator fun <@kotlin.internal.OnlyInputTypes T> Iterable<T>.contains(element: T): Boolean {
            if (this is Collection)
                return contains(element)
            return indexOf(element) >= 0
        }

        /**
         * Returns an element at the given [index] or throws an [IndexOutOfBoundsException] if the [index] is out of bounds of this collection.
         * 
         * @sample samples.collections.Collections.Elements.elementAt
         */
        public fun <T> Iterable<T>.elementAt(index: Int): T {
            if (this is List)
                return get(index)
            return elementAtOrElse(index) { throw IndexOutOfBoundsException("Collection doesn't contain element at index index.") }
        }

        /**
         * Returns an element at the given [index] or throws an [IndexOutOfBoundsException] if the [index] is out of bounds of this list.
         * 
         * @sample samples.collections.Collections.Elements.elementAt
         */
        @kotlin.internal.InlineOnly
        public inline fun <T> List<T>.elementAt(index: Int): T {
            return get(index)
        }

        /**
         * Returns an element at the given [index] or the result of calling the [defaultValue] function if the [index] is out of bounds of this collection.
         * 
         * @sample samples.collections.Collections.Elements.elementAtOrElse
         */
        public fun <T> Iterable<T>.elementAtOrElse(index: Int, defaultValue: (Int) -> T): T {
            contract {
                callsInPlace(defaultValue, InvocationKind.AT_MOST_ONCE)
            }
            if (this is List)
                return this.getOrElse(index, defaultValue)
            if (index < 0)
                return defaultValue(index)
            val iterator = iterator()
            var count = 0
            while (iterator.hasNext()) {
                val element = iterator.next()
                if (index == count++)
                    return element
            }
            return defaultValue(index)
        }
    """.trimIndent()

    private val exampleFunctionInClass = """
class Pet {
    constructor(owner: Person) {
        owner.pets.add(this)
    }
    
    /**
    * simple
    */
    fun method() {
        println("method")
    }
}
        
    """.trimIndent()

    private val exampleFunctionInClassInClass = """
class Pet {
    constructor(owner: Person) {
        owner.pets.add(this)
    }
    
    /**
    * simple
    */
    fun method() {
        println("method")
    }
    
    fun method2() {
        println("method")
    }
    
    class Sub {
        fun inside() {
            println("method")
        }
        
        /**
        * simple
        */  
        
        fun inside2() {
            println("method")
        }
    }
}        
    """.trimIndent()

    private val exampleFunctionInInterface = """
interface MyInterface {
    fun bar() {}
    fun foo() {
      println("foo")
    }
}
    """.trimIndent()

    private val exampleNoBody = """
        fun foo() {
            val a = 1
            val f = { x: Int ->
                val y = x + a
                use(a)
            }
        }

        fun use(vararg a: Any?) = a
    """.trimIndent()

    @Test
    fun `test findFunctionsAndDocstrings simple`() {
        val tokens = tokenizeKotlinCode(simpleExample)
        val root = parseKotlinCode(tokens)
        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                "* simple\n" +
                "*/", res[0]["docstring"])
    }


    @Test
    fun `test findFunctionsAndDocstrings 2 functions`() {
        val tokens = tokenizeKotlinCode(simpleExample2Functions)
        val root = parseKotlinCode(tokens)
        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(2, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    "* simple\n" +
                    "*/", res[0]["docstring"])


        assertEquals("fun bar ( )", res[1]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[1]["body"])
        assertEquals(
            "/**\n" +
                    "* simple\n" +
                    "*/", res[1]["docstring"])
    }


    @Test
    fun `test findFunctionsAndDocstrings nonAsciiBody`() {
        val tokens = tokenizeKotlinCode(exampleWithNonAsciiBody)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(0, res.size)
    }

    @Test
    fun `test findFunctionsAndDocstrings nonAsciiSignature`() {
        val tokens = tokenizeKotlinCode(exampleWithNonAsciiSignature)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(0, res.size)
    }

    @Test
    fun `test findFunctionsAndDocstrings nonAsciiDocstring`() {
        val tokens = tokenizeKotlinCode(exampleWithNonAsciiDocstring)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "", res[0]["docstring"])
    }

    @Test
    fun `test findFunctionsAndDocstrings nestedFunction`() {
        val tokens = tokenizeKotlinCode(exampleWithNestedFunction)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> fun kek ( ) { <EOL> println ( \"<STR_LIT>\" ) <EOL> } <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    "* simple\n" +
                    "*/", res[0]["docstring"])
    }

    @Test
    fun `test findFunctionsAndDocstrings nestedFunction2`() {
        val tokens = tokenizeKotlinCode(exampleWithNestedFunction2)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> fun kek ( ) { <EOL> fun oneMore ( ) { <EOL> println ( \"<STR_LIT>\" ) <EOL> } <EOL> } <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    "* simple\n" +
                    "*/", res[0]["docstring"])
    }

    @Test
    fun `test findFunctionsAndDocstrings nestedFunctionWithDocstring`() {
        val tokens = tokenizeKotlinCode(exampleWithNestedFunctionDocstring)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> fun kek ( ) { <EOL> println ( \"<STR_LIT>\" ) <EOL> } <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    "* simple\n" +
                    "*/", res[0]["docstring"])
    }

    @Test
    fun `test findFunctionsAndDocstrings nestedFunctionWithDocstring2`() {
        val tokens = tokenizeKotlinCode(exampleWithNestedFunctionDocstring2)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> fun kek ( ) { <EOL> fun oneMore ( ) { <EOL> println ( \"<STR_LIT>\" ) <EOL> } <EOL> } <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    "* simple\n" +
                    "*/", res[0]["docstring"])
    }

    @Test
    fun `test findFunctionsAndDocstrings OtherTokensAfterDoc`() {
        val tokens = tokenizeKotlinCode(exampleWithOtherTokensAfterDoc)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun foo ( )", res[0]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "", res[0]["docstring"])
    }


    @Test
    fun `test findFunctionsAndDocstrings NewLinesAfterDoc`() {
        val tokens = tokenizeKotlinCode(exampleWithNewLinesAfterDoc)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        assertEquals("fun main ( )", res[0]["signature"])
        assertEquals("{ <EOL> print ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    "* Simple docstring with new lines after\n" +
                    "*/", res[0]["docstring"])
    }


    @Test
    fun `test findFunctionsAndDocstrings complicated`() {
        val tokens = tokenizeKotlinCode(complicatedExample)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(6, res.size)
        assertEquals("@ kotlin . internal . InlineOnly <EOL> public inline operator fun < T > List < T > . component4 ( ) : T", res[0]["signature"])
        assertEquals("{ <EOL> return get ( <NUM_LIT> ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    " * Returns 4th *element* from the list.\n" +
                    " * \n" +
                    " * Throws an [IndexOutOfBoundsException] if the size of this list is less than 4.\n" +
                    " */", res[0]["docstring"])
    }


    @Test
    fun `test findFunctionsAndDocstrings functionInClass`() {
        val tokens = tokenizeKotlinCode(exampleFunctionInClass)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(1, res.size)
        println(res)
        assertEquals("fun method ( )", res[0]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    " * simple\n" +
                    " */", res[0]["docstring"])
    }



    @Test
    fun `test findFunctionsAndDocstrings functionInClassInClass`() {
        val tokens = tokenizeKotlinCode(exampleFunctionInClassInClass)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)

        assertEquals(4, res.size)
        println(res)
        assertEquals("fun method ( )", res[0]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    " * simple\n" +
                    " */", res[0]["docstring"])

        assertEquals("fun inside2 ( )", res[3]["signature"])
        assertEquals("{ <EOL> println ( \"<STR_LIT>\" ) <EOL> }", res[0]["body"])
        assertEquals(
            "/**\n" +
                    " * simple\n" +
                    " */", res[0]["docstring"])
    }

    @Test
    fun `test findFunctionsAndDocstrings functionInInterface`() {
        val tokens = tokenizeKotlinCode(exampleFunctionInInterface)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)
        println(res)
        assertEquals(2, res.size)
    }


    @Test
    fun `test findFunctionsAndDocstrings no body`() {
        val tokens = tokenizeKotlinCode(exampleNoBody)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)
        println(res)
        assertEquals(2, res.size)
        assertEquals("= a", res[1]["body"])
    }

    @Test
    fun `test findFunctionsAndDocstrings tricky string`() {
        val inputStream: InputStream? = ClassLoader.getSystemResourceAsStream("trickyStringTest.txt")
        val exampleTrickyString = inputStream?.bufferedReader(UTF_8).use { it?.readText() ?: "" }
        print(exampleTrickyString)
        val tokens = tokenizeKotlinCode(exampleTrickyString)
        val root = parseKotlinCode(tokens)

        val popularLiterals = PopularLiterals(
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val res = findFunctionsAndDocstrings(root, tokens, popularLiterals)
        println(res)
        assertEquals(1, res.size)
    }





}
