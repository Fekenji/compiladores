package br.ufscar.dc.compiladores.la;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import java.io.PrintWriter;

public class CustomErrorListener extends BaseErrorListener {
    private PrintWriter pw;
    private boolean errorFound = false;

    public CustomErrorListener(PrintWriter pw) {
        this.pw = pw;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        if (errorFound) return;

        Token t = (Token) offendingSymbol;
        String text = t.getText();

        if (text.equals("<EOF>")) {
            text = "EOF";
        }

        String tokenName = LALexer.VOCABULARY.getSymbolicName(t.getType());

        if ("ERRO_SIMBOLO".equals(tokenName)) {
            pw.println("Linha " + line + ": " + text + " - simbolo nao identificado");
        } else if ("ERRO_COMENTARIO".equals(tokenName)) {
            pw.println("Linha " + line + ": comentario nao fechado");
        } else if ("ERRO_CADEIA".equals(tokenName)) {
            pw.println("Linha " + line + ": cadeia literal nao fechada");
        } else {
            pw.println("Linha " + line + ": erro sintatico proximo a " + text);
        }

        pw.println("Fim da compilacao");
        errorFound = true;

        throw new RuntimeException("ParseError");
    }
}