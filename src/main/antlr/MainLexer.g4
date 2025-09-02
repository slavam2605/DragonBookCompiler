lexer grammar MainLexer;

channels { COMMENTS, WHITESPACE, LINE_BREAK }

// Keywords
IF : 'if' ;
ELSE : 'else' ;
FOR : 'for' ;
WHILE : 'while' ;
BREAK : 'break' ;
CONTINUE : 'continue' ;
RETURN : 'return' ;
TRUE : 'true' ;
FALSE : 'false' ;
DO : 'do' ;

// Operators
ASSIGN : '=' ;
SEMICOLON : ';' ;
COMMA : ',' ;
STAR : '*' ;
DIV : '/' ;
MOD : '%' ;
PLUS : '+' ;
MINUS : '-' ;
LPAR : '(' ;
RPAR : ')' ;
LBRACE : '{' ;
RBRACE : '}' ;

// Comparison operators
EQUAL : '==' ;
NOT_EQUAL : '!=' ;
LESS : '<' ;
GREATER : '>' ;
LESS_EQUAL : '<=' ;
GREATER_EQUAL : '>=' ;

// Logical operators
AND : '&&' ;
OR : '||' ;
NOT : '!' ;

// Literals and identifiers
ID  : [a-zA-Z_][a-zA-Z0-9_]* ;
INT_LITERAL : [0-9]+ ;

// Whitespace and comments
WS  : [ \t]+ -> channel(WHITESPACE) ;
NEW_LINE : [\r\n]+ -> channel(LINE_BREAK) ;
COMMENT : '//' ~[\r\n]* -> channel(COMMENTS) ;
COMMENT_BLOCK : '/*' .*? '*/' -> channel(COMMENTS) ;