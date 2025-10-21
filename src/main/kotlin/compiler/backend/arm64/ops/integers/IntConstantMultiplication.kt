package compiler.backend.arm64.ops.integers

import compiler.backend.arm64.instructions.Add
import compiler.backend.arm64.instructions.Instruction
import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.instructions.LslImm
import compiler.backend.arm64.instructions.Mov
import compiler.backend.arm64.instructions.Mul
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.instructions.ShiftKind
import compiler.backend.arm64.instructions.Sub
import compiler.backend.arm64.ops.utils.NumberUtils

object IntConstantMultiplication {
    fun emitMultiply(context: NativeCompilerContext, dst: IntRegister.X, left: IntRegister.X, right: Long) {
        if (!tryEmitMultiply(context, dst, left, right)) {
            fallbackMultiply(context, dst, left, right)
        }
    }

    fun tryEmitMultiply(context: NativeCompilerContext, dst: IntRegister.X, left: IntRegister.X, right: Long): Boolean {
        val groups = splitToGroups(right).toMutableList()
        val bits = longToBitArray(right)
        val bitIndices = bits.indices.filter { bits[it] == 1 }
        check(groups.isNotEmpty())

        if (right.countOneBits() == 1) {
            context.ops.add(LslImm(dst, left, right.countTrailingZeroBits()))
            return true
        }

        if (right.countOneBits() == 2) {
            val lower = right.countTrailingZeroBits()
            val higher = 63 - right.countLeadingZeroBits()
            check(lower == bitIndices[0] && higher == bitIndices[1])

            val diff = higher - lower
            context.ops.add(Add(dst, left, left, ShiftKind.LSL, diff))
            if (lower > 0) {
                context.ops.add(LslImm(dst, dst, lower))
            }
            return true
        }

        (groups.singleOrNull() as? BitGroup.ManyOnes)?.let { group ->
            if (group.holes.isNotEmpty()) return@let

            val countBits = group.toIndex - group.fromIndex + 1
            context.allocator.tempIntReg { temp ->
                val tReg = if (dst == left) temp else dst
                if (countBits < 64) {
                    context.ops.add(LslImm(tReg, left, countBits))
                    context.ops.add(Sub(dst, tReg, left))
                    if (group.fromIndex > 0) {
                        context.ops.add(LslImm(dst, dst, group.fromIndex))
                    }
                } else {
                    context.ops.add(Sub(dst, IntRegister.Xzr, left))
                    if (group.fromIndex > 0) {
                        context.ops.add(LslImm(dst, dst, group.fromIndex))
                    }
                }
            }
            return true
        }

        if (right.countOneBits() == 3) {
            context.allocator.tempIntReg { temp ->
                val tReg = if (dst == left) temp else dst
                context.ops.add(Add(tReg, left, left, ShiftKind.LSL, bitIndices[2] - bitIndices[1]))
                context.ops.add(Add(dst, left, tReg, ShiftKind.LSL, bitIndices[1] - bitIndices[0]))
                if (bitIndices[0] > 0) {
                    context.ops.add(LslImm(dst, dst, bitIndices[0]))
                }
            }
            return true
        }

        // TODO revisit this implementation and enable
        // return tryEmitAnyGroups(context, groups, dst, left)
        return false
    }

    private fun tryEmitAnyGroups(
        context: NativeCompilerContext,
        groups: List<BitGroup>,
        dst: IntRegister.X,
        left: IntRegister.X
    ): Boolean {
        val groups = groups.toMutableList()
        val ops = mutableListOf<Instruction>()

        context.allocator.tempIntReg { temp ->
            val globalShift = groups.minOf {
                (it as? BitGroup.One)?.index ?: (it as BitGroup.ManyOnes).fromIndex
            }
            groups.indexOf(BitGroup.One(globalShift)).let { oneIndex ->
                if (oneIndex < 0) return@let
                // Move the lowest One to the end of the list
                groups.add(groups.removeAt(oneIndex))
            }

            groups.forEachIndexed { groupIndex, group ->
                val isFirstGroup = groupIndex == 0
                val isLastGroup = groupIndex == groups.lastIndex
                val lastDstReg = if (dst == left) temp else dst // dstReg from `groupIndex - 1`
                val dstReg = if (dst == left && !isLastGroup) temp else dst

                when (group) {
                    is BitGroup.One -> {
                        when {
                            isFirstGroup -> {
                                check(!isLastGroup)
                                ops.add(LslImm(dstReg, left, group.index - globalShift))
                            }
                            else -> ops.add(Add(dstReg, lastDstReg, left, ShiftKind.LSL, group.index - globalShift))
                        }
                    }
                    is BitGroup.ManyOnes -> {
                        if (isFirstGroup) {
                            if (group.toIndex - globalShift < 63) {
                                ops.add(LslImm(lastDstReg, left, group.toIndex - globalShift + 1))
                            } else {
                                ops.add(Mov(lastDstReg, IntRegister.Xzr))
                            }
                        } else {
                            if (group.toIndex - globalShift < 63) {
                                ops.add(Add(lastDstReg, lastDstReg, left, ShiftKind.LSL, group.toIndex - globalShift + 1))
                            }
                        }
                        val subReg = if (group.holes.isNotEmpty()) lastDstReg else dstReg
                        ops.add(Sub(subReg, lastDstReg, left, ShiftKind.LSL, group.fromIndex - globalShift))
                        group.holes.forEachIndexed { holeInArrayIndex, holeIndex ->
                            val subHoleReg = if (holeInArrayIndex == group.holes.lastIndex) dstReg else lastDstReg
                            ops.add(Sub(subHoleReg, lastDstReg, left, ShiftKind.LSL, holeIndex - globalShift))
                        }
                    }
                }
            }
            if (globalShift > 0) {
                ops.add(LslImm(dst, dst, globalShift))
            }
        }

        if (ops.size <= 4) {
            context.ops.addAll(ops)
            return true
        }
        return false
    }

    private fun splitToGroups(n: Long): List<BitGroup> {
        val bits = longToBitArray(n)
        val groups = mutableListOf<BitGroup>()

        var fromIndex = -1
        var toIndex = -1
        val holes = mutableListOf<Int>()
        for (index in 0 until 64) {
            if (bits[index] == 0) {
                if (fromIndex >= 0) {
                    if (index == 63 || bits[index + 1] == 0) {
                        // Close group
                        groups.addAll(BitGroup.create(fromIndex, toIndex, holes.toList()))
                        fromIndex = -1
                        toIndex = -1
                        holes.clear()
                    } else {
                        toIndex = index
                        holes.add(index)
                    }
                } else {
                    // Skip zero
                }
            } else {
                if (fromIndex < 0) {
                    fromIndex = index
                }
                toIndex = index
            }
        }
        if (fromIndex >= 0) {
            groups.addAll(BitGroup.create(fromIndex, toIndex, holes.toList()))
        }
        if (groups.any { it is BitGroup.One && it.index == 0 }) {
            val zero = groups.find { it is BitGroup.One && it.index == 0 }!!
            groups.remove(zero)
            groups.add(zero)
        }
        return groups
    }

    private fun longToBitArray(n: Long): IntArray {
        return IntArray(64) { i ->
            ((n shr i) and 1L).toInt()
        }
    }

    private fun fallbackMultiply(context: NativeCompilerContext, dst: IntRegister.X, left: IntRegister.X, right: Long) {
        if (dst == left) {
            context.allocator.tempIntReg { temp ->
                NumberUtils.emitAssignConstantInt64(context, temp, right)
                context.ops.add(Mul(dst, left, temp))
            }
        } else {
            NumberUtils.emitAssignConstantInt64(context, dst, right)
            context.ops.add(Mul(dst, left, dst))
        }
    }

    private sealed class BitGroup {
        data class One(val index: Int) : BitGroup()
        data class ManyOnes(val fromIndex: Int, val toIndex: Int, val holes: List<Int>) : BitGroup()

        companion object {
            fun create(fromIndex: Int, toIndex: Int, holes: List<Int>): List<BitGroup> {
                if (holes.isNotEmpty()) check(toIndex - fromIndex >= 2)
                return when {
                    fromIndex == toIndex -> listOf(One(fromIndex))
                    toIndex - fromIndex == 1 -> listOf(One(fromIndex), One(toIndex))
                    else -> listOf(ManyOnes(fromIndex, toIndex, holes))
                }
            }
        }
    }
}