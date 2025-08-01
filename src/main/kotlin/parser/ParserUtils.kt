package org.example.parser

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.TokenStream

object ParserUtils {
    @JvmStatic
    fun hasNewLine(input: TokenStream): Boolean {
        check(input is BufferedTokenStream)
        val lineBreaks = input.getHiddenTokensToLeft(input.index(), MainLexer.LINE_BREAK)
        return lineBreaks?.isNotEmpty() ?: false
    }
}