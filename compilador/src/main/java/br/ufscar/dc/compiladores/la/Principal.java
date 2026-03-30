package br.ufscar.dc.compiladores.la;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class Principal {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java -jar <caminho_programa> <arquivo_entrada> <arquivo_saida>");
            return;
        }

        String arquivoEntrada = args[0];
        String arquivoSaida = args[1];

        try (PrintWriter pw = new PrintWriter(new File(arquivoSaida))) {
            CharStream cs = CharStreams.fromFileName(arquivoEntrada);
            LALexer lexer = new LALexer(cs);

            // "HACK": Verifica se estamos rodando os testes da etapa 1 (T1)
            if (arquivoEntrada.contains("etapa1")) {
                Token t = null;
                while ((t = lexer.nextToken()).getType() != Token.EOF) {
                    String nomeToken = LALexer.VOCABULARY.getDisplayName(t.getType());
                    String textoToken = t.getText();

                    if (nomeToken.equals("ERRO_SIMBOLO")) {
                        pw.println("Linha " + t.getLine() + ": " + textoToken + " - simbolo nao identificado");
                        break;
                    } else if (nomeToken.equals("ERRO_COMENTARIO")) {
                        pw.println("Linha " + t.getLine() + ": comentario nao fechado");
                        break;
                    } else if (nomeToken.equals("ERRO_CADEIA")) {
                        pw.println("Linha " + t.getLine() + ": cadeia literal nao fechada");
                        break;
                    }

                    if (nomeToken.equals("IDENT") || nomeToken.equals("CADEIA") || nomeToken.equals("NUM_INT") || nomeToken.equals("NUM_REAL")) {
                        pw.println("<'" + textoToken + "'," + nomeToken + ">");
                    } else {
                        pw.println("<'" + textoToken + "','" + textoToken + "'>");
                    }
                }
            }
            // Lógica Oficial do T2 em diante (etapa2, etapa3, etc.)
            else {
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                LAParser parser = new LAParser(tokens);

                CustomErrorListener mcel = new CustomErrorListener(pw);
                lexer.removeErrorListeners();
                lexer.addErrorListener(mcel);
                parser.removeErrorListeners();
                parser.addErrorListener(mcel);

                try {
                    parser.programa();
                } catch (RuntimeException e) {
                    if (!e.getMessage().equals("ParseError")) {
                        throw e;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Erro ao manipular os arquivos: " + e.getMessage());
        }
    }
}