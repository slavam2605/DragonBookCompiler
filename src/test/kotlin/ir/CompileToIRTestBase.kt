package ir

import TestResources
import backend.NativeArm64TestCompilationFlow
import compiler.backend.arm64.registerAllocation.MemoryAllocator
import compiler.ir.IRFunctionCall
import compiler.ir.IRInt
import compiler.ir.IRVar
import compiler.ir.cfg.ssa.ConvertFromSSA
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.print
import compiler.ir.printToString
import ir.TestCompilationFlow.compileToCFG
import ir.TestCompilationFlow.compileToIR
import ir.TestCompilationFlow.compileToOptimizedSSA
import ir.TestCompilationFlow.compileToSSA
import ir.interpreter.BaseInterpreter.Companion.ReturnValue
import ir.interpreter.CFGInterpreter
import ir.interpreter.ProtoIRInterpreter
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import statistics.StatsHolder
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class CompileToIRTestBase {
    protected enum class TestMode {
        IR, CFG, SSA, OPTIMIZED_SSA, OPTIMIZED_NON_SSA, NATIVE_ARM64
    }

    protected open val excludeModes: Set<TestMode> = emptySet()
    protected open val ignoreInterpretedValues: Boolean = false

    protected fun compileAndRun(mode: TestMode, input: String): Map<IRVar, Long> {
        StatsHolder.clear()
        when (mode) {
            TestMode.IR -> {
                val (ffs, _) = compileToIR(input).also { (ffs, _) ->
                    if (PRINT_DEBUG_INFO) ffs.print { it.print() }
                }
                return ProtoIRInterpreter(MAIN, emptyList(), ffs, TestFunctionHandler).eval()
            }
            TestMode.CFG -> {
                val ffs = compileToCFG(input).also { ffs ->
                    if (PRINT_DEBUG_INFO) ffs.print { it.print() }
                }
                return CFGInterpreter(MAIN, emptyList(), ffs, TestFunctionHandler).eval()
            }
            TestMode.SSA -> {
                val ffs = compileToSSA(input).also { ffs ->
                    if (PRINT_DEBUG_INFO) ffs.print { it.print() }
                }
                return CFGInterpreter(MAIN, emptyList(), ffs, TestFunctionHandler).eval()
            }
            TestMode.OPTIMIZED_SSA -> {
                val ffs = compileToOptimizedSSA(input).also { ffs ->
                    if (PRINT_DEBUG_INFO) ffs.print { (_, ssa) -> ssa.print() }
                }
                ffs.values.forEach { function ->
                    val (unoptimizedSSA, optimizedSSA, cpValues, _) = function.value
                    checkStaticallyEvaluatedValues(unoptimizedSSA, optimizedSSA, cpValues)
                }

                val mainFunction = ffs["test_main"]!!.value
                if (ignoreInterpretedValues) {
                    return mainFunction.cpValues
                }

                val optimizedFfs = ffs.map { it.value.optimizedSSA }
                return CFGInterpreter(MAIN, emptyList(), optimizedFfs, TestFunctionHandler).eval()
                    .withValues(mainFunction.cpValues, mainFunction.equalities)
            }
            TestMode.OPTIMIZED_NON_SSA -> {
                check(!ignoreInterpretedValues) {
                    "Static value test for optimized non-SSA mode is not supported " +
                            "because it returns the same values as for optimized SSA mode"
                }

                val ffs = compileToOptimizedSSA(input)
                val nonSsaFfs = ffs.map { ConvertFromSSA(it.value.optimizedSSA).run() }
                if (PRINT_DEBUG_INFO) nonSsaFfs.print { it.print() }

                val mainFunction = ffs["test_main"]!!.value
                return CFGInterpreter(MAIN, emptyList(), nonSsaFfs, TestFunctionHandler).eval()
                    .withValues(mainFunction.cpValues, mainFunction.equalities)
            }
            TestMode.NATIVE_ARM64 -> {
                val ffs = compileToOptimizedSSA(input).map { ConvertFromSSA(it.value.optimizedSSA).run() }
                val output = NativeArm64TestCompilationFlow.compileAndRun(ffs)

                ffs.forEach { function ->
                    val name = function.name
                    val availableRegisters = StatsHolder.get<MemoryAllocator.StatAvailableRegisters>(name).value
                    val usedRegisters = StatsHolder.get<MemoryAllocator.StatUsedRegisters>(name).value
                    val spilledRegisters = StatsHolder.get<MemoryAllocator.StatSpilledRegisters>(name).value

                    if (PRINT_DEBUG_INFO) {
                        println("Memory allocator stats for $name:")
                        println("\tUsed registers: $usedRegisters of $availableRegisters")
                        println("\tSpilled registers: $spilledRegisters")
                        println()
                    }
                }

                return mapOf(ReturnValue to output.toLong())
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
            check(irVar !in result || result[irVar] == result[otherVar])
            result[irVar] = result[otherVar] ?: return@forEach
        }
        return result.toMap()
    }

    protected fun Map<IRVar, Long>.getVariable(varName: String) =
        entries.singleOrNull { "x${varName}_[0-9]+\$".toRegex().matches(it.key.name) }?.value

    protected fun compileAndGetResult(mode: TestMode, input: String): Long? =
        compileAndRun(mode, input)[ReturnValue]

    protected fun readWithPattern(file: File, vararg replacements: Pair<String, Any>) =
        file.readText().let {
            replacements.fold(it) { acc, (from, to) ->
                acc.replace($$"<$$$from>", to.toString())
            }
        }

    private fun listResourceFiles(path: String): List<File> {
        return TestResources.getFile(path).walk().toList().filter { it.isFile }
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
        const val PRINT_DEBUG_INFO = true
        private const val MAIN = "test_main"

        @JvmStatic
        protected val TestFunctionHandler = handler@ { name: String, args: List<Long> ->
            when (name) {
                "assertEquals", "assertStaticEquals" -> {
                    assertEquals(args[1], args[0], "Wrong values in assertEquals")
                }
                "assertStaticUnknown" -> { /* ignore on runtime */ }
                "undef" -> return@handler args[0]
                else -> error("Unknown function: $name")
            }
            0L // default return value
        }

        private fun checkStaticallyEvaluatedValues(
            unoptimized: SSAControlFlowGraph,
            optimized: SSAControlFlowGraph,
            cpValues: Map<IRVar, Long>
        ) {
            // Statically check equality after optimization
            optimized.blocks.forEach { (_, block) ->
                block.irNodes.filterIsInstance<IRFunctionCall>().forEach { node ->
                    when (node.name) {
                        "assertStaticEquals" -> {
                            check(node.arguments.size == 2)
                            val expected = node.arguments[1]
                            val actual = node.arguments[0]
                            if (expected is IRInt) {
                                assertTrue(actual is IRInt, "Expected ${actual.printToString()} to be statically known")
                                assertEquals(expected.value, actual.value, "assertStaticEquals failed")
                            } else {
                                assertEquals(expected, actual, "assertStaticEquals failed")
                            }
                        }
                        "assertStaticUnknown" -> {
                            check(node.arguments.size == 1)
                            val actual = node.arguments[0]
                            assertTrue(actual is IRVar, "Expected ${actual.printToString()} to be unknown")
                        }
                        "assertStaticUnreachable" -> {
                            check(node.arguments.isEmpty())
                            error("assertStaticUnreachable was not removed during optimization")
                        }
                    }
                }
            }

            // TODO fix
//            val expected = CFGInterpreter(
//                cfg = unoptimized,
//                exitAfterMaxSteps = true,
//                functionHandler = { _, _ -> 0L /* ignore assertions, they are checked statically */ }
//            ).eval()
//            (cpValues.keys.intersect(expected.keys)).forEach {
//                assertEquals(expected[it], cpValues[it],
//                    "Expected ${it.printToString()} to be ${expected[it]}, but was ${cpValues[it]}")
//            }
        }
    }
}