import compiler.frontend.CompilationFailed
import compiler.frontend.CompileToIRVisitor
import compiler.frontend.SemanticAnalysisVisitor
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.print
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import parser.UnderlineErrorListener
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val input = File(args[0]).readText()
    val lexer = MainLexer(CharStreams.fromString(input))
    val tokens = CommonTokenStream(lexer)
    val parser = MainGrammar(tokens).apply {
        removeErrorListeners()
        addErrorListener(UnderlineErrorListener())
    }
    val tree = parser.program()
    if (parser.numberOfSyntaxErrors > 0) {
        exitProcess(1)
    }

    try {
        SemanticAnalysisVisitor().analyze(tree)
        cfg.print()
        val (ir, sourceMap) = CompileToIRVisitor().compileToIR(tree)
        val cfg = ControlFlowGraph.build(ir, sourceMap)
    } catch (e: CompilationFailed) {
        e.printErrors(tokens)
        exitProcess(1)
    }
}