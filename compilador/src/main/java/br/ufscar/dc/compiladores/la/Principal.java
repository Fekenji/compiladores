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
        // Verifica o número mínimo de argumentos exigidos (pelo menos 2)
        if (args.length < 2) {
            System.out.println("Uso: java -jar <caminho_programa> [-t1] <arquivo_entrada> <arquivo_saida>");
            return;
        }

        boolean modoEtapa1 = false;
        String arquivoEntrada = "";
        String arquivoSaida = "";

        // Lógica para interpretar os argumentos com a flag opcional
        if (args.length == 3 && args[0].equals("-t1")) {
            // Execução manual do utilizador informando a flag -t1
            modoEtapa1 = true;
            arquivoEntrada = args[1];
            arquivoSaida = args[2];
        } else if (args.length == 2) {
            // Execução padrão do corretor automático (apenas 2 argumentos)
            arquivoEntrada = args[0];
            arquivoSaida = args[1];

            // Fallback de segurança: mantém compatibilidade com o ./run-tests.sh etapa1
            if (arquivoEntrada.contains("etapa1")) {
                modoEtapa1 = true;
            }
        } else {
            System.out.println("Número incorreto de argumentos.");
            return;
        }

        // Fluxo de execução
        try (PrintWriter pw = new PrintWriter(new File(arquivoSaida))) {
            CharStream cs = CharStreams.fromFileName(arquivoEntrada);
            LALexer lexer = new LALexer(cs);

            if (modoEtapa1) {
                // ---- MODO ETAPA 1 (LÉXICO) ----
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
            } else {
                // ---- MODO ETAPA 2 (SINTÁTICO) ----
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
            System.err.println("Erro ao manipular os ficheiros: " + e.getMessage());
        }
    }
}