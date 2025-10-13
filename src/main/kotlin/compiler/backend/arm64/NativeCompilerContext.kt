package compiler.backend.arm64

import compiler.backend.arm64.instructions.Instruction
import compiler.backend.arm64.instructions.Label
import compiler.backend.arm64.registerAllocation.CompositeRegisterAllocator
import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph

class NativeCompilerContext(
    val cfg: ControlFlowGraph,
    val ops: MutableList<Instruction>,
    val constPool: Arm64ConstantPool,
    val orderedBlocks: List<IRLabel>,
    val returnLabel: Label,

    // *** Mutable part ***
    var currentBlockIndex: Int,
    var spShift: Int // How much SP is different from right after prologue
) {
    lateinit var allocator: CompositeRegisterAllocator

    init {
        check(orderedBlocks.first() == cfg.root) {
            "Root block must be the first block in the order"
        }
    }
}