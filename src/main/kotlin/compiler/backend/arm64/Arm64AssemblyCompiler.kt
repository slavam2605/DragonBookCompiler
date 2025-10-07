package compiler.backend.arm64

import compiler.backend.arm64.IntRegister.Companion.D0
import compiler.backend.arm64.IntRegister.Companion.X0
import compiler.backend.arm64.IntRegister.Companion.X29
import compiler.backend.arm64.IntRegister.Companion.X30
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.registerAllocation.CompositeRegisterAllocator
import compiler.frontend.FrontendFunction
import compiler.ir.*
import compiler.ir.IRBinOpKind
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.LoopInfo
import java.lang.Double.doubleToLongBits

class Arm64AssemblyCompiler(
    private val function: FrontendFunction<ControlFlowGraph>,
    private val constPool: Arm64ConstantPool,
    private val ops: MutableList<Instruction>
) {
    val allocator = CompositeRegisterAllocator(this, function, ops)
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

        // Order blocks to minimize branching cost using loop-aware greedy algorithm
        val loopInfo = LoopInfo.get(cfg)
        orderedBlocks.addAll(computeBlockOrder(cfg, loopInfo))
        check(orderedBlocks[0] == cfg.root) {
            "Root block must be the first block in the order"
        }

        orderedBlocks.forEachIndexed { blockIndex, label ->
            if (label != cfg.root) {
                ops.add(Label(label.local()))
            }

            currentBlockIndex = blockIndex
            val window = IRPeepholeWindow(cfg.blocks[label]!!.irNodes)
            while (window.hasNext) {
                emitOnce(window)
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

    private fun pushCallerSaved(liveVars: Set<IRVar>): List<Pair<Register, Register?>> {
        // Only consider registers that contain live variables
        val liveRegs = liveVars.mapNotNull {
            allocator.loc(it) as? Register
        }.toSet()

        // Filter used registers to only those containing live variables
        val liveUsedIntRegs = allocator.usedRegisters(X::class.java).filter { it in liveRegs }
        val liveUsedFloatRegs = allocator.usedRegisters(D::class.java).filter { it in liveRegs }

        val regPairs = mutableListOf<Pair<Register, Register?>>()
        fillPairs(regPairs, liveUsedIntRegs.toSet(), X.CallerSaved)
        fillPairs(regPairs, liveUsedFloatRegs.toSet(), D.CallerSaved)
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

    private fun emitOnce(window: IRPeepholeWindow) {
        val node = window.current ?: return
        window.move()
        when (node) {
            is IRBinOp -> emitBinOp(node, window)
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
        if (currentBlockIndex != orderedBlocks.lastIndex) {
            ops.add(B(returnLabel.name))
        }
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

    private fun emitBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        require(node.left.type == node.right.type) {
            "IRBinOp requires operands of the same type, got ${node.left.type} and ${node.right.type}"
        }
        if (node.left.type == IRType.FLOAT64) {
            emitFloatBinOp(node, window)
        } else {
            emitIntBinOp(node, window)
        }
    }

    private fun emitIntBinOp(node: IRBinOp, window: IRPeepholeWindow) {
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
                            insertComparisonJumpOrSet(window, node, node.op, dst)
                        }
                    }
                }
            }
        }
    }

    private fun emitFloatBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        allocator.writeReg(node.result) { dst ->
            allocator.readReg(node.left) { left -> left as D
                allocator.readReg(node.right) { right -> right as D
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
                            insertComparisonJumpOrSet(window, node, node.op, dst as X)
                        }
                    }
                }
            }
        }
    }

    private fun insertComparisonJumpOrSet(window: IRPeepholeWindow, node: IRBinOp, op: IRBinOpKind, dst: X) {
        val nextIr = window.current as? IRJumpIfTrue
        if (nextIr?.cond == node.result && !allocator.getLivenessInfo().isLiveAfterBranch(node.result, nextIr)) {
            window.move()
            insertBranches(op.toConditionFlag(), nextIr.target, nextIr.elseTarget)
        } else {
            ops.add(CSet(dst, op.toConditionFlag()))
        }
    }

    private fun insertBranches(flag: ConditionFlag, trueTarget: IRLabel, falseTarget: IRLabel) {
        val nextLabel = orderedBlocks.getOrNull(currentBlockIndex + 1)?.local() ?: returnLabel.name
        val trueLabel = trueTarget.local()
        val falseLabel = falseTarget.local()

        when (nextLabel) {
            trueLabel -> {
                ops.add(BCond(flag.invert(), falseLabel))
            }
            falseLabel -> {
                ops.add(BCond(flag, trueLabel))
            }
            else -> {
                ops.add(BCond(flag, trueLabel))
                ops.add(B(falseLabel))
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

        insertBranches(ConditionFlag.NE, n.target, n.elseTarget)
    }

    private fun emitCall(n: IRFunctionCall) {
        // Get the set of variables that are live after this call
        val liveVars = allocator.getLivenessInfo().liveAtCalls[n] ?: emptySet()
        val pushedRegs = pushCallerSaved(liveVars)
        val pushedRegsSet = pushedRegs.flatMap { listOfNotNull(it.first, it.second) }.toSet()

        // TODO support more than 8 arguments
        val writtenArgRegs = mutableSetOf<Register>()
        fun checkArgumentRegister(arg: IRValue) {
            if (arg !is IRVar) return
            val argReg = allocator.loc(arg) as? Register ?: return
            if (argReg !in writtenArgRegs) return

            error("Argument conflict detected: argument ${arg.printToString()} " +
                    "reads from $argReg which is overwritten by an earlier argument.")
        }

        // Emit argument copies
        var intIndex = 0
        var floatIndex = 0
        n.arguments.forEach { arg ->
            val reg = when (arg.type) {
                IRType.INT64 -> X(intIndex++).also { check(intIndex <= 8) }
                IRType.FLOAT64 -> D(floatIndex++).also { check(floatIndex <= 8) }
            }
            checkArgumentRegister(arg)
            emitCopy(reg, arg)
            writtenArgRegs.add(reg)
        }

        ops.add(BL("_${n.name}"))
        n.result?.let { res ->
            val dst = allocator.loc(res)
            val resultReg = when (res.type) {
                IRType.INT64 -> X0
                IRType.FLOAT64 -> D0
            }

            check(dst !in pushedRegsSet) {
                "Result register $dst must not be pushed, it is not live during the call"
            }
            emitCopy(dst, resultReg)
        }
        popCallerSaved(pushedRegs)
    }

    private fun IRBinOpKind.toConditionFlag(): ConditionFlag {
        return when (this) {
            IRBinOpKind.EQ -> ConditionFlag.EQ
            IRBinOpKind.NEQ -> ConditionFlag.NE
            IRBinOpKind.GT -> ConditionFlag.GT
            IRBinOpKind.GE -> ConditionFlag.GE
            IRBinOpKind.LT -> ConditionFlag.LT
            IRBinOpKind.LE -> ConditionFlag.LE
            else -> error("Unexpected comparison operator: $this")
        }
    }

    companion object {
        private val SPAllocStub = CustomText("<sp allocation of locals>")
        private val PushRegsStub = CustomText("<save callee-saved registers>")
        private val PopRegsStub = CustomText("<restore callee-saved registers>")

        private fun IRLabel.local() = ".$name"

        /**
         * Computes optimal block ordering using a greedy algorithm that minimizes
         * the total cost of edges that require explicit branches.
         *
         * Cost model:
         * - If blocks A and B are consecutive and A→B exists, cost = 0 (fall-through)
         * - Otherwise, cost = edge_weight(A→B) from loop analysis
         *
         * Algorithm: Greedy chain-building
         * 1. Start with the root block
         * 2. At each step, follow the highest-weight outgoing edge to an unvisited block
         * 3. If no unvisited successors, pick the highest-weight unvisited block
         */
        private fun computeBlockOrder(cfg: ControlFlowGraph, loopInfo: LoopInfo): List<IRLabel> {
            val resultOrder = mutableListOf<IRLabel>()
            val visited = mutableSetOf<IRLabel>()

            fun visit(block: IRLabel) {
                if (block in visited) return
                visited.add(block)
                resultOrder.add(block)

                // Find the best successor to visit next (highest nesting level edge to unvisited block)
                val bestSuccessor = cfg.edges(block)
                    .filter { it !in visited }
                    .maxByOrNull { successor ->
                        // Prefer edges with higher nesting level (more deeply nested in loops)
                        // This keeps loop bodies together in the generated code
                        loopInfo.edgeNestingLevel[block to successor]!!
                    }

                if (bestSuccessor != null) {
                    visit(bestSuccessor)
                }
            }

            // Start with root
            visit(cfg.root)

            // Visit any remaining unvisited blocks (unreachable code)
            // Prioritize blocks with higher nesting level (likely more important)
            cfg.blocks.keys
                .filter { it !in visited }
                .sortedByDescending { loopInfo.blockNestingLevel[it] ?: 0 }
                .forEach { visit(it) }

            return resultOrder
        }
    }
}