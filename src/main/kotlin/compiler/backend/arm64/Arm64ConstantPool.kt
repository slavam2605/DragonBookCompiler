package compiler.backend.arm64

import compiler.backend.arm64.instructions.CustomText
import compiler.backend.arm64.instructions.Instruction
import compiler.backend.arm64.instructions.Label
import compiler.utils.NameAllocator
import statistics.StatsData
import java.lang.Double.doubleToLongBits
import java.lang.Double.longBitsToDouble
import java.lang.Long.toHexString

class Arm64ConstantPool {
    private val names = NameAllocator("Lconst")
    private val map64Bits = mutableMapOf<Long, String>()

    fun getConstant(double: Double): String {
        val bits = doubleToLongBits(double)
        return map64Bits.computeIfAbsent(bits) { names.newName() }
    }

    fun writeSection(ops: MutableList<Instruction>) {
        ops.add(CustomText(".section __TEXT,__const"))
        ops.add(CustomText(".p2align 3"))
        map64Bits.forEach { (bits, name) ->
            ops.add(Label(name))

            ops.add(CustomText(".quad 0x${toHexString(bits)} ; ${longBitsToDouble(bits)}"))
        }

        StatConstantPoolSize(map64Bits.size).record()
    }

    class StatConstantPoolSize(val size: Int) : StatsData()
}