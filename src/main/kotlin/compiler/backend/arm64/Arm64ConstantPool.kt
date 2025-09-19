package compiler.backend.arm64

import compiler.utils.NameAllocator
import java.lang.Double.doubleToLongBits

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
            ops.add(CustomText(".quad 0x${bits.toString(16)}"))
        }
    }
}