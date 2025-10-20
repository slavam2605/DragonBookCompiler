package ir

import TestResources
import backend.NativeArm64TestCompilationFlow
import compiler.backend.PrepareForNativeCompilation
import compiler.backend.arm64.registerAllocation.BaseMemoryAllocator.StatAvailableRegisters
import compiler.backend.arm64.registerAllocation.BaseMemoryAllocator.StatSpilledRegisters
import compiler.backend.arm64.registerAllocation.BaseMemoryAllocator.StatUsedRegisters
import compiler.ir.*
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import ir.TestCompilationFlow.compileToCFG
import ir.TestCompilationFlow.compileToIR
import ir.TestCompilationFlow.compileToOptimizedCFG
import ir.TestCompilationFlow.compileToOptimizedSSA
import ir.TestCompilationFlow.compileToSSA
import ir.interpreter.BaseInterpreter.Companion.IntReturnValue
import ir.interpreter.CFGInterpreter
import compiler.frontend.FrontendConstantValue
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
    protected open val nativeTestOptions: NativeTestOptions = NativeTestOptions()

    protected open fun handleNativeOutput(output: String) {}

    protected fun compileAndRun(mode: TestMode, input: String): Map<IRVar, FrontendConstantValue> {
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
                    if (PRINT_DEBUG_INFO) ffs.print { it.print() }
                }
                ffs.values.forEach { function ->
                    val optimizedSSA = function.value
                    checkStaticallyEvaluatedValues(optimizedSSA)
                }

                if (ignoreInterpretedValues) {
                    return emptyMap()
                }

                val optimizedFfs = ffs.map { it.value }
                return CFGInterpreter(MAIN, emptyList(), optimizedFfs, TestFunctionHandler).eval()
            }
            TestMode.OPTIMIZED_NON_SSA -> {
                check(!ignoreInterpretedValues) {
                    "Static value test for optimized non-SSA mode is not supported " +
                            "because it returns the same values as for optimized SSA mode"
                }

                val nonSsaFfs = compileToOptimizedCFG(input)
                if (PRINT_DEBUG_INFO) nonSsaFfs.print { it.print() }

                return CFGInterpreter(MAIN, emptyList(), nonSsaFfs, TestFunctionHandler).eval()
            }
            TestMode.NATIVE_ARM64 -> {
                val ffs = compileToOptimizedCFG(input).map {
                    PrepareForNativeCompilation.run(it.value, it.parameters)
                }
                val output = NativeArm64TestCompilationFlow.compileAndRun(ffs, nativeTestOptions)

                ffs.forEach { function ->
                    val name = function.name
                    val availableRegisters = StatsHolder.get<StatAvailableRegisters>(name, IRType.INT64).value
                    val usedRegisters = StatsHolder.get<StatUsedRegisters>(name, IRType.INT64).value
                    val spilledRegisters = StatsHolder.get<StatSpilledRegisters>(name, IRType.INT64).value

                    if (PRINT_DEBUG_INFO) {
                        println("Memory allocator stats for $name:")
                        println("\tUsed registers: $usedRegisters of $availableRegisters")
                        println("\tSpilled registers: $spilledRegisters")
                        println()
                    }
                }

                handleNativeOutput(output)
                return output.toLongOrNull()?.let { mapOf(IntReturnValue to FrontendConstantValue.IntValue(it)) } ?: emptyMap()
            }
        }
    }

    protected fun compileAndGetResult(mode: TestMode, input: String): FrontendConstantValue? =
        compileAndRun(mode, input)[IntReturnValue]

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
        block: (TestMode, Long, File) -> DynamicNode?
    ): List<DynamicContainer> = withFiles(resourceFolder) { mode, file ->
        val tests = intRange.mapNotNull { n -> block(mode, n, file) }
        DynamicContainer.dynamicContainer(file.name, tests)
    }

    protected fun withFiles(resourceFolder: String, block: (TestMode, File) -> DynamicNode): List<DynamicContainer> {
        val testNames = System.getProperty("testNames")?.split(",")?.toSet()
        val resourceFiles = listResourceFiles(resourceFolder).filter {
            if (testNames != null && it.name !in testNames) return@filter false
            it.extension !in ignoredExtensions
        }

        return TestMode.entries.filter { it !in excludeModes }.map { mode ->
            val nodes = resourceFiles.map { file -> block(mode, file) }
            DynamicContainer.dynamicContainer("$mode", nodes)
        }
    }

    protected fun resetExternalCallCount() {
        externalCallCount = 0
    }

    companion object {
        const val PRINT_DEBUG_INFO = false
        private const val MAIN = "test_main"
        private val ignoredExtensions = setOf("c", "cpp", "o")
        internal var externalCallCount: Int = 0

        @JvmStatic
        protected val TestFunctionHandler = handler@ { name: String, args: List<FrontendConstantValue> ->
            when (name) {
                "assertEquals", "assertFloatEquals",
                "assertStaticEquals", "assertStaticFloatEquals" -> {
                    assertEquals(args[1], args[0], "Wrong values in assertEquals")
                }
                "assertStaticUnknown" -> { /* ignore on runtime */ }
                "undef" -> return@handler args[0]
                "print_float", "println" -> {
                    println(args.joinToString())
                }
                else -> error("Unknown function: $name")
            }
            externalCallCount++
            FrontendConstantValue.IntValue(0) // default return value
        }

        private fun checkStaticallyEvaluatedValues(optimized: SSAControlFlowGraph) {
            // Statically check equality after optimization
            optimized.blocks.forEach { (_, block) ->
                block.irNodes.filterIsInstance<IRFunctionCall>().forEach { node ->
                    when (node.name) {
                        "assertStaticEquals", "assertStaticFloatEquals" -> {
                            check(node.arguments.size == 2)
                            val expected = node.arguments[1]
                            val actual = node.arguments[0]
                            when (expected) {
                                is IRInt -> {
                                    assertTrue(actual is IRInt, "Expected ${actual.printToString()} to be statically known")
                                    assertEquals(expected.value, actual.value, "assertStaticEquals failed")
                                }
                                is IRFloat -> {
                                    assertTrue(actual is IRFloat, "Expected ${actual.printToString()} to be statically known")
                                    assertEquals(expected.value, actual.value, "assertStaticEquals failed")
                                }
                                else -> {
                                    assertEquals(expected, actual, "assertStaticEquals failed")
                                }
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
        }
    }
}