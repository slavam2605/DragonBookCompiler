package parser

import MainLexer
import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.TokenStream

object ParserUtils {
    private val endTokens = setOf(MainLexer.RBRACE, MainLexer.ELSE)

    @JvmStatic
    fun isEndOfStatement(input: TokenStream): Boolean {
        check(input is BufferedTokenStream)
        val lineBreaks = input.getHiddenTokensToLeft(input.index(), MainLexer.LINE_BREAK)
        if (lineBreaks?.isNotEmpty() == true) {
            return true
        }
        return input.LA(1) in endTokens
    }
}