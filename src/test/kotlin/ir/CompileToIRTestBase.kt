package ir

import MainGrammar
import MainLexer
import compiler.frontend.CompileToIRVisitor
import compiler.ir.IRVar
import compiler.ir.printToString
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.example.parser.UnderlineErrorListener
import java.io.File
import kotlin.random.Random

abstract class CompileToIRTestBase {
    protected fun compileAndRun(input: String): Map<IRVar, Long> {
        val lexer = MainLexer(CharStreams.fromString(input))
        val parser = MainGrammar(CommonTokenStream(lexer)).apply {
            removeErrorListeners()
            addErrorListener(UnderlineErrorListener())
        }
        val tree = parser.program()
        val ir = CompileToIRVisitor().compileToIR(tree)
        ir.forEach { println(it.printToString()) }
        return ProtoIRInterpreter(ir).eval()
    }

    protected fun compileAndGet(input: String, varName: String): Long? =
        compileAndRun(input).entries
            .singleOrNull { "x$varName[0-9]+\$".toRegex().matches(it.key.name) }?.value

    protected fun readWithPattern(file: File, vararg replacements: Pair<String, Any>) =
        file.readText().let {
            replacements.fold(it) { acc, (from, to) ->
                acc.replace($$"<$$$from>", to.toString())
            }
        }

    private fun listResourceFiles(path: String): List<File> {
        val url = javaClass.getResource(path) ?: error("Resource not found: $path")
        return when (url.protocol) {
            "file" -> {
                File(url.toURI()).listFiles()?.toList() ?: emptyList()
            }
            else -> error("Unsupported protocol: ${url.protocol}")
        }
    }

    protected fun generateRandomParameters(n: Int, range: LongRange, seed: Long = 271987239827L): List<Long> {
        val fixedRandom = Random(seed)
        return generateSequence { fixedRandom.nextLong(range.first, range.last) }
            .distinct()
            .take(n)
            .sorted()
            .toList()
    }

    protected fun <T> withParametersAndFiles(intRange: Iterable<Long>, resourceFolder: String, block: (Long, File) -> T): List<T> {
        val resourceFiles = listResourceFiles(resourceFolder)
        return intRange.flatMap { n -> resourceFiles.map { n to it } }.map { (n, p) -> block(n, p) }
    }

    protected fun <T> withFiles(resourceFolder: String, block: (File) -> T): List<T> {
        return listResourceFiles(resourceFolder).map(block)
    }
}