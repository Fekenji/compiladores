lexer grammar LALexer;

// Espaços e comentários (que serão descartados)
WS : [ \t\r\n]+ -> skip ;                // Ignora espaços em branco e quebras de linha
COMENTARIO : '{' ~[\r\n}]* '}' -> skip ; // Ignora comentários válidos entre chaves

// Palavras-chave e tipos
ALGORITMO : 'algoritmo' ;
DECLARE : 'declare' ;
FIM_ALGORITMO : 'fim_algoritmo' ;
LITERAL : 'literal' ;
INTEIRO : 'inteiro' ;
REAL : 'real' ;
LOGICO : 'logico' ;
CARACTERE : 'caractere' ;
LEIA : 'leia' ;
ESCREVA : 'escreva' ;
SE : 'se' ;
ENTAO : 'entao' ;
SENAO : 'senao' ;
FIM_SE : 'fim_se' ;
CASO : 'caso' ;
SEJA : 'seja' ;
FIM_CASO : 'fim_caso' ;
PARA : 'para' ;
ATE : 'ate' ;
FACA : 'faca' ;
FIM_PARA : 'fim_para' ;
ENQUANTO : 'enquanto' ;
FIM_ENQUANTO : 'fim_enquanto' ;
TIPO : 'tipo' ;
REGISTRO : 'registro' ;
FIM_REGISTRO : 'fim_registro' ;
PROCEDIMENTO : 'procedimento' ;
FIM_PROCEDIMENTO : 'fim_procedimento' ;
FUNCAO : 'funcao' ;
FIM_FUNCAO : 'fim_funcao' ;
RETORNE : 'retorne' ;
VERDADEIRO : 'verdadeiro' ;
FALSO : 'falso' ;
NAO : 'nao' ;
E : 'e' ;
OU : 'ou' ;
CONSTANTE : 'constante' ;
VAR : 'var' ;

// Símbolos e operadores
DOIS_PONTOS : ':' ;
VIRGULA : ',' ;
ABRE_PAR : '(' ;
FECHA_PAR : ')' ;
ABRE_COL : '[' ;
FECHA_COL : ']' ;
PONTO_PONTO : '..' ;
PONTO : '.' ;
ATRIBUICAO : '<-' ;
IGUAL : '=' ;
DIFERENTE : '<>' ;
MENOR_IGUAL : '<=' ;
MAIOR_IGUAL : '>=' ;
MENOR : '<' ;
MAIOR : '>' ;
MAIS : '+' ;
MENOS : '-' ;
MULT : '*' ;
DIV : '/' ;
CIRCUNFLEXO : '^' ;
MODULO : '%' ;
ENDERECO : '&' ;

// Tokens dinâmicos
NUM_INT : [0-9]+ ;                  // Números inteiros
NUM_REAL : [0-9]+ '.' [0-9]+ ;      // Números reais (separados por ponto)
IDENT : [a-zA-Z] [a-zA-Z0-9_]* ;    // Identificadores (começam com letra, seguidos de letras, números ou '_')
CADEIA : '"' ~[\r\n"]* '"' ;        // Strings delimitadas por aspas duplas

// Tratamento de erros
ERRO_CADEIA : '"' ~[\r\n"]* ;        // Cadeia que abriu com aspas, mas não fechou na mesma linha
ERRO_COMENTARIO : '{' ~[\r\n}]* ;    // Comentário que abriu com chave, mas não fechou na mesma linha
ERRO_SIMBOLO : . ;                   // Captura qualquer caractere que não casou com NADA acima