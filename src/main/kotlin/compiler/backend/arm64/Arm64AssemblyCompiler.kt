package compiler.backend.arm64

import compiler.backend.arm64.IntRegister.Companion.D0
import compiler.backend.arm64.IntRegister.Companion.X0
import compiler.backend.arm64.IntRegister.Companion.X29
import compiler.backend.arm64.IntRegister.Companion.X30
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.registerAllocation.AggregatedRegisterAllocator
import compiler.backend.arm64.registerAllocation.RegHandle
import compiler.frontend.FrontendFunction
import compiler.ir.*
import compiler.ir.cfg.ControlFlowGraph
import java.lang.Double.doubleToLongBits

class Arm64AssemblyCompiler(
    private val function: FrontendFunction<ControlFlowGraph>,
    private val constPool: Arm64ConstantPool,
    private val ops: MutableList<Instruction>
) {
    val allocator = AggregatedRegisterAllocator(this, function, ops)
    private val returnLabel = Label(".L_${function.name}_return")
    private val orderedBlocks = mutableListOf<IRLabel>()
    private var currentBlockIndex = 0
    internal var spShift = 0 // How much SP is different from right after prologue

    fun buildFunction() {
        val cfg = function.value
        ops.add(Label("_${function.name}"))

        // Prologue
        ops.add(Stp(X29, X30, SP, -16, StpMode.PRE_INDEXED))
        ops.add(PushRegsStub) // Push used callee-saved registers
        ops.add(Mov(X29, SP))
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
        ops.add(Mov(SP, X29))
        ops.add(PopRegsStub)
        ops.add(Ldp(X29, X30, SP, 16, StpMode.POST_INDEXED))
        ops.add(Ret)

        setStackAllocSize()
        setPushPopRegs()
    }

    private fun setPushPopRegs() {
        val regPairs = mutableListOf<Pair<Register, Register?>>()
        fillPairs(regPairs, allocator.usedRegisters(X::class.java), X.CalleeSaved)
        fillPairs(regPairs, allocator.usedRegisters(D::class.java), D.CalleeSaved)

        // Replace the push stub with a list of stp/str
        val pushIndex = ops.indexOfFirst { it === PushRegsStub }
        ops.removeAt(pushIndex)
        val pushOps = createPushOps(regPairs)
        ops.addAll(pushIndex, pushOps)

        // Replace the pop stub with a list of ldp/ldr
        val popIndex = ops.indexOfFirst { it === PopRegsStub }
        ops.removeAt(popIndex)
        val popOps = createPopOps(regPairs)
        ops.addAll(popIndex, popOps)
    }

    private fun createPopOps(regPairs: List<Pair<Register, Register?>>): List<Instruction> =
        regPairs.reversed().map { (r1, r2) ->
            if (r2 != null) {
                Ldp(r1, r2, SP, 16, StpMode.POST_INDEXED)
            } else {
                Ldr(r1, SP, 16, StpMode.POST_INDEXED)
            }
        }

    private fun createPushOps(regPairs: List<Pair<Register, Register?>>): List<Instruction> =
        regPairs.map { (r1, r2) ->
            if (r2 != null) {
                Stp(r1, r2, SP, -16, StpMode.PRE_INDEXED)
            } else {
                Str(r1, SP, -16, StpMode.PRE_INDEXED)
            }
        }

    private fun pushCallerSaved(): List<Pair<Register, Register?>> {
        val regPairs = mutableListOf<Pair<Register, Register?>>()
        fillPairs(regPairs, allocator.usedRegisters(X::class.java), X.CallerSaved)
        fillPairs(regPairs, allocator.usedRegisters(D::class.java), D.CallerSaved)
        ops.addAll(createPushOps(regPairs))

        check(spShift == 0) { "SP shift must be zero before calls" }
        spShift = regPairs.size * 16
        return regPairs
    }

    private fun popCallerSaved(regPairs: List<Pair<Register, Register?>>) {
        ops.addAll(createPopOps(regPairs))
        spShift = 0
    }

    private fun fillPairs(
        regPairs: MutableList<Pair<Register, Register?>>,
        usedRegisters: Set<Register>,
        saved: Set<Register>
    ) {
        val regs = usedRegisters
            .filter { it in saved }
            .sortedBy { (it as? X)?.index ?: (it as D).index }

        // Create pairs of used callee-saved registers
        for (i in 0 until regs.size step 2) {
            if (i == regs.lastIndex) break
            regPairs.add(regs[i] to regs[i + 1])
        }
        if (regs.size % 2 == 1) regPairs.add(regs.last() to null)
    }

    private fun setStackAllocSize() {
        val allocOp = ops.indexOfFirst { it === SPAllocStub }
        check(allocOp >= 0) { "Failed to find stack allocation stub" }
        if (allocator.alignedAllocatedSize == 0) {
            ops.removeAt(allocOp)
        } else {
            ops[allocOp] = SubImm(SP, SP, allocator.alignedAllocatedSize)
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
                is StackLocation -> ops.add(Ldr(dst, SP, src.spOffset(spShift), StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory locations: $dst <- $src")
            }
            is D -> when (src) {
                is D -> ops.add(FMov(dst, src))
                is StackLocation -> ops.add(Ldr(dst, SP, src.spOffset(spShift), StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory locations: $dst <- $src")
            }
            is StackLocation -> when (src) {
                is X -> ops.add(Str(src, SP, dst.spOffset(spShift), StpMode.SIGNED_OFFSET))
                is D -> ops.add(Str(src, SP, dst.spOffset(spShift), StpMode.SIGNED_OFFSET))
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
            is IRVar -> emitCopy(dst, allocator.loc(value))
            is IRInt -> {
                when (dst) {
                    is X -> emitAssignConstantInt64(dst, value.value)
                    is StackLocation -> {
                        allocator.tempIntReg { reg ->
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
        if (value == 0L) {
            ops.add(Mov(targetReg, IntRegister.Xzr))
            return
        }

        val parts = (0..3).map { (value ushr (16 * it)) and 0xFFFFL }

        var isFirstOp = true
        parts.forEachIndexed { index, part ->
            if (part == 0L) return@forEachIndexed
            val opCtr = if (isFirstOp) ::MovZ else ::MovK
            ops.add(opCtr(targetReg, part, 16 * index))
            isFirstOp = false
        }
        check(!isFirstOp)
    }

    fun emitAssignConstantFloat64(targetReg: D, value: Double) {
        // TODO support mov/movk and fmov d<n>, x<m>

        if (value == 0.0) {
            ops.add(FMov(targetReg, IntRegister.Xzr))
            return
        }

        // Use `fmov dX, imm8` if possible
        val bits = doubleToLongBits(value)
        val exp = ((bits ushr 52) and 0x7FFL) - 1023
        val frac = bits and ((1L shl 52) - 1)
        if (exp in -3..4 && (frac and ((1L shl 48) - 1)) == 0L) {
            ops.add(FMovImm(targetReg, value))
            return
        }

        // TODO why only two parts?
        val parts = (0..3).map { (bits ushr (16 * it)) and 0xFFFFL }
        if (parts.count { it != 0L } <= 2) {
            allocator.tempIntReg { reg ->
                emitAssignConstantInt64(reg, bits)
                ops.add(FMov(targetReg, reg))
            }
            return
        }

        val label = constPool.getConstant(value)
        allocator.tempIntReg { reg ->
            ops.add(Adrp(reg, "$label@PAGE"))
            ops.add(Ldr(targetReg, reg, "$label@PAGEOFF", StpMode.SIGNED_OFFSET))
        }
    }

    private fun emitNode(node: IRNode) {
        when (node) {
            is IRBinOp -> emitBinOp(node)
            is IRAssign -> emitAssign(node)
            is IRNot -> emitNot(node)
            is IRConvert -> emitConvert(node)
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
                IRType.INT64 -> X0
                IRType.FLOAT64 -> D0
            }
            emitCopy(targetReg, value)
        }
        ops.add(B(returnLabel.name))
    }

    private fun emitAssign(node: IRAssign) {
        allocator.writeReg(node.result) { dst ->
            emitCopy(dst, node.right)
        }
    }

    private fun emitConvert(node: IRConvert) {
        allocator.writeReg(node.result) { dst ->
            allocator.readReg(node.value) { src ->
                when {
                    node.result.type == IRType.FLOAT64 && node.value.type == IRType.INT64 -> {
                        check(dst is D && src is X) { "Int to float conversion requires X -> D registers" }
                        ops.add(Scvtf(dst, src))
                    }
                    node.result.type == IRType.INT64 && node.value.type == IRType.FLOAT64 -> {
                        check(dst is X && src is D) { "Float to int conversion requires D -> X registers" }
                        ops.add(Fcvtzs(dst, src))
                    }
                    else -> error("Invalid conversion: ${node.value.type} -> ${node.result.type}")
                }
            }
        }
    }

    private fun emitBinOp(node: IRBinOp) {
        val anyFloat = node.left.type == IRType.FLOAT64 || node.right.type == IRType.FLOAT64
        if (anyFloat) {
            emitFloatBinOp(node)
        } else {
            emitIntBinOp(node)
        }
    }

    private fun emitIntBinOp(node: IRBinOp) {
        allocator.writeReg(node.result) { dst ->
            allocator.readReg(node.left) { left ->
                allocator.readReg(node.right) { right ->
                    check(dst is X && left is X && right is X)
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
        allocator.writeReg(node.result) { dst ->
            val left = allocator.readReg(node.left)
            val right = allocator.readReg(node.right)
            check(left.reg is D || right.reg is D)
            emitIntToFloat(left) { leftD ->
                emitIntToFloat(right) { rightD ->
                    emitFloatBinOpWithRegs(node, dst, leftD, rightD)
                }
            }
        }
    }

    private fun emitIntToFloat(handle: RegHandle<Register>, block: (D) -> Unit) {
        if (handle.reg is D) return block(handle.reg).also { handle.dispose() }
        check(handle.reg is X)
        allocator.tempFloatReg { tmp ->
            ops.add(Scvtf(tmp, handle.reg))
            handle.dispose()
            block(tmp)
        }
    }

    private fun emitFloatBinOpWithRegs(node: IRBinOp, dst: Register, left: D, right: D): Boolean {
        return when (node.op) {
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

    private fun emitNot(n: IRNot) {
        allocator.readReg(n.value) { v ->
            ops.add(CmpImm(v as X, 0))
        }
        allocator.writeReg(n.result) { dst ->
            ops.add(CSet(dst as X, ConditionFlag.EQ))
        }
    }

    private fun emitJcc(n: IRJumpIfTrue) {
        allocator.readReg(n.cond) { v ->
            ops.add(CmpImm(v as X, 0))
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
        val pushedRegs = pushCallerSaved()
        val pushedRegsSet = pushedRegs.flatMap { listOfNotNull(it.first, it.second) }.toSet()

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
            val dst = allocator.loc(res)
            val resultReg = when (res.type) {
                IRType.INT64 -> X0
                IRType.FLOAT64 -> D0
            }

            when {
                resultReg !in pushedRegsSet -> {
                    popCallerSaved(pushedRegs)
                    emitCopy(dst, resultReg)
                }
                dst !in pushedRegsSet -> {
                    emitCopy(dst, resultReg)
                    popCallerSaved(pushedRegs)
                }
                else -> allocator.tempReg(res) { tmp ->
                    emitCopy(tmp, resultReg)
                    popCallerSaved(pushedRegs)
                    emitCopy(dst, tmp)
                }
            }
        } ?: run {
            popCallerSaved(pushedRegs)
        }
    }

    companion object {
        private val SPAllocStub = CustomText("<sp allocation of locals>")
        private val PushRegsStub = CustomText("<save callee-saved registers>")
        private val PopRegsStub = CustomText("<restore callee-saved registers>")

        private fun IRLabel.local() = ".$name"
    }
}