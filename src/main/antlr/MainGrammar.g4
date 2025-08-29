parser grammar MainGrammar;

options { tokenVocab = MainLexer; }

@header {
    import parser.ParserUtils;
}

program
    : statement+ EOF
    ;

statement
    : declaration end
    | assignment end
    | functionCall end
    | breakStatement end
    | continueStatement end
    | ifStatement
    | whileStatement
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
    : ID ASSIGN expression
    ;

ifStatement
    : IF LPAR expression RPAR ifTrue=statement (ELSE ifFalse=statement)?
    ;

whileStatement
    : WHILE LPAR expression RPAR statement
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

expression
    : LPAR expression RPAR                                      # ParenExpr
    | NOT expression                                            # NotExpr
    | left=expression op=(STAR | DIV | MOD) right=expression    # MulDivExpr
    | left=expression op=(PLUS | MINUS) right=expression        # AddSubExpr
    | left=expression op=comparisonOp right=expression          # ComparisonExpr
    | left=expression AND right=expression                      # AndExpr
    | left=expression OR right=expression                       # OrExpr
    | functionCall                                              # CallExpr
    | ID                                                        # IdExpr
    | INT_LITERAL                                               # IntExpr
    | TRUE                                                      # TrueExpr
    | FALSE                                                     # FalseExpr
    | UNDEF                                                     # UndefExpr
    ;

/* ------------- Helper and synthetic rules ------------- */

// Helper rule for comparison operators
comparisonOp : LESS | GREATER | LESS_EQUAL | GREATER_EQUAL | EQUAL | NOT_EQUAL ;

// Helper rule for a statement terminator
end : SEMICOLON | EOF | isEndOfStatement ;

// Synthetic rule that checks if there is a line break on a separate lexer channel or another end token
isEndOfStatement : { ParserUtils.isEndOfStatement(_input) }? ;