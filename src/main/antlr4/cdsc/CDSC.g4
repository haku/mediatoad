/* UPnP-av-ContentDirectory-v4-Service-20101231.pdf */
/* pages 68 */
/* CDCS ~ Content Directory Search Criteria */

grammar CDSC;

searchCrit   : searchExp
             | asterisk
             ;

searchExp    : relExp
             | searchExp WChar+ logOp WChar+ searchExp
             | '(' WChar* searchExp WChar* ')'
             ;

logOp        : 'and'
             | 'or'
             ;

relExp       : Property WChar+ binOp WChar+ QuotedVal
             | Property WChar+ existsOp WChar+ boolVal
             ;

binOp        : relOp
             | stringOp
             ;

relOp        : '='
             | '!='
             | '<'
             | '<='
             | '>'
             | '>='
             ;

stringOp     : 'contains'
             | 'doesNotContain'
             | 'derivedfrom'
             | 'startsWith'
             | 'derivedFrom'
             ;

existsOp     : 'exists'
             ;

boolVal      : 'true'
             | 'false'
             ;

QuotedVal    : '"' ('\\"' | ~["\\])* '"'
             ;

WChar        : [ \t\n\r]
             ;

Property     : [a-zA-Z_:@.]+
             ;

asterisk     : '*'
             ;
