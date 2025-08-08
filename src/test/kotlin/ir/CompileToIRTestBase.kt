package ir

import MainGrammar
import MainLexer
import compiler.frontend.CompileToIRVisitor
import compiler.frontend.SemanticAnalysisVisitor
import compiler.ir.IRProtoNode
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.SourceLocationMap
import compiler.ir.cfg.analysis.DefiniteAssignmentAnalysis
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.print
import compiler.ir.printToString
import ir.interpreter.CFGInterpreter
import ir.interpreter.ProtoIRInterpreter
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import parser.UnderlineErrorListener
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import java.io.File
import kotlin.random.Random
import kotlin.test.assertTrue

abstract class CompileToIRTestBase {
    protected enum class TestMode {
        IR, CFG, SSA
    }

    protected open val excludeModes: Set<TestMode> = emptySet()

    private fun compileToIR(input: String): Pair<List<IRProtoNode>, SourceLocationMap> {
        val lexer = MainLexer(CharStreams.fromString(input))
        val parser = MainGrammar(CommonTokenStream(lexer)).apply {
            removeErrorListeners()
            addErrorListener(UnderlineErrorListener())
        }
        val tree = parser.program()
        assertTrue("Test program has ${parser.numberOfSyntaxErrors} parser errors") {
            parser.numberOfSyntaxErrors == 0
        }
        SemanticAnalysisVisitor().analyze(tree)
        return CompileToIRVisitor().compileToIR(tree)
    }

    protected fun compileAndRun(mode: TestMode, input: String): Map<IRVar, Long> {
        val (ir, sourceMap) = compileToIR(input)
        when (mode) {
            TestMode.IR -> {
                ir.print()
                return ProtoIRInterpreter(ir).eval()
            }
            TestMode.CFG -> {
                val cfg = ControlFlowGraph.build(ir, sourceMap)
                cfg.print()
                DefiniteAssignmentAnalysis(cfg).run()
                return CFGInterpreter(cfg).eval()
            }
            TestMode.SSA -> {
                val cfg = ControlFlowGraph.build(ir, sourceMap)
                DefiniteAssignmentAnalysis(cfg).run()
                val ssa = SSAControlFlowGraph.transform(cfg)
                ssa.print()
                testSingleAssignmentsInSSA(ssa)
                return CFGInterpreter(ssa).eval()
            }
        }
    }

    protected fun Map<IRVar, Long>.getVariable(varName: String) =
        entries.singleOrNull { "x$varName[0-9]+\$".toRegex().matches(it.key.name) }?.value

    protected fun compileAndGet(mode: TestMode, input: String, varName: String): Long? =
        compileAndRun(mode, input).getVariable(varName)

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
        return TestMode.entries.filter { it !in excludeModes }.map { mode ->
            val nodes = resourceFiles.map { file -> block(mode, file) }
            DynamicContainer.dynamicContainer("$mode", nodes)
        }
    }

    companion object {
        private fun testSingleAssignmentsInSSA(ssa: SSAControlFlowGraph) {
            val seenLValues = mutableSetOf<IRVar>()
            ssa.blocks.forEach { (_, block) ->
                block.irNodes.forEach { node ->
                    node.lvalues().forEach { lvalue ->
                       assertTrue(seenLValues.add(lvalue),
                           "Variable ${lvalue.printToString()} is used more than once")
                    }
                }
            }
        }
    }
}