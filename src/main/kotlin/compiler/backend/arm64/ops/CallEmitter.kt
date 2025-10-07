package compiler.backend.arm64.ops

import compiler.backend.arm64.BL
import compiler.backend.arm64.IntRegister.Companion.D0
import compiler.backend.arm64.IntRegister.Companion.X0
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.ops.utils.CopyUtils
import compiler.backend.arm64.ops.utils.PushPopUtils
import compiler.ir.IRFunctionCall
import compiler.ir.IRType
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.printToString

class CallEmitter(private val context: NativeCompilerContext) {
    private val allocator = context.allocator
    private val ops = context.ops

    fun emitCall(n: IRFunctionCall) {
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
            CopyUtils.emitCopy(context, reg, arg)
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
            CopyUtils.emitCopy(context, dst, resultReg)
        }
        popCallerSaved(pushedRegs)
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
        PushPopUtils.fillPairs(regPairs, liveUsedIntRegs.toSet(), X.CallerSaved)
        PushPopUtils.fillPairs(regPairs, liveUsedFloatRegs.toSet(), D.CallerSaved)
        ops.addAll(PushPopUtils.createPushOps(regPairs))

        check(context.spShift == 0) { "SP shift must be zero before calls" }
        context.spShift = regPairs.size * 16
        return regPairs
    }

    private fun popCallerSaved(regPairs: List<Pair<Register, Register?>>) {
        ops.addAll(PushPopUtils.createPopOps(regPairs))
        context.spShift = 0
    }
}