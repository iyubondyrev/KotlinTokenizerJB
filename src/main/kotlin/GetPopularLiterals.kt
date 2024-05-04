package org.tokenizer

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.IndexOutOfBoundsException
import org.jetbrains.kotlin.spec.grammar.tools.tokenizeKotlinCode
import org.jetbrains.kotlin.spec.grammar.tools.KotlinLexerException
import org.jetbrains.kotlin.spec.grammar.tools.KotlinTokensList
import kotlin.text.Charsets.UTF_8

fun getFullPath(directory: String, fileName: String): String {
    if (directory == "") {
        return fileName
    }
    return "$directory/$fileName"
}

fun updatePopularLiterals(tokens: KotlinTokensList, stringLiterals: MutableMap<String, Int>,
                          charLiterals: MutableMap<String, Int>, numLiterals: MutableMap<String, Int>) {
    var i = 0;

    while (i < tokens.size) {
        when (tokens[i].type) {
            "QUOTE_OPEN", "TRIPLE_QUOTE_OPEN" -> {
                val (stringLiteral, newI) = processString(tokens, i)
                // python and java literals.json do not have an empty string. so we won't
                if (stringLiteral.isNotEmpty()) {
                    stringLiterals[stringLiteral] = stringLiterals.getOrDefault(stringLiteral, 0) + 1
                }
                i = newI // points to the last quote
            }
            in literalNames -> {
                if (tokens[i].type == "NullLiteral" || tokens[i].type == "BooleanLiteral") {
                    i++
                    continue
                }
                if (tokens[i].type == "CharacterLiteral") {
                    val literal = processChar(tokens[i])
                    charLiterals[literal] = charLiterals.getOrDefault(literal, 0) + 1
                } else {
                    val literal = tokens[i].text
                    numLiterals[literal] = numLiterals.getOrDefault(literal, 0) + 1
                }
            }
        }
        i++
    }

}

fun main(args: Array<String>) {
    val options = Options()
    options.addOption("b", "base_dir", true, "Base directory")
    options.addOption("f", "file_names", true, "File with paths for the data files")
    options.addOption("bf", "bad_file", true, "File in which paths of the bad files will be written")
    options.addOption("l", "literal_file", true, "Result file with popular literals")

    val parser = DefaultParser()
    val cmd = try {
        parser.parse(options, args)
    } catch (e: ParseException) {
        println("Error parsing command line arguments: ${e.message}")
        return
    }

    val baseDir = cmd.getOptionValue("base_dir", "kotlin_data")
    val fileNameWithPaths = cmd.getOptionValue("file_names", "train_file_names.txt")
    val literalFile = cmd.getOptionValue("literal_file", "literals.json")
    val badFile = cmd.getOptionValue("bad_file", "bad_files.txt")


    val outputLiteralsPath = getFullPath(baseDir, literalFile)
    val outputBadFilesPath = getFullPath(baseDir, badFile)


    val stringLiterals = mutableMapOf<String, Int>()
    val charLiterals = mutableMapOf<String, Int>()
    val numLiterals = mutableMapOf<String, Int>()
    val badFiles = mutableListOf<String>()

    var fileCount = 0

    println("Getting popular literals");

    File(getFullPath(baseDir, fileNameWithPaths)).readLines().forEach { path ->
        val fullPath = getFullPath(baseDir, path)
        try {
            val fileContent = File(fullPath).readText(UTF_8)
            val tokens = tokenizeKotlinCode(fileContent)
            updatePopularLiterals(tokens, stringLiterals, charLiterals, numLiterals)
        } catch (e: KotlinLexerException) {
            badFiles.add(fullPath)
        } catch (e: IndexOutOfBoundsException) {
            badFiles.add(fullPath)
        }
        fileCount++
        if (fileCount % 1000 == 0) {
            println("Done with $fileCount files")
        }
    }

    val popularLiterals = PopularLiterals(
        str = stringLiterals.entries.sortedByDescending { it.value }.take(200).map { it.key }.toMutableList(),
        char = charLiterals.entries.sortedByDescending { it.value }.take(30).map { it.key }.toMutableList(),
        num = numLiterals.entries.sortedByDescending { it.value }.take(30).map { it.key }.toMutableList()
    )

    val jsonString = Json.encodeToString(popularLiterals)
    File(outputLiteralsPath).writeText(jsonString, UTF_8)

    File(outputBadFilesPath).writeText(badFiles.joinToString("\n"), UTF_8)
    badFiles.forEach { path ->
        val fileToDelete = File(path)
        if (fileToDelete.exists()) {
            fileToDelete.delete()
        }
    }

    println("Finished with ${badFiles.size} failures. Bad files deleted.")
}
