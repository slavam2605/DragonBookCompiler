import compiler.backend.arm64.Arm64CompilationFlow
import compiler.frontend.CompilationFailed
import compiler.frontend.FrontendCompilationFlow
import parser.ParserFlow
import java.io.File
import kotlin.system.exitProcess

object CompilationFlow {
    fun compile(inputFile: File, outputFile: File, arch: TargetArchitecture, optimize: Boolean) {
        val (tokens, treeSupplier) = ParserFlow.parseFile(inputFile)
        try {
            val tree = treeSupplier.get()
            val (ir, sourceMap) = FrontendCompilationFlow.compileToIR(tree)
            val cfg = FrontendCompilationFlow.buildCFG(ir, sourceMap)
            val ssa = FrontendCompilationFlow.buildSSA(cfg)
            val optimizedSSA = if (optimize) {
                FrontendCompilationFlow.optimizeSSA(ssa).map { it.value.optimizedSSA }
            } else ssa
            val nonSSA = FrontendCompilationFlow.convertFromSSA(optimizedSSA)

            when (arch) {
                TargetArchitecture.ARM64 -> {
                    Arm64CompilationFlow.compileToAsm(nonSSA, outputFile)
                }
            }.let { /* exhaustive check */ }
        } catch (e: CompilationFailed) {
            e.printErrors(tokens)
            exitProcess(1)
        }
    }
}