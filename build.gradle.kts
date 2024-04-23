plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
}


repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlin.spec.grammar.tools:kotlin-grammar-tools:0.1")
    implementation("commons-cli:commons-cli:1.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    implementation("com.google.guava:guava:30.1-jre")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Creating a Jar for GetPopularLiterals
val getPopularLiteralsJar by tasks.creating(Jar::class) {
    dependsOn("test")
    archiveBaseName.set("GetPopularLiterals")
    manifest {
        attributes("Main-Class" to "org.tokenizer.GetPopularLiteralsKt")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Creating a Jar for Preprocess
val tokenizeJar by tasks.creating(Jar::class) {
    dependsOn("test")
    archiveBaseName.set("Tokenize")
    manifest {
        attributes("Main-Class" to "org.tokenizer.TokenizeKt")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
