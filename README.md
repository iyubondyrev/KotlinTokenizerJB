
# Kotlin Code Preprocessor for Language Model Tasks

This project includes Kotlin applications designed to preprocess raw Kotlin code and generate datasets suitable for language model tasks such as method generation and token completion. The output datasets and files from this program closely match the format used by the CodeXGLUE [method generation](https://github.com/microsoft/CodeXGLUE/tree/main/Code-Code/Method-Generation) and [token completion](https://github.com/microsoft/CodeXGLUE/tree/main/Code-Code/CodeCompletion-token) tasks.
The interface of the tools also follows the design used in the CodeXGLUE dataset.

## Getting Started

### Prerequisites

- Java JDK 17
- Kotlin 1.9.23

Ensure that Java JDK 17 is installed on your system. Kotlin will be managed through the build configuration.

### Installation

#### Option 1: download jars from the latest release

You can download jars from the latest release in this GitHub repository and run the tools like this:

```bash
java -jar GetPopularLiterals.jar ...
# or
java -jar Preprocess.jar ...
```

#### Option 2: build the project

To build the project yourself you will have to install [kotlin-grammar-tools](https://github.com/Kotlin/grammar-tools). After that:

```bash
git clone https://github.com/iyubondyrev/KotlinTokenizerJB.git
cd KotlinTokenizerJB
./gradlew build
```

This command compiles the Kotlin code, runs tests, and produces JAR files for the two main programs: `Preprocess` and `GetPopularLiterals`.



## Usage

### GetPopularLiterals

The `GetPopularLiterals` tool is designed to analyze Kotlin source files to identify and count the occurrences of string, character, and numerical literals. The results are summarized in a JSON file which lists the most frequently occurring literals.

The resulting .json file format is:

```json
{
  "str": [top 200 str literals here],
  "char": [top 30 char literals here],
  "num": [top 30 num literals here]
}
```

#### Features

- **Literals Extraction**: Processes Kotlin source files to extract string, character, and numeric literals.
- **Error Handling**: Identifies files that cannot be processed correctly and logs their paths for review.
- **Performance Reporting**: Outputs progress updates during the processing of large datasets.

#### Command-Line Parameters

- `--base_dir` (Required): Specifies the base directory where the Kotlin source files are located. Default is `kotlin_data`.
- `--file_names` (Required): Specifies the file that contains the list of paths to the Kotlin source files to be processed.
- `--literal_file` (Required): Designates the output file name and path where the JSON summary of popular literals will be saved. Default is `literals.json`.
- `--bad_file` (Required): Designates the output file name and path where the paths of files that failed processing will be saved. Default is `bad_files.txt`.

#### Usage Example

```bash
java -jar GetPopularLiterals.jar --base_dir="path/to/data" --file_names="file_list.txt" --literal_file="output_literals.json" --bad_file="failed_files.txt"
```



### Preprocess

The `Preprocess` tool is designed to process Kotlin source files for specific tasks like token completion and method generation, preparing datasets for training machine learning models. It reads source files, tokenizes the content, and then generates structured data for further use.

The resulting datasets files formats are:

* For method generation task (.jsonl file):

```json lines
{
  "signature":  "tokenized code with literal normalization here",
  "body":  "tokenized code with literal normalization here",
  "docstring": ""
}
```
Note, that the docstring field can contain empty strings

* For token completion (.txt file):

```text
<s> tokenized code with literal normalization here <\s>
```

The format of the token completion file is exactly the same as described here: [token completion](https://github.com/microsoft/CodeXGLUE/tree/main/Code-Code/CodeCompletion-token)

#### Features

- **Data Tokenization**: Tokenizes Kotlin code to facilitate detailed syntactic and semantic analysis.
- **Dataset Generation**: Generates distinct datasets for token completion and method generation tasks.
- **Error Management**: Handles errors gracefully, logging issues without stopping the batch processing.
- **Directory Management**: Ensures all necessary output directories are created and ready for use.

#### Command-Line Parameters

- `--base_dir` (Required): Specifies the base directory where the Kotlin source files are located. Default is `kotlin_data`.
- `--output_dir_token_completion` (Required): Directory to save the token completion dataset.
- `--output_dir_method_generation` (Required): Directory to save the method generation dataset.
- `--file_names` (Required): File containing paths to the data files to be processed.
- `--result_file_token_completion` (Required): Output file for the token completion results.
- `--result_file_method_generation` (Required): Output file for the method generation results.
- `--literal_file_path` (Required): Path to the file containing popular literals used for filtering and processing.
- `--tokens_threshold_to_parse` (Optional): Sets a maximum limit for the number of tokens to parse in each file, defaulting to 10000.

#### Usage Example

```bash
java -jar Preprocess.jar --base_dir="path/to/data" --output_dir_token_completion="token_completion" --output_dir_method_generation="method_generation" --file_names="list_of_files.txt" --result_file_token_completion="completion_results.txt" --result_file_method_generation="generation_results.json" --literal_file_path="popular_literals.json" --tokens_threshold_to_parse=5000
```

### Tests

For a more detailed understanding of these tools, you can review the tests located in `src/test/kotlin/`. These tests thoroughly cover the tools' capabilities, including how they handle corner cases and limitations.
### Example datasets

You can find example of datasets generated by these tools [here](https://huggingface.co/iyubondyrev/token_completion_kotlin/tree/main) and [here](https://huggingface.co/iyubondyrev/method_generation_kotlin/tree/main).


