package compiler.backend.arm64.registerAllocation

import compiler.ir.IRType

enum class Arm64StorageType {
    INT_REG, FLOAT_REG;

    companion object {
        fun of(type: IRType) = when (type) {
            IRType.INT64, is IRType.PTR -> INT_REG
            IRType.FLOAT64 -> FLOAT_REG
        }
    }
}
