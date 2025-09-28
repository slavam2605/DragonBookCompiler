package parser

import MainLexer
import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.TokenStream

object ParserUtils {
    private val endTokens = setOf(MainLexer.RBRACE, MainLexer.RPAR, MainLexer.ELSE)

    @JvmStatic
    fun isEndOfStatement(input: TokenStream): Boolean {
        check(input is BufferedTokenStream)
        if (hasLineBreak(input)) return true

        // Check for obvious "end" tokens
        return input.LA(1) in endTokens
    }

    @JvmStatic
    fun noLineBreaks(input: TokenStream): Boolean {
        check(input is BufferedTokenStream)
        return !hasLineBreak(input)
    }

    private fun hasLineBreak(input: BufferedTokenStream): Boolean {
        // Try to find an explicit line break
        val lineBreaks = input.getHiddenTokensToLeft(input.index(), MainLexer.LINE_BREAK)
        if (lineBreaks?.isNotEmpty() == true) {
            return true
        }

        // Try to find a block comment with a line break inside
        val comments = input.getHiddenTokensToLeft(input.index(), MainLexer.COMMENTS)
        comments?.forEach { token ->
            if (token.text.contains('\n')) {
                return true
            }
        }

        return false
    }
}