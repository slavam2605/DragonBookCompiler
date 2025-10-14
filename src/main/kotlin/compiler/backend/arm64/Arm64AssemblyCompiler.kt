package compiler.backend.arm64

import compiler.backend.arm64.IntRegister.Companion.X29
import compiler.backend.arm64.IntRegister.Companion.X30
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.instructions.*
import compiler.backend.arm64.ops.OpsEmitter
import compiler.backend.arm64.ops.utils.PushPopUtils
import compiler.backend.arm64.ops.utils.local
import compiler.backend.arm64.registerAllocation.CompositeRegisterAllocator
import compiler.frontend.FrontendFunction
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.hasFunctionCalls

class Arm64AssemblyCompiler(
    private val function: FrontendFunction<ControlFlowGraph>,
    private val constPool: Arm64ConstantPool,
    private val ops: MutableList<Instruction>
) {
    private val context = NativeCompilerContext(
        function = function,
        cfg = function.value,
        ops = ops,
        constPool = constPool,
        orderedBlocks = BlocksSorter.sort(function.value),
        returnLabel = Label(".L_${function.name}_return"),
        currentBlockIndex = 0,
        spShift = 0
    )
    private val allocator = CompositeRegisterAllocator(context, function, ops)
    private val emitter: OpsEmitter

    init {
        context.allocator = allocator
        emitter = OpsEmitter(context)
    }

    fun buildFunction() {
        val cfg = function.value
        val isLeaf = !cfg.hasFunctionCalls()

        ops.add(Label("_${function.name}"))

        // Prologue
        ops.add(PushRegsStub) // Push used callee-saved registers (includes x29/x30 for non-leaf)
        if (!isLeaf) {
            ops.add(Mov(X29, SP))
        }
        ops.add(SPAllocStub) // Allocate stack space for locals

        context.orderedBlocks.forEachIndexed { blockIndex, label ->
            if (label != cfg.root) {
                ops.add(Label(label.local(function.name)))
            }

            context.currentBlockIndex = blockIndex
            val window = IRPeepholeWindow(cfg.blocks[label]!!.irNodes)
            while (window.hasNext) {
                emitter.emitOnce(window)
            }
        }

        // Epilogue
        ops.add(context.returnLabel)
        if (!isLeaf) {
            ops.add(Mov(SP, X29))
        } else {
            // For leaf functions, manually deallocate stack space for locals
            ops.add(SPDeallocStub)
        }
        ops.add(PopRegsStub) // Pop callee-saved registers (includes x29/x30 for non-leaf)
        ops.add(Ret)

        setStackAllocSize()
        setPushPopRegs(isLeaf)
    }

    private fun setPushPopRegs(isLeaf: Boolean) {
        val regPairs = mutableListOf<Pair<Register, Register?>>()

        // For non-leaf functions, x29/x30 must be saved first
        if (!isLeaf) {
            regPairs.add(X29 to X30)
        }

        PushPopUtils.fillPairs(regPairs, allocator.usedRegisters(X::class.java), X.CalleeSaved)
        PushPopUtils.fillPairs(regPairs, allocator.usedRegisters(D::class.java), D.CalleeSaved)

        // Replace the push stub with a list of stp/str
        val pushIndex = ops.indexOfFirst { it === PushRegsStub }
        ops.removeAt(pushIndex)
        val pushOps = PushPopUtils.createPushOps(regPairs)
        ops.addAll(pushIndex, pushOps)

        // Replace the pop stub with a list of ldp/ldr
        val popIndex = ops.indexOfFirst { it === PopRegsStub }
        ops.removeAt(popIndex)
        val popOps = PushPopUtils.createPopOps(regPairs)
        ops.addAll(popIndex, popOps)
    }

    private fun setStackAllocSize() {
        // Handle allocation stub
        val allocOp = ops.indexOfFirst { it === SPAllocStub }
        check(allocOp >= 0) { "Failed to find stack allocation stub" }
        if (allocator.alignedAllocatedSize == 0) {
            ops.removeAt(allocOp)
        } else {
            ops[allocOp] = SubImm(SP, SP, allocator.alignedAllocatedSize)
        }

        // Handle deallocation stub (for leaf functions only)
        val deallocOp = ops.indexOfFirst { it === SPDeallocStub }
        if (deallocOp >= 0) {
            if (allocator.alignedAllocatedSize == 0) {
                ops.removeAt(deallocOp)
            } else {
                ops[deallocOp] = AddImm(SP, SP, allocator.alignedAllocatedSize)
            }
        }
    }

    companion object {
        private val SPAllocStub = CustomText("<sp allocation of locals>")
        private val SPDeallocStub = CustomText("<sp deallocation of locals>")
        private val PushRegsStub = CustomText("<save callee-saved registers>")
        private val PopRegsStub = CustomText("<restore callee-saved registers>")
    }
}