package org.tokenizer

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

import java.io.File
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.spec.grammar.tools.tokenizeKotlinCode
import org.jetbrains.kotlin.spec.grammar.tools.KotlinTokensList
import kotlin.text.Charsets.UTF_8
import com.google.common.base.CharMatcher;



fun processTokens(tokens: KotlinTokensList, popularLiterals: PopularLiterals): String {
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
    var resultString = processedTokens.joinToString(" ")
    resultString = "<s> $resultString </s>\n"
    return resultString

}


fun main(args: Array<String>) {
    val options = Options()
    options.addOption("b", "base_dir", true, "Base directory")
    options.addOption("o", "output_dir", true, "Output directory")
    options.addOption("f", "file_names", true, "File names with paths")
    options.addOption("r", "result_file", true, "Result file")
    options.addOption("l", "literal_file_path", true, "Literal file")

    val parser = DefaultParser()
    val cmd = try {
        parser.parse(options, args)
    } catch (e: ParseException) {
        println("Error parsing command line arguments: ${e.message}")
        return
    }

    val outputDir = cmd.getOptionValue("output_dir", "token_completion")
    val baseDir = cmd.getOptionValue("base_dir", "kotlin_data")
    val fileNameWithPaths = cmd.getOptionValue("file_names", "train_file_names.txt")
    val resultFile = cmd.getOptionValue("result_file", "train.txt")
    val literalFile = cmd.getOptionValue("literal_file", "literals.json")

    val directory = File(outputDir)

    if (!directory.exists()) {
        val result = directory.mkdirs()
        if (result) {
            println("Directory created successfully: $outputDir")
        } else {
            println("Failed to create directory: $outputDir")
        }
    } else {
        println("Directory already exists: $outputDir")
    }


    val jsonString = File(literalFile).readText(UTF_8)
    val popularLiterals = Json.decodeFromString<PopularLiterals>(jsonString)

    var fileCount = 0
    val resultFilePath = "$outputDir/$resultFile"
    File(resultFilePath).bufferedWriter(UTF_8).use { writer ->
        File("$baseDir/$fileNameWithPaths").readLines().forEach { path ->

            val fullPath = "$baseDir/$path"
            val fileContent = File(fullPath).readText(UTF_8)
            val tokens = tokenizeKotlinCode(fileContent)

            val resultString = processTokens(tokens, popularLiterals)

            if (resultString.isNotEmpty()) {
                writer.write(resultString)
            }
            fileCount++
            if (fileCount % 1000 == 0) {
                println("Preprocessed $fileCount files")
            }
        }
    }

    println("Finished preprocessing")
}
