package ir

import compiler.ir.IRVar
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.print
import compiler.ir.printToString
import ir.TestCompilationFlow.compileToCFG
import ir.TestCompilationFlow.compileToIR
import ir.TestCompilationFlow.compileToOptimizedSSA
import ir.TestCompilationFlow.compileToSSA
import ir.interpreter.CFGInterpreter
import ir.interpreter.ProtoIRInterpreter
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class CompileToIRTestBase {
    protected enum class TestMode {
        IR, CFG, SSA, OPTIMIZED_SSA
    }

    protected open val excludeModes: Set<TestMode> = emptySet()
    protected open val ignoreInterpretedValues: Boolean = false

    protected fun compileAndRun(mode: TestMode, input: String): Map<IRVar, Long> {
        when (mode) {
            TestMode.IR -> {
                val (ir, _) = compileToIR(input).also { (ir, _) ->
                    if (PRINT_DEBUG_INFO) ir.print()
                }
                return ProtoIRInterpreter(ir).eval()
            }
            TestMode.CFG -> {
                val cfg = compileToCFG(input).also {
                    if (PRINT_DEBUG_INFO) it.print()
                }
                return CFGInterpreter(cfg).eval()
            }
            TestMode.SSA -> {
                val ssa = compileToSSA(input).also {
                    if (PRINT_DEBUG_INFO) it.print()
                }
                return CFGInterpreter(ssa).eval()
            }
            TestMode.OPTIMIZED_SSA -> {
                val (unoptimizedSSA, optimizedSSA, cpValues, equalities) = compileToOptimizedSSA(input)
                    .also { (_, ssa) -> if (PRINT_DEBUG_INFO) ssa.print() }

                checkStaticallyEvaluatedValues(unoptimizedSSA, cpValues)
                if (ignoreInterpretedValues) {
                    return cpValues
                }

                return CFGInterpreter(optimizedSSA).eval().withValues(cpValues, equalities)
            }
        }
    }

    private fun Map<IRVar, Long>.withValues(extraValues: Map<IRVar, Long>, equalities: Map<IRVar, IRVar>): Map<IRVar, Long> {
        val result = toMutableMap()
        extraValues.forEach { (irVar, value) ->
            assertTrue(irVar !in this, "Constant propagation didn't remove variable $irVar with value $value")
            result[irVar] = value
        }
        equalities.forEach { (irVar, otherVar) ->
            check(irVar !in result)
            result[irVar] = result[otherVar] ?: return@forEach
        }
        return result.toMap()
    }

    protected fun Map<IRVar, Long>.getVariable(varName: String) =
        entries.singleOrNull { "x${varName}_[0-9]+\$".toRegex().matches(it.key.name) }?.value

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
                File(url.toURI()).walk().toList().filter { it.isFile }
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
        val testNames = System.getProperty("testNames")?.split(",")?.toSet()
        val resourceFiles = listResourceFiles(resourceFolder).let { files ->
            if (testNames == null) files
            else files.filter { it.name in testNames }
        }

        return TestMode.entries.filter { it !in excludeModes }.map { mode ->
            val nodes = resourceFiles.map { file -> block(mode, file) }
            DynamicContainer.dynamicContainer("$mode", nodes)
        }
    }

    companion object {
        const val PRINT_DEBUG_INFO = false

        private fun checkStaticallyEvaluatedValues(
            unoptimized: SSAControlFlowGraph,
            cpValues: Map<IRVar, Long>
        ) {
            val expected = CFGInterpreter(unoptimized, simulateUndef = true, exitAfterMaxSteps = true).eval()
            (cpValues.keys.intersect(expected.keys)).forEach {
                assertEquals(expected[it], cpValues[it],
                    "Expected ${it.printToString()} to be ${expected[it]}, but was ${cpValues[it]}")
            }
        }
    }
}