package ir

import MainGrammar
import MainLexer
import compiler.frontend.CompileToIRVisitor
import compiler.ir.IRProtoNode
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.print
import ir.interpreter.CFGInterpreter
import ir.interpreter.ProtoIRInterpreter
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.example.parser.UnderlineErrorListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.io.File
import kotlin.random.Random

abstract class CompileToIRTestBase {
    protected enum class TestMode {
        IR, CFG
    }

    private fun compileToIR(input: String): List<IRProtoNode> {
        val lexer = MainLexer(CharStreams.fromString(input))
        val parser = MainGrammar(CommonTokenStream(lexer)).apply {
            removeErrorListeners()
            addErrorListener(UnderlineErrorListener())
        }
        val tree = parser.program()
        return CompileToIRVisitor().compileToIR(tree)
    }

    protected fun compileAndRun(mode: TestMode, input: String): Map<IRVar, Long> {
        val ir = compileToIR(input)
        when (mode) {
            TestMode.IR -> {
                ir.print()
                return ProtoIRInterpreter(ir).eval()
            }
            TestMode.CFG -> {
                val cfg = ControlFlowGraph.build(ir)
                cfg.print()
                return CFGInterpreter(cfg).eval()
            }
        }
    }

    private fun Map<IRVar, Long>.getVariable(varName: String) =
        entries.singleOrNull { "x$varName[0-9]+\$".toRegex().matches(it.key.name) }?.value

    protected fun compileAndGet(mode: TestMode, input: String, varName: String): Long? =
        compileAndRun(mode, input).getVariable(varName)

    protected fun readWithPattern(file: File, vararg replacements: Pair<String, Any>) =
        file.readText().let {
            replacements.fold(it) { acc, (from, to) ->
                acc.replace($$"<$$$from>", to.toString())
            }
        }

    /**
     * Reads the file and runs an automated test.
     * Expected values should be written in the file itself in comments, e.g. `// expected: result == 10`
     */
    protected fun runTestFromFile(mode: TestMode, file: File): DynamicTest {
        return DynamicTest.dynamicTest(file.name) {
            val testProgram = file.readText()
            val expectedRegex = "// *expected: *([a-zA-Z0-9_]+) *== *([0-9]+)".toRegex()
            val assertions = testProgram.lines().mapNotNull { line ->
                expectedRegex.find(line)?.let {
                    val (varName, expectedValue) = it.destructured
                    varName to expectedValue.toLong()
                }
            }
            check(assertions.isNotEmpty()) { "No assertions found in file $file" }
            val result = compileAndRun(mode, testProgram)
            assertions.forEach { (varName, expectedValue) ->
                val actualValue = result.getVariable(varName)
                assertEquals(expectedValue, actualValue) {
                    "Expected $varName == $expectedValue, but got $actualValue"
                }
            }
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

    protected fun withParametersAndFiles(
        intRange: Iterable<Long>,
        resourceFolder: String,
        block: (TestMode, Long, File) -> DynamicNode
    ): List<DynamicContainer> = withFiles(resourceFolder) { mode, file ->
        val tests = intRange.map { n -> block(mode, n, file) }
        DynamicContainer.dynamicContainer(file.name, tests)
    }

    protected fun withFiles(resourceFolder: String, block: (TestMode, File) -> DynamicNode): List<DynamicContainer> {
        val resourceFiles = listResourceFiles(resourceFolder)
        return TestMode.entries.map { mode ->
            val nodes = resourceFiles.map { file -> block(mode, file) }
            DynamicContainer.dynamicContainer("$mode", nodes)
        }
    }
}