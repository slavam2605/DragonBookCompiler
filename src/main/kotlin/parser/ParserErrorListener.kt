package parser

import compiler.frontend.CompilationFailed
import compiler.frontend.SourceLocation
import compiler.frontend.SyntaxErrorException
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.FailedPredicateException
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token

class ParserErrorListener : BaseErrorListener() {
    private val errors = mutableListOf<SyntaxErrorException>()

    override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                             charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val token = offendingSymbol as Token
        val endPositionInLine = token.charPositionInLine + token.stopIndex - token.startIndex
        errors.add(SyntaxErrorException(
            SourceLocation(line, token.charPositionInLine, endPositionInLine),
            prepareMessage(token, msg, e)
        ))
    }

    fun throwIfAnyErrors() {
        if (errors.isEmpty()) return
        throw CompilationFailed(errors)
    }

    private fun prepareMessage(token: Token, msg: String?, e: RecognitionException?): String {
        if (e is FailedPredicateException) {
            if (e.predicate.contains("ParserUtils.isEndOfStatement")) {
                return "Unexpected token '${token.text}' (use ';' to separate statements on the same line)"
            }
        }
        return msg ?: "Unknown error"
    }
}