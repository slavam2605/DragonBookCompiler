package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.Arm64AssemblyCompiler
import compiler.backend.arm64.Instruction
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Ldr
import compiler.backend.arm64.MemoryLocation
import compiler.backend.arm64.Register
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.StackLocation
import compiler.backend.arm64.StpMode
import compiler.backend.arm64.Str
import compiler.frontend.FrontendFunction
import compiler.ir.IRInt
import compiler.ir.IRFloat
import compiler.ir.IRType
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.printToString
import statistics.PerFunctionStatsData

interface MemoryAllocator<Reg : Register> {
    /**
     * Returns the size of allocated stack memory, aligned to 16 bytes.
     */
    val alignedAllocatedSize: Int

    /**
     * Returns the set of registered that were used at least once.
     * If [clazz] is specified, only registers of that class are returned.
     */
    fun <T : Register> usedRegisters(clazz: Class<T>? = null): Set<T>

    fun loc(v: IRVar): MemoryLocation

    fun <T> writeReg(v: IRVar, block: (Reg) -> T): T

    fun readReg(v: IRValue): RegHandle<Reg>

    fun <T> readReg(v: IRValue, block: (Reg) -> T): T {
        val handle = readReg(v)
        try {
            return block(handle.reg)
        } finally {
            handle.dispose()
        }
    }
}

/**
 * Handle for a register that should be disposed of after use
 * (usually a temporary register that should be returned to the pool).
 */
class RegHandle<Reg : Register>(val reg: Reg, val dispose: () -> Unit)

abstract class BaseMemoryAllocator<Reg : Register>(
    val compiler: Arm64AssemblyCompiler,
    val function: FrontendFunction<ControlFlowGraph>,
    val ops: MutableList<Instruction>,
    val type: IRType
) : MemoryAllocator<Reg> {
    private val map = HashMap<IRVar, MemoryLocation>()
    private val usedRegsHistory = mutableSetOf<Reg>()
    internal var nextStackOffset = 0

    abstract val freeTempRegs: MutableSet<Reg>

    abstract val nonTempRegs: Set<Reg>

    abstract fun parameterReg(index: Int): Reg

    fun init(minStackOffset: Int = 0) {
        nextStackOffset = minStackOffset
        val freeRegs = nonTempRegs.toMutableSet()

        val typeIndex = mutableMapOf<IRType, Int>()
        function.parameters.forEach { parameter ->
            val index = typeIndex.compute(parameter.type) { _, v -> (v ?: -1) + 1 }!!
            check(index < 8)
            val reg = parameterReg(index)
            map[parameter] = reg
            freeRegs.remove(reg)
            freeTempRegs.remove(reg)
        }

        val cfg = function.value
        val interferenceGraph = InterferenceGraph.create(cfg) { it.type == type }
        val coloring = GraphColoring(
            colors = freeRegs,
            initialColoring = map,
            graph = interferenceGraph,
            colorScore = RegScore,
            isExtraColor = { it is StackLocation }
        ) { index ->
            val stackOffset = minStackOffset + index * 8
            nextStackOffset = stackOffset + 8
            StackLocation(stackOffset)
        }

        val colorMapping = coloring.findColoring()
        colorMapping.forEach { (irVar, reg) ->
            map[irVar] = reg
            freeRegs.remove(reg)
            freeTempRegs.remove(reg)
            if (reg is Register) {
                @Suppress("UNCHECKED_CAST")
                usedRegsHistory.add(reg as Reg)
            }
        }

        StatAvailableRegisters(nonTempRegs.size, type).record(functionName = function.name)
        StatUsedRegisters(usedRegsHistory.size, type).record(functionName = function.name)
        StatSpilledRegisters(nextStackOffset / 8, type).record(functionName = function.name)
    }

    override val alignedAllocatedSize: Int get() = (nextStackOffset + 15) and (-16)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Register> usedRegisters(clazz: Class<T>?): Set<T> {
        if (clazz == null) return usedRegsHistory.toSet() as Set<T>
        return usedRegsHistory.filterTo(mutableSetOf()) {
            clazz.isInstance(it)
        } as Set<T>
    }


    override fun loc(v: IRVar): MemoryLocation {
        return map[v] ?: error("Unallocated variable ${v.printToString()}")
    }

    override fun readReg(v: IRValue): RegHandle<Reg> {
        val tempReg = when (v) {
            is IRVar -> {
                val loc = loc(v)
                if (loc is Register) {
                    @Suppress("UNCHECKED_CAST")
                    return RegHandle(loc as Reg) {}
                }

                check(loc is StackLocation)
                tempRegInternal().also {
                    ops.add(Ldr(it, SP, loc.spOffset(compiler.spShift), StpMode.SIGNED_OFFSET))
                }
            }
            is IRInt -> {
                tempRegInternal().also {
                    compiler.emitAssignConstantInt64(it as X, v.value)
                }
            }
            is IRFloat -> {
                tempRegInternal().also {
                    compiler.emitAssignConstantFloat64(it as D, v.value)
                }
            }
        }
        return RegHandle(tempReg) {
            free(tempReg)
        }
    }

    override fun <T> writeReg(v: IRVar, block: (Reg) -> T): T {
        return when (val loc = loc(v)) {
            is Register -> {
                @Suppress("UNCHECKED_CAST")
                block(loc as Reg)
            }
            is StackLocation -> tempRegInternal().let { tempReg ->
                block(tempReg).also { writeBack(tempReg, loc) }
            }
        }
    }

    fun <T> tempReg(block: (Reg) -> T): T {
        val tempReg = tempRegInternal()
        return block(tempReg).also { free(tempReg) }
    }

    private fun writeBack(reg: Reg, loc: StackLocation) {
        ops.add(Str(reg, SP, loc.spOffset(compiler.spShift), StpMode.SIGNED_OFFSET))
        free(reg)
    }

    private fun free(reg: Reg) = freeTempRegs.add(reg)

    private fun tempRegInternal(): Reg {
        val tempReg = freeTempRegs.firstOrNull() ?: error("No free registers for $type")
        freeTempRegs.remove(tempReg)
        usedRegsHistory.add(tempReg)
        return tempReg
    }

    companion object {
        val RegScore = { loc: MemoryLocation ->
            when (loc) {
                is X -> 0
                is D -> 0
                is StackLocation -> 1
                else -> error("Unsupported memory location: $loc")
            }
        }
    }

    class StatAvailableRegisters(val value: Int, type: IRType) : PerFunctionStatsData(type)
    class StatUsedRegisters(val value: Int, type: IRType) : PerFunctionStatsData(type)
    class StatSpilledRegisters(val value: Int, type: IRType) : PerFunctionStatsData(type)
}