package org.tokenizer

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jetbrains.kotlin.spec.grammar.tools.KotlinParseTree
import org.jetbrains.kotlin.spec.grammar.tools.KotlinParserException
import org.jetbrains.kotlin.spec.grammar.tools.parseKotlinCode
import org.jetbrains.kotlin.spec.grammar.tools.tokenizeKotlinCode
import java.lang.IndexOutOfBoundsException
import kotlin.text.Charsets.UTF_8

fun createDirectory(name: String) {
    val directory = File(name)

    if (!directory.exists()) {
        val result = directory.mkdirs()
        if (result) {
            println("Directory created successfully: $name")
        } else {
            println("Failed to create directory: $name")
        }
    } else {
        println("Directory already exists: $name")
    }
}

fun main(args: Array<String>) {
    val options = Options()
    options.addOption("b", "base_dir", true, "Base directory")
    options.addOption("ot", "output_dir_token_completion", true, "Output directory for token completion")
    options.addOption("om", "output_dir_method_generation", true, "Output directory for method generation")
    options.addOption("f", "file_names", true, "File names with paths")
    options.addOption("rt", "result_file_token_completion", true, "Result file for token completion")
    options.addOption("rm", "result_file_method_generation", true, "Result file for method generation")
    options.addOption("l", "literal_file_path", true, "Literal file")
    options.addOption("t", "tokens_threshold_to_parse", true, "Max number of tokens to parse")

    val parser = DefaultParser()
    val cmd = try {
        parser.parse(options, args)
    } catch (e: ParseException) {
        println("Error parsing command line arguments: ${e.message}")
        return
    }

    val outputDirTokenCompletion = cmd.getOptionValue("output_dir_token_completion", "token_completion")
    val outputDirMethodGeneration = cmd.getOptionValue("output_dir_method_generation", "method_generation")
    val baseDir = cmd.getOptionValue("base_dir", "kotlin_data")
    val fileNameWithPaths = cmd.getOptionValue("file_names", "train_file_names.txt")
    val resultFileTokenCompletion = cmd.getOptionValue("result_file_token_completion", "train.txt")
    val resultFileMethodGeneration = cmd.getOptionValue("result_file_method_generation", "train.json")
    val literalFile = cmd.getOptionValue("literal_file", "literals.json")
    val tokensThresholdToParseStr = cmd.getOptionValue("literal_file", "10000")
    val threshold = tokensThresholdToParseStr.toInt()

    createDirectory(outputDirTokenCompletion)
    createDirectory(outputDirMethodGeneration)

    val jsonString = File(literalFile).readText(UTF_8)
    val popularLiterals = Json.decodeFromString<PopularLiterals>(jsonString)

    var fileCount = 0
    val resultFileTokenCompletionPath = "$outputDirTokenCompletion/$resultFileTokenCompletion"
    val resultMethodGenerationPath = "$outputDirMethodGeneration/$resultFileMethodGeneration"
    File(resultFileTokenCompletionPath).bufferedWriter(UTF_8).use { writer ->
        File(resultMethodGenerationPath).bufferedWriter(UTF_8).use { jsonWriter ->
            File("$baseDir/$fileNameWithPaths").readLines().forEach { path ->

                val fullPath = "$baseDir/$path"
                val fileContent = File(fullPath).readText(UTF_8)
                val tokens = tokenizeKotlinCode(fileContent)

                val resultString = processTokens(tokens, popularLiterals)

                if (resultString.isNotEmpty()) {
                    writer.write("<s> $resultString </s>\n")
                }

                if (resultString.isNotEmpty() && tokens.size < threshold) {
                    try {
                        val root = parseKotlinCode(tokens)
                        val listOfNodes = mutableListOf<KotlinParseTree>()
                        listFromNode(root, listOfNodes)
                        val functions = findFunctionsAndDocstrings(listOfNodes, tokens, popularLiterals)
                        functions.forEach { functionMap ->
                            jsonWriter.write(Json.encodeToString(functionMap) + "\n")
                        }
                    } catch (_: KotlinParserException) {
                    }
                      catch (_: IndexOutOfBoundsException) {
                    }
                }

                fileCount++
                if (fileCount % 100 == 0) {
                    println("Preprocessed $fileCount files")
                }
            }
        }
    }

    println("Finished preprocessing")
}
