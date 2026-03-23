package br.ufscar.dc.compiladores.la.lexico;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class AnalisadorLexico {
    public static void main(String[] args) {
        // Valida se os dois argumentos obrigatórios foram passados (entrada e saída)
        if (args.length < 2) {
            System.out.println("Uso: java -jar <caminho_programa> <arquivo_entrada> <arquivo_saida>");
            return;
        }

        String arquivoEntrada = args[0];
        String arquivoSaida = args[1];

        // Tenta abrir o arquivo de saída para escrita
        try (PrintWriter pw = new PrintWriter(new File(arquivoSaida))) {
            // Lê o arquivo de entrada
            CharStream cs = CharStreams.fromFileName(arquivoEntrada);
            LALexer lexer = new LALexer(cs);

            Token t = null;
            // Pede os tokens um por um até o fim do arquivo (EOF)
            while ((t = lexer.nextToken()).getType() != Token.EOF) {
                String nomeToken = LALexer.VOCABULARY.getDisplayName(t.getType());
                String textoToken = t.getText();

                // Tratamento de Erros Léxicos (Interrompe a execução após o primeiro erro)
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

                // Formatação de Tokens Válidos
                if (nomeToken.equals("IDENT") || nomeToken.equals("CADEIA") || nomeToken.equals("NUM_INT")
                        || nomeToken.equals("NUM_REAL")) {
                    // Tokens dinâmicos: lexema entre aspas simples, nome do token sem aspas
                    pw.println("<'" + textoToken + "'," + nomeToken + ">");
                } else {
                    // Palavras-chave e Símbolos (Ex: <'algoritmo','algoritmo'> ou <':',':'>)
                    pw.println("<'" + textoToken + "','" + textoToken + "'>");
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao manipular os arquivos: " + e.getMessage());
        }
    }
}