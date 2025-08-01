plugins {
    kotlin("jvm") version "2.2.0"
    id("antlr")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-long-messages")
    
    // Copy generated lexer tokens to sources to fix the IDEA Antlr plugin issue
    doLast {
        val sourceFolder = layout.projectDirectory.dir("src/main/antlr")
        val outputFolder = layout.buildDirectory.dir("generated-src/antlr/main").get()
        val grammarFiles = sourceFolder.asFile.listFiles { file -> file.extension == "g4"}
        val usedLexers = grammarFiles.flatMap { file ->
            "tokenVocab *= *([a-zA-Z0-9_]+)".toRegex().findAll(file.readText()).map { it.groupValues[1] }
        }
        for (lexer in usedLexers) {
            copy {
                from(outputFolder.file("$lexer.tokens"))
                into(sourceFolder)
            }
        }
    }
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}