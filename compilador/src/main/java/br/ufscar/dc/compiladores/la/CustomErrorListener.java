package br.ufscar.dc.compiladores.la;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import java.io.PrintWriter;

// Listener customizado para interceptar erros léxicos e sintáticos do ANTLR
public class CustomErrorListener extends BaseErrorListener {
    private PrintWriter pw;
    private boolean errorFound = false; // Flag para garantir que o compilador pare no primeiro erro

    public CustomErrorListener(PrintWriter pw) {
        this.pw = pw;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        if (errorFound) return; // Se um erro já foi relatado, ignora os subsequentes

        Token t = (Token) offendingSymbol;
        String text = t.getText();

        // Tratamento especial para o final do arquivo
        if (text.equals("<EOF>")) {
            text = "EOF";
        }

        // Identifica a regra violada para classificar o tipo do erro
        String tokenName = LALexer.VOCABULARY.getSymbolicName(t.getType());

        // Formatação das mensagens de erro conforme os testes exigidos
        if ("ERRO_SIMBOLO".equals(tokenName)) {
            pw.println("Linha " + line + ": " + text + " - simbolo nao identificado");
        } else if ("ERRO_COMENTARIO".equals(tokenName)) {
            pw.println("Linha " + line + ": comentario nao fechado");
        } else if ("ERRO_CADEIA".equals(tokenName)) {
            pw.println("Linha " + line + ": cadeia literal nao fechada");
        } else {
            // Se não for erro léxico específico, trata como erro sintático padrão
            pw.println("Linha " + line + ": erro sintatico proximo a " + text);
        }

        pw.println("Fim da compilacao");
        errorFound = true;

        // Dispara uma exceção para forçar o ANTLR a abortar o parsing imediatamente
        throw new RuntimeException("ParseError");
    }
}