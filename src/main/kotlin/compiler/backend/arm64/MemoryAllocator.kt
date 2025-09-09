package compiler.backend.arm64

import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.frontend.FrontendFunction
import compiler.ir.IRInt
import compiler.ir.IRValue
import compiler.ir.IRVar

class MemoryAllocator(
    val compiler: Arm64AssemblyCompiler,
    val function: FrontendFunction<*>,
    val ops: MutableList<Instruction>
) {
    private val map = HashMap<IRVar, MemoryLocation>()
    private val freeRegs = NonTempRegs.toMutableSet()
    private val freeTempRegs = TempRegs.toMutableSet()
    private val usedRegsHistory = mutableSetOf<X>()
    private var nextStackOffset = 0

    init {
        check(function.parameters.size <= 8)
        function.parameters.forEachIndexed { index, parameter ->
            map[parameter] = X(index)
            freeRegs.remove(X(index))
            freeTempRegs.remove(X(index))
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
        val loc = map.getOrPut(v) {
            if (freeRegs.isNotEmpty()) {
                freeRegs.first().also {
                    usedRegsHistory.add(it)
                    freeRegs.remove(it)
                }
            } else StackLocation(nextStackOffset).also { nextStackOffset += 8 }
        }
        return loc
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