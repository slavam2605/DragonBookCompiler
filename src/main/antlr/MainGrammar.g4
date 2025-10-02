parser grammar MainGrammar;

options { tokenVocab = MainLexer; }

@header {
    import parser.ParserUtils;
}

program
    : function+ EOF
    ;

function
    : FUN ID LPAR functionParameters? RPAR (ARROW type)? block
    ;

functionParameters
    : functionParameter (COMMA functionParameter)*
    ;

functionParameter
    : type ID
    ;

statement
    : declaration end
    | assignment end
    | functionCall end
    | breakStatement end
    | continueStatement end
    | returnStatement end
    | ifStatement
    | whileStatement
    | doWhileStatement
    | forStatement
    | block
    ;

functionCall
    : ID LPAR callArguments? RPAR
    ;

callArguments
    : expression (COMMA expression)*
    ;

block
    : LBRACE statement* RBRACE
    ;

declaration
    : type ID
    | type ID ASSIGN expression
    ;

type
    : ID
    ;

assignment
    : ID op=(ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | DIV_ASSIGN | MOD_ASSIGN) expression
    ;

ifStatement
    : IF LPAR expression RPAR ifTrue=statement (ELSE ifFalse=statement)?
    ;

whileStatement
    : WHILE LPAR expression RPAR statement
    ;

doWhileStatement
    : DO statement WHILE LPAR expression RPAR end
    ;

forStatement
    : FOR LPAR (initDecl=declaration | initAssign=assignment)? SEMICOLON cond=expression? SEMICOLON inc=assignment? RPAR statement
    ;

breakStatement
    : BREAK
    ;

continueStatement
    : CONTINUE
    ;

returnStatement
    : RETURN (noLineBreaks expression)?
    ;

expression
    : LPAR expression RPAR                                      # ParenExpr
    | ID                                                        # IdExpr
    | TRUE                                                      # TrueExpr
    | FALSE                                                     # FalseExpr
    | MINUS? INT_LITERAL                                        # IntExpr
    | MINUS? FLOAT_LITERAL                                      # FloatExpr
    | MINUS expression                                          # NegExpr
    | NOT expression                                            # NotExpr
    | expression AS type                                        # CastExpr
    | left=expression op=(STAR | DIV | MOD) right=expression    # MulDivExpr
    | left=expression op=(PLUS | MINUS) right=expression        # AddSubExpr
    | left=expression op=comparisonOp right=expression          # ComparisonExpr
    | left=expression AND right=expression                      # AndExpr
    | left=expression OR right=expression                       # OrExpr
    | functionCall                                              # CallExpr
    ;

/* ------------- Helper and synthetic rules ------------- */

// Helper rule for comparison operators
comparisonOp : LESS | GREATER | LESS_EQUAL | GREATER_EQUAL | EQUAL | NOT_EQUAL ;

// Helper rule for a statement terminator
end : SEMICOLON | EOF | isEndOfStatement ;

// Synthetic rule that checks if there is a line break on a separate lexer channel or another end token
isEndOfStatement : { ParserUtils.isEndOfStatement(_input) }? ;

// Synthetic rule that checks that no line breaks were skipped between previous and current tokens
noLineBreaks : { ParserUtils.noLineBreaks(_input) }? ;