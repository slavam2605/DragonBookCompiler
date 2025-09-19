package compiler.backend.arm64

import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.registerAllocation.BaseMemoryAllocator
import compiler.backend.arm64.registerAllocation.FloatMemoryAllocator
import compiler.backend.arm64.registerAllocation.IntMemoryAllocator
import compiler.frontend.FrontendFunction
import compiler.ir.*
import compiler.ir.cfg.ControlFlowGraph

class Arm64AssemblyCompiler(
    private val function: FrontendFunction<ControlFlowGraph>,
    private val constPool: Arm64ConstantPool,
    private val ops: MutableList<Instruction>
) {
    private val intAllocator = IntMemoryAllocator(this, function, ops)
    private val floatAllocator = FloatMemoryAllocator(this, function, ops)
    private val returnLabel = Label(".L_${function.name}_return")
    private val orderedBlocks = mutableListOf<IRLabel>()
    private var currentBlockIndex = 0

    init {
        intAllocator.init()
        floatAllocator.init()
    }

    fun buildFunction() {
        val cfg = function.value
        ops.add(Label("_${function.name}"))

        // Prologue
        ops.add(Stp(x29, x30, SP, -16, StpMode.PRE_INDEXED))
        ops.add(PushRegsStub) // Push used callee-saved registers
        ops.add(Mov(x29, SP))
        ops.add(SPAllocStub) // Allocate stack space for locals

        // Form some stable blocks order
        cfg.blocks.entries.forEach { (label, _) ->
            orderedBlocks.add(label)
        }
        orderedBlocks.indexOf(cfg.root).let { rootIndex ->
            if (rootIndex == 0) return@let
            // Root must be the first block
            orderedBlocks.removeAt(rootIndex)
            orderedBlocks.add(0, cfg.root)
        }
        check(orderedBlocks[0] == cfg.root) {
            "Root block must be the first block in the order"
        }

        orderedBlocks.forEachIndexed { blockIndex, label ->
            if (label != cfg.root) {
                ops.add(Label(label.local()))
            }

            currentBlockIndex = blockIndex
            cfg.blocks[label]!!.irNodes.forEach { node ->
                emitNode(node)
            }
        }

        // Epilogue
        ops.add(returnLabel)
        ops.add(Mov(SP, x29))
        ops.add(PopRegsStub)
        ops.add(Ldp(x29, x30, SP, 16, StpMode.POST_INDEXED))
        ops.add(Ret)

        setStackAllocSize()
        setPushPopRegs()
    }

    private fun chooseAllocator(value: IRValue): BaseMemoryAllocator<*> = when (value.type) {
        IRType.INT64 -> intAllocator
        IRType.FLOAT64 -> floatAllocator
    }

    private fun setPushPopRegs() {
        val regs = intAllocator.usedRegisters
            .filter { it in X.CalleeSaved }
            .sortedBy { it.index }

        // Create pairs of used callee-saved registers
        val regPairs = mutableListOf<Pair<X, X?>>()
        for (i in 0 until regs.size step 2) {
            if (i == regs.lastIndex) break
            regPairs.add(regs[i] to regs[i + 1])
        }
        if (regs.size % 2 == 1) regPairs.add(regs.last() to null)

        // Replace the push stub with a list of stp/str
        val pushIndex = ops.indexOfFirst { it === PushRegsStub }
        ops.removeAt(pushIndex)
        val pushOps = regPairs.map { (r1, r2) ->
            if (r2 != null) {
                Stp(r1, r2, SP, -16, StpMode.PRE_INDEXED)
            } else {
                Str(r1, SP, -16, StpMode.PRE_INDEXED)
            }
        }
        ops.addAll(pushIndex, pushOps)

        // Replace the pop stub with a list of ldp/ldr
        val popIndex = ops.indexOfFirst { it === PopRegsStub }
        ops.removeAt(popIndex)
        val popOps = regPairs.reversed().map { (r1, r2) ->
            if (r2 != null) {
                Ldp(r1, r2, SP, 16, StpMode.POST_INDEXED)
            } else {
                Ldr(r1, SP, 16, StpMode.POST_INDEXED)
            }
        }
        ops.addAll(popIndex, popOps)
    }

    private fun setStackAllocSize() {
        val allocOp = ops.indexOfFirst { it === SPAllocStub }
        check(allocOp >= 0) { "Failed to find stack allocation stub" }
        if (intAllocator.alignedAllocatedSize == 0) {
            ops.removeAt(allocOp)
        } else {
            ops[allocOp] = SubImm(SP, SP, intAllocator.alignedAllocatedSize)
        }
    }

    /**
     * Helper function for emitting copy of a memory location [src] to another memory location [dst].
     */
    fun emitCopy(dst: MemoryLocation, src: MemoryLocation) {
        if (src == dst) return
        when (dst) {
            is X -> when (src) {
                is X -> ops.add(Mov(dst, src))
                is StackLocation -> ops.add(Ldr(dst, SP, src.offset, StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory locations: $dst <- $src")
            }
            is D -> when (src) {
                is D -> ops.add(FMov(dst, src))
                else -> error("Unsupported memory locations: $dst <- $src")
            }
            is StackLocation -> when (src) {
                is X -> ops.add(Str(src, SP, dst.offset, StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory location: $dst <- $src")
            }
            else -> error("Unsupported memory location: $dst <- $src")
        }
    }

    /**
     * Helper function for emitting copy of any [value] to a memory location [dst].
     */
    fun emitCopy(dst: MemoryLocation, value: IRValue) {
        when (value) {
            is IRVar -> emitCopy(dst, chooseAllocator(value).loc(value))
            is IRInt -> {
                when (dst) {
                    is X -> emitAssignConstantInt64(dst, value.value)
                    is StackLocation -> {
                        intAllocator.tempReg { reg ->
                            emitAssignConstantInt64(reg, value.value)
                            emitCopy(dst, reg)
                        }
                    }
                    else -> error("Unsupported memory location: $dst <- $value")
                }
            }
            is IRFloat -> {
                when (dst) {
                    is D -> emitAssignConstantFloat64(dst, value.value)
                    else -> error("Unsupported memory location: $dst <- $value")
                }
            }
        }
    }

    fun emitAssignConstantInt64(targetReg: X, value: Long) {
        // TODO add support for cases with movn
        val parts = (0..3).map { (value ushr (16 * it)) and 0xFFFFL }

        ops.add(MovZ(targetReg, parts[0]))
        if (parts[1] != 0L) ops.add(MovK(targetReg, parts[1], 16))
        if (parts[2] != 0L) ops.add(MovK(targetReg, parts[2], 32))
        if (parts[3] != 0L) ops.add(MovK(targetReg, parts[3], 48))
    }

    fun emitAssignConstantFloat64(targetReg: D, value: Double) {
        val label = constPool.getConstant(value)
        intAllocator.tempReg { reg ->
            ops.add(Adrp(reg, "$label@PAGE"))
            ops.add(Ldr(targetReg, reg, "$label@PAGEOFF", StpMode.SIGNED_OFFSET))
        }
    }

    private fun emitNode(node: IRNode) {
        when (node) {
            is IRBinOp -> emitBinOp(node)
            is IRAssign -> emitAssign(node)
            is IRNot -> emitNot(node)
            is IRFunctionCall -> emitCall(node)
            is IRJumpIfTrue -> emitJcc(node)
            is IRJump -> emitB(node)
            is IRReturn -> emitRet(node)
            is IRPhi -> error("Backend compilation supports only non-SSA IR, phi-nodes are not allowed")
        }
    }

    private fun emitB(node: IRJump) {
        if (node.target == orderedBlocks.getOrNull(currentBlockIndex + 1)) {
            return
        }

        ops.add(B(node.target.local()))
    }

    private fun emitRet(node: IRReturn) {
        node.value?.let { value ->
            val targetReg = when (value.type) {
                IRType.INT64 -> x0
                IRType.FLOAT64 -> d0
            }
            emitCopy(targetReg, value)
        }
        ops.add(B(returnLabel.name))
    }

    private fun emitAssign(node: IRAssign) {
        val allocator = chooseAllocator(node.result)
        allocator.writeReg(node.result) { dst ->
            emitCopy(dst, node.right)
        }
    }

    private fun emitBinOp(node: IRBinOp) {
        when (node.left.type) {
            IRType.INT64 -> emitIntBinOp(node)
            IRType.FLOAT64 -> emitFloatBinOp(node)
        }
    }

    private fun emitIntBinOp(node: IRBinOp) {
        intAllocator.writeReg(node.result) { dst ->
            intAllocator.readReg(node.left) { left ->
                intAllocator.readReg(node.right) { right ->
                    when (node.op) {
                        IRBinOpKind.ADD -> ops.add(Add(dst, left, right))
                        IRBinOpKind.SUB -> ops.add(Sub(dst, left, right))
                        IRBinOpKind.MUL -> ops.add(Mul(dst, left, right))
                        IRBinOpKind.DIV -> ops.add(SDiv(dst, left, right))
                        IRBinOpKind.MOD -> {
                            // dst = l - (l / r) * r
                            ops.add(SDiv(dst, left, right))
                            ops.add(MSub(dst, dst, right, left))
                        }
                        IRBinOpKind.EQ, IRBinOpKind.NEQ, IRBinOpKind.GT,
                        IRBinOpKind.GE, IRBinOpKind.LT, IRBinOpKind.LE -> {
                            ops.add(Cmp(left, right))
                            val cond = when (node.op) {
                                IRBinOpKind.EQ -> ConditionFlag.EQ
                                IRBinOpKind.NEQ -> ConditionFlag.NE
                                IRBinOpKind.GT -> ConditionFlag.GT
                                IRBinOpKind.GE -> ConditionFlag.GE
                                IRBinOpKind.LT -> ConditionFlag.LT
                                IRBinOpKind.LE -> ConditionFlag.LE
                                else -> error("Unexpected comparison operator: ${node.op}")
                            }
                            ops.add(CSet(dst, cond))
                        }
                    }
                }
            }
        }
    }

    private fun emitFloatBinOp(node: IRBinOp) {
        chooseAllocator(node.result).writeReg(node.result) { dst ->
            floatAllocator.readReg(node.left) { left ->
                floatAllocator.readReg(node.right) { right ->
                    when (node.op) {
                        IRBinOpKind.ADD -> ops.add(FAdd(dst as D, left, right))
                        IRBinOpKind.SUB -> ops.add(FSub(dst as D, left, right))
                        IRBinOpKind.MUL -> ops.add(FMul(dst as D, left, right))
                        IRBinOpKind.DIV -> ops.add(FDiv(dst as D, left, right))
                        IRBinOpKind.MOD -> {
                            error("Float modulo is not supported")
                        }
                        IRBinOpKind.EQ, IRBinOpKind.NEQ, IRBinOpKind.GT,
                        IRBinOpKind.GE, IRBinOpKind.LT, IRBinOpKind.LE -> {
                            ops.add(FCmp(left, right))
                            val cond = when (node.op) {
                                IRBinOpKind.EQ -> ConditionFlag.EQ
                                IRBinOpKind.NEQ -> ConditionFlag.NE
                                IRBinOpKind.GT -> ConditionFlag.GT
                                IRBinOpKind.GE -> ConditionFlag.GE
                                IRBinOpKind.LT -> ConditionFlag.LT
                                IRBinOpKind.LE -> ConditionFlag.LE
                                else -> error("Unexpected comparison operator: ${node.op}")
                            }
                            ops.add(CSet(dst as X, cond))
                        }
                    }
                }
            }
        }
    }

    private fun emitNot(n: IRNot) {
        intAllocator.readReg(n.value) { v ->
            ops.add(CmpImm(v, 0))
        }
        intAllocator.writeReg(n.result) { dst ->
            ops.add(CSet(dst, ConditionFlag.EQ))
        }
    }

    private fun emitJcc(n: IRJumpIfTrue) {
        intAllocator.readReg(n.cond) { v ->
            ops.add(CmpImm(v, 0))
        }

        val nextBlock = orderedBlocks.getOrNull(currentBlockIndex + 1)
        when {
            n.target == nextBlock -> ops.add(BCond(ConditionFlag.EQ, n.elseTarget.local()))
            n.elseTarget == nextBlock -> ops.add(BCond(ConditionFlag.NE, n.target.local()))
            else -> {
                ops.add(BCond(ConditionFlag.NE, n.target.local()))
                ops.add(B(n.elseTarget.local()))
            }
        }
    }

    private fun emitCall(n: IRFunctionCall) {
        // TODO support more than 8 arguments
        var intIndex = 0
        var floatIndex = 0
        n.arguments.forEach { arg ->
            val reg = when (arg.type) {
                IRType.INT64 -> X(intIndex++).also { check(intIndex <= 8) }
                IRType.FLOAT64 -> D(floatIndex++).also { check(floatIndex <= 8) }
            }
            emitCopy(reg, arg)
        }

        ops.add(BL("_${n.name}"))
        n.result?.let { res ->
            val dst = chooseAllocator(res).loc(res)
            val resultReg = when (res.type) {
                IRType.INT64 -> x0
                IRType.FLOAT64 -> d0
            }
            emitCopy(dst, resultReg)
        }
    }

    companion object {
        private val SPAllocStub = CustomText("<sp allocation of locals>")
        private val PushRegsStub = CustomText("<save callee-saved registers>")
        private val PopRegsStub = CustomText("<restore callee-saved registers>")

        private val x0 = X(0)
        private val d0 = D(0)
        private val x29 = X(29)
        private val x30 = X(30)

        private fun IRLabel.local() = ".$name"
    }
}