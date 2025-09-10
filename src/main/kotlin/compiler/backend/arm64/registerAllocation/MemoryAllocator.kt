package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.Arm64AssemblyCompiler
import compiler.backend.arm64.Instruction
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Ldr
import compiler.backend.arm64.MemoryLocation
import compiler.backend.arm64.StackLocation
import compiler.backend.arm64.StpMode
import compiler.backend.arm64.Str
import compiler.frontend.FrontendFunction
import compiler.ir.IRInt
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.printToString

class MemoryAllocator(
    val compiler: Arm64AssemblyCompiler,
    val function: FrontendFunction<ControlFlowGraph>,
    val ops: MutableList<Instruction>
) {
    private val map = HashMap<IRVar, MemoryLocation>()
    private val freeTempRegs = TempRegs.toMutableSet()
    private val usedRegsHistory = mutableSetOf<X>()
    internal var nextStackOffset = 0

    init {
        val freeRegs = NonTempRegs.toMutableSet()

        check(function.parameters.size <= 8)
        function.parameters.forEachIndexed { index, parameter ->
            map[parameter] = X(index)
            freeRegs.remove(X(index))
            freeTempRegs.remove(X(index))
        }

        val cfg = function.value
        val interferenceGraph = InterferenceGraph.create(cfg)
        val coloring = GraphColoring(freeRegs, map) { index ->
            val stackOffset = index * 8
            nextStackOffset = stackOffset + 8
            StackLocation(stackOffset)
        }.findColoring(interferenceGraph)

        coloring.forEach { (irVar, reg) ->
            map[irVar] = reg
            freeTempRegs.remove(reg)
            if (reg is X) {
                usedRegsHistory.add(reg)
            }
        }
    }

    /**
     * Returns the size of allocated stack memory, aligned to 16 bytes.
     */
    val alignedAllocatedSize: Int get() = (nextStackOffset + 15) and (-16)

    /**
     * Returns the set of registered that were used at least once.
     */
    val usedRegisters: Set<X> get() = usedRegsHistory.toSet()

    fun loc(v: IRVar): MemoryLocation {
        return map[v] ?: error("Unallocated variable ${v.printToString()}")
    }

    fun <T> readReg(v: IRValue, block: (X) -> T): T {
        val tempReg = when (v) {
            is IRVar -> {
                val loc = loc(v)
                if (loc is X) return block(loc)

                check(loc is StackLocation)
                tempRegInternal().also {
                    ops.add(Ldr(it, SP, loc.offset, StpMode.SIGNED_OFFSET))
                }
            }
            is IRInt -> {
                tempRegInternal().also {
                    compiler.emitAssignConstant64(it, v.value)
                }
            }
        }
        return block(tempReg).also { free(tempReg) }
    }

    fun <T> writeReg(v: IRVar, block: (X) -> T): T {
        return when (val loc = loc(v)) {
            is X -> block(loc)
            is StackLocation -> tempRegInternal().let { tempReg ->
                block(tempReg).also { writeBack(tempReg, loc) }
            }
            else -> error("Unsupported memory location: $loc")
        }
    }

    fun <T> tempReg(block: (X) -> T): T {
        val tempReg = tempRegInternal()
        return block(tempReg).also { free(tempReg) }
    }

    private fun writeBack(reg: X, loc: StackLocation) {
        ops.add(Str(reg, SP, loc.offset, StpMode.SIGNED_OFFSET))
        free(reg)
    }

    private fun free(reg: X) = freeTempRegs.add(reg)

    private fun tempRegInternal(): X {
        val tempReg = freeTempRegs.first()
        freeTempRegs.remove(tempReg)
        usedRegsHistory.add(tempReg)
        return tempReg
    }

    companion object {
        val TempRegs = X.CallerSaved.take(5).toSet()
        val NonTempRegs = X.CalleeSaved - TempRegs
    }
}