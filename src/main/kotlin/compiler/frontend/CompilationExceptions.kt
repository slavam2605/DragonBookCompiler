package compiler.frontend

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import utils.ErrorPrinter
import java.io.PrintStream

class CompilationFailed(val exceptions: List<CompilationException>) : Exception(
    "Compilation failed: ${exceptions.size} error${if (exceptions.size > 1) "s" else ""}"
) {
    fun printErrors(tokens: CommonTokenStream, out: PrintStream = System.err) {
        for (exception in exceptions) {
            val ctx = exception.ctx
            out.println("line ${ctx.line}:${ctx.start} ${exception.message}")
            ErrorPrinter.printError(tokens, ctx.line, ctx.start, ctx.end, out)
        }
    }
}

abstract class CompilationException(val ctx: ExceptionContext, message: String) : Exception(message)

class SyntaxErrorException(ctx: ExceptionContext, message: String) : CompilationException(ctx, message)

class MismatchedTypeException(ctx: ExceptionContext, expectedType: FrontendType, actualType: FrontendType)
    : CompilationException(ctx, "Expected type '$expectedType', got '$actualType'")

class UndefinedVariableException(ctx: ExceptionContext, name: String) : CompilationException(ctx, "Undefined variable '$name'")

// TODO store ctx in SymbolTable and show in exception when the `name` was originally declared
class VariableRedeclarationException(ctx: ExceptionContext, name: String) : CompilationException(ctx, "Variable '$name' was already declared")

class UnknownTypeException(ctx: ExceptionContext, name: String) : CompilationException(ctx, "Unknown type '$name'")

// ------------ exception context ------------

sealed class ExceptionContext {
    abstract val line: Int
    abstract val start: Int
    abstract val end: Int

    class ParserContext(ctx: ParserRuleContext) : ExceptionContext() {
        // TODO support multiline contexts
        override val line: Int = ctx.start.line
        override val start: Int = ctx.start.charPositionInLine
        override val end: Int = if (ctx.stop.line == line) {
            ctx.stop.charPositionInLine + ctx.stop.stopIndex - ctx.stop.startIndex
        } else {
            ctx.start.charPositionInLine + ctx.start.stopIndex - ctx.start.startIndex
        }
    }

    class TokenContext(token: Token) : ExceptionContext() {
        override val line: Int = token.line
        override val start: Int = token.charPositionInLine
        override val end: Int = token.charPositionInLine + token.stopIndex - token.startIndex
    }
}

fun ParserRuleContext.asContext() = ExceptionContext.ParserContext(this)

fun TerminalNode.asContext() = ExceptionContext.TokenContext(symbol)

fun Token.asContext() = ExceptionContext.TokenContext(this)