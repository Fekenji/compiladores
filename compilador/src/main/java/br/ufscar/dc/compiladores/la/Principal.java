package br.ufscar.dc.compiladores.la;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/** Classe principal do compilador da linguagem LA. */
public class Principal {
    public static void main(String[] args) {
        // Verifica o número mínimo de argumentos exigidos (pelo menos 2)
        if (args.length < 2) {
            System.out.println("Uso: java -jar <caminho_programa> [-t1|-t2] <arquivo_entrada> <arquivo_saida>");
            return;
        }

        // Modos de execução: T1 (léxico), T2 (sintático), T3 (semântico, padrão)
        String modo = "t3"; // Padrão: análise semântica
        String arquivoEntrada = "";
        String arquivoSaida = "";

        // Lógica para interpretar os argumentos com a flag opcional
        if (args.length == 3 && (args[0].equals("-t1") || args[0].equals("-t2"))) {
            modo = args[0].substring(1); // "t1" ou "t2"
            arquivoEntrada = args[1];
            arquivoSaida = args[2];
        } else if (args.length == 2) {
            // Execução padrão do corretor automático (apenas 2 argumentos)
            arquivoEntrada = args[0];
            arquivoSaida = args[1];

            // Fallback de segurança: compatibilidade com ./run-tests.sh etapa1/etapa2
            if (arquivoEntrada.contains("1.casos_teste_t1") || arquivoEntrada.contains("etapa1")) {
                modo = "t1";
            } else if (arquivoEntrada.contains("2.casos_teste_t2") || arquivoEntrada.contains("etapa2")) {
                modo = "t2";
            }
        } else {
            System.out.println("Número incorreto de argumentos.");
            return;
        }

        // Fluxo de execução conforme o modo selecionado
        try (PrintWriter pw = new PrintWriter(new File(arquivoSaida))) {
            CharStream cs = CharStreams.fromFileName(arquivoEntrada);
            LALexer lexer = new LALexer(cs);

            if (modo.equals("t1")) {
                // ---- MODO ETAPA 1 (LÉXICO) ----
                executarModoLexico(lexer, pw);

            } else if (modo.equals("t2")) {
                // ---- MODO ETAPA 2 (SINTÁTICO) ----
                executarModoSintatico(lexer, pw);

            } else {
                // ---- MODO ETAPA 3 (SEMÂNTICO) — PADRÃO ----
                executarModoSemantico(lexer, pw);
            }

        } catch (IOException e) {
            System.err.println("Erro ao manipular os ficheiros: " + e.getMessage());
        }
    }

    /** Modo T1: Análise léxica — lista todos os tokens até EOF ou erro léxico. */
    private static void executarModoLexico(LALexer lexer, PrintWriter pw) {
        Token t;
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

            if (nomeToken.equals("IDENT") || nomeToken.equals("CADEIA")
                || nomeToken.equals("NUM_INT") || nomeToken.equals("NUM_REAL")) {
                pw.println("<'" + textoToken + "'," + nomeToken + ">");
            } else {
                pw.println("<'" + textoToken + "','" + textoToken + "'>");
            }
        }
    }

    /** Modo T2: Análise sintática — detecta o primeiro erro sintático e para. */
    private static void executarModoSintatico(LALexer lexer, PrintWriter pw) {
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

    /** Modo T3: Análise semântica — detecta erros semânticos sem interromper. */
    private static void executarModoSemantico(LALexer lexer, PrintWriter pw) {
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LAParser parser = new LAParser(tokens);

        // Configurar listeners para capturar erros léxicos/sintáticos
        CustomErrorListener mcel = new CustomErrorListener(pw);
        lexer.removeErrorListeners();
        lexer.addErrorListener(mcel);
        parser.removeErrorListeners();
        parser.addErrorListener(mcel);

        try {
            // Fase 1: Parsing (gera a árvore sintática)
            LAParser.ProgramaContext arvore = parser.programa();

            // Fase 2: Análise semântica (percorre a árvore com o visitor)
            LASemanticoUtils.errosSemanticos.clear();
            LASemanticoUtils.parametrosFuncoes.clear();
            LASemantico semantico = new LASemantico();
            semantico.visit(arvore);

            // Escrever todos os erros semânticos encontrados
            for (String erro : LASemanticoUtils.errosSemanticos) {
                pw.println(erro);
            }

        } catch (RuntimeException e) {
            // Se houve erro léxico/sintático, o CustomErrorListener já escreveu
            if (!e.getMessage().equals("ParseError")) {
                throw e;
            }
            return; // Não escrever "Fim da compilacao" novamente (já foi escrito pelo listener)
        }

        pw.println("Fim da compilacao");
    }
}