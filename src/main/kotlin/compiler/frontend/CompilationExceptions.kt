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
            val location = exception.location
            if (location != null) {
                out.println("line ${location.line}:${location.start} ${exception.message}")
                ErrorPrinter.printError(tokens, location.line, location.start, location.end, out)
            } else {
                out.println("[unknown location] ${exception.message}")
            }
        }
    }
}

abstract class CompilationException(val location: SourceLocation?, message: String) : Exception(message)

class SyntaxErrorException(location: SourceLocation, message: String) : CompilationException(location, message)

class MismatchedTypeException(location: SourceLocation, expectedTypes: List<FrontendType>, actualType: FrontendType)
    : CompilationException(location, getErrorMessage(expectedTypes, actualType)) {

    constructor(location: SourceLocation, expectedType: FrontendType, actualType: FrontendType)
            : this(location, listOf(expectedType), actualType)

    companion object {
        private fun getErrorMessage(expectedTypes: List<FrontendType>, actualType: FrontendType): String {
            expectedTypes.singleOrNull()?.let { expectedType ->
                return "Expected type '$expectedType', got '$actualType'"
            }
            val expectedTypesStr = expectedTypes.joinToString(separator = " or ") { "'$it'" }
            return "Mismatched types: expected $expectedTypesStr, got $actualType"
        }
    }
}

class UndefinedVariableException(location: SourceLocation, name: String) : CompilationException(location, "Undefined variable '$name'")

// TODO store ctx in SymbolTable and show in exception when the `name` was originally declared
class VariableRedeclarationException(location: SourceLocation, name: String) : CompilationException(location, "Variable '$name' was already declared")

class FunctionRedeclarationException(location: SourceLocation, name: String) : CompilationException(location, "Function '$name' was already declared")

class UninitializedVariableException(location: SourceLocation?, name: String) : CompilationException(location, "Variable '$name' is used before being initialized")

class MalformedNumberException(location: SourceLocation, numberType: String, value: String) : CompilationException(location, "Malformed $numberType '$value'")

class UnknownTypeException(location: SourceLocation, name: String) : CompilationException(location, "Unknown type '$name'")

class MissingReturnException(location: SourceLocation, functionName: String)
    : CompilationException(location, "Missing return statement in function '$functionName'")

// ------------ exception context ------------

class SourceLocation(val line: Int, val start: Int, val end: Int) {
    companion object {
        fun fromParserContext(ctx: ParserRuleContext): SourceLocation {
            val line = ctx.start.line
            val start = ctx.start.charPositionInLine
            val end = if (ctx.stop.line == line) {
                ctx.stop.charPositionInLine + ctx.stop.stopIndex - ctx.stop.startIndex
            } else {
                ctx.start.charPositionInLine + ctx.start.stopIndex - ctx.start.startIndex
            }
            return SourceLocation(line, start, end)
        }

        fun fromToken(token: Token): SourceLocation {
            val line = token.line
            val start = token.charPositionInLine
            val end = start + token.stopIndex - token.startIndex
            return SourceLocation(line, start, end)
        }
    }
}

fun ParserRuleContext.asLocation() = SourceLocation.fromParserContext(this)

fun TerminalNode.asLocation() = SourceLocation.fromToken(symbol)

fun Token.asLocation() = SourceLocation.fromToken(this)