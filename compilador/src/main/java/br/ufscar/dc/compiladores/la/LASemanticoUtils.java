package br.ufscar.dc.compiladores.la;

import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.TipoLA;
import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.EntradaTabelaDeSimbolos;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/** Classe utilitária para o analisador semântico da linguagem LA. */
public class LASemanticoUtils {

    // Lista global de erros semânticos encontrados durante a análise
    public static List<String> errosSemanticos = new ArrayList<>();

    /** Adiciona um erro semântico à lista de erros. */
    public static void adicionarErroSemantico(Token t, String mensagem) {
        int linha = t.getLine();
        errosSemanticos.add(String.format("Linha %d: %s", linha, mensagem));
    }

    /** Converte o nome textual de um tipo básico para o enum TipoLA. */
    public static TipoLA getTipoPorNome(String nomeDoTipo) {
        switch (nomeDoTipo) {
            case "inteiro": return TipoLA.INTEIRO;
            case "real":    return TipoLA.REAL;
            case "literal": return TipoLA.LITERAL;
            case "logico":  return TipoLA.LOGICO;
            default:        return TipoLA.NAO_DECLARADO;
        }
    }

    /** Verifica se um nome corresponde a um tipo básico da linguagem LA. */
    public static boolean ehTipoBasico(String nome) {
        return nome.equals("inteiro") || nome.equals("real")
            || nome.equals("literal") || nome.equals("logico");
    }

    /** Busca um identificador em todos os escopos (do mais interno ao mais externo). */
    public static EntradaTabelaDeSimbolos buscarEscopos(Escopo escopos, String nome) {
        for (TabelaDeSimbolos tabela : escopos.obterTodosEscopos()) {
            if (tabela.existe(nome)) {
                return tabela.verificar(nome);
            }
        }
        return null;
    }

    /** Verifica se um identificador já foi declarado em algum escopo ativo. */
    public static boolean existeEmAlgumEscopo(Escopo escopos, String nome) {
        return buscarEscopos(escopos, nome) != null;
    }

    /** Verifica se dois tipos são compatíveis para atribuição. */
    public static boolean tiposCompativeis(TipoLA tipoEsquerda, TipoLA tipoDireita) {
        // Tipos inválidos ou não declarados são sempre incompatíveis
        if (tipoEsquerda == TipoLA.INVALIDO || tipoDireita == TipoLA.INVALIDO
         || tipoEsquerda == TipoLA.NAO_DECLARADO || tipoDireita == TipoLA.NAO_DECLARADO) {
            return false;
        }

        // Ponteiro recebe endereço
        if (tipoEsquerda == TipoLA.PONTEIRO && tipoDireita == TipoLA.ENDERECO) {
            return true;
        }

        // Numéricos são compatíveis entre si
        if (ehNumerico(tipoEsquerda) && ehNumerico(tipoDireita)) {
            return true;
        }

        // Demais tipos devem ser iguais (literal=literal, logico=logico, etc.)
        return tipoEsquerda == tipoDireita;
    }

    /** Verifica se um tipo é numérico (inteiro ou real). */
    public static boolean ehNumerico(TipoLA tipo) {
        return tipo == TipoLA.INTEIRO || tipo == TipoLA.REAL;
    }

    /** Infere o tipo resultante de uma operação aritmética entre dois tipos numéricos. */
    public static TipoLA inferirTipoOperacao(TipoLA tipo1, TipoLA tipo2) {
        if (tipo1 == TipoLA.INVALIDO || tipo2 == TipoLA.INVALIDO) {
            return TipoLA.INVALIDO;
        }
        if (tipo1 == TipoLA.NAO_DECLARADO || tipo2 == TipoLA.NAO_DECLARADO) {
            return TipoLA.INVALIDO;
        }

        // Numéricos são compatíveis em operações aritméticas
        if (ehNumerico(tipo1) && ehNumerico(tipo2)) {
            if (tipo1 == TipoLA.REAL || tipo2 == TipoLA.REAL) {
                return TipoLA.REAL;
            }
            return TipoLA.INTEIRO;
        }

        // Literal + literal (concatenação) — embora raro em aritmética, é válido
        if (tipo1 == TipoLA.LITERAL && tipo2 == TipoLA.LITERAL) {
            return TipoLA.LITERAL;
        }

        // Tipos incompatíveis em operação aritmética
        return TipoLA.INVALIDO;
    }

    // ==================== Métodos de inferência de tipo para expressões ====================

    /** Infere o tipo de uma expressão completa (com operadores lógicos 'ou'). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.ExpressaoContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.termo_logico(0));
        for (int i = 1; i < ctx.termo_logico().size(); i++) {
            // Operador 'ou' requer operandos lógicos, resultado é lógico
            tipo = TipoLA.LOGICO;
        }
        return tipo;
    }

    /** Infere o tipo de um termo lógico (com operadores 'e'). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Termo_logicoContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.fator_logico(0));
        for (int i = 1; i < ctx.fator_logico().size(); i++) {
            tipo = TipoLA.LOGICO;
        }
        return tipo;
    }

    /** Infere o tipo de um fator lógico (com possível 'nao'). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Fator_logicoContext ctx) {
        return inferirTipo(escopos, ctx.parcela_logica());
    }

    /** Infere o tipo de uma parcela lógica ('verdadeiro'/'falso' ou expressão relacional). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Parcela_logicaContext ctx) {
        if (ctx.getText().equals("verdadeiro") || ctx.getText().equals("falso")) {
            return TipoLA.LOGICO;
        }
        return inferirTipo(escopos, ctx.exp_relacional());
    }

    /** Infere o tipo de uma expressão relacional. */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Exp_relacionalContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.exp_aritmetica(0));
        if (ctx.op_relacional() != null) {
            // Presença de operador relacional → resultado é lógico
            // Mas ainda precisamos verificar o tipo do segundo operando
            inferirTipo(escopos, ctx.exp_aritmetica(1));
            return TipoLA.LOGICO;
        }
        return tipo;
    }

    /** Infere o tipo de uma expressão aritmética (com + e -). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Exp_aritmeticaContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.termo(0));
        for (int i = 1; i < ctx.termo().size(); i++) {
            TipoLA tipoTermo = inferirTipo(escopos, ctx.termo(i));
            tipo = inferirTipoOperacao(tipo, tipoTermo);
        }
        return tipo;
    }

    /** Infere o tipo de um termo (com  e /). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.TermoContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.fator(0));
        for (int i = 1; i < ctx.fator().size(); i++) {
            TipoLA tipoFator = inferirTipo(escopos, ctx.fator(i));
            tipo = inferirTipoOperacao(tipo, tipoFator);
        }
        return tipo;
    }

    /** Infere o tipo de um fator (com %). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.FatorContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.parcela(0));
        for (int i = 1; i < ctx.parcela().size(); i++) {
            TipoLA tipoParcela = inferirTipo(escopos, ctx.parcela(i));
            tipo = inferirTipoOperacao(tipo, tipoParcela);
        }
        return tipo;
    }

    /** Infere o tipo de uma parcela (unária ou não-unária). */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.ParcelaContext ctx) {
        if (ctx.parcela_unario() != null) {
            return inferirTipo(escopos, ctx.parcela_unario());
        }
        return inferirTipo(escopos, ctx.parcela_nao_unario());
    }

    /** Infere o tipo de uma parcela unária: identificador, chamada de função, */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Parcela_unarioContext ctx) {
        // Caso 1: identificador (com possível ^)
        if (ctx.identificador() != null) {
            String nomeId = ctx.identificador().IDENT(0).getText();
            EntradaTabelaDeSimbolos entrada = buscarEscopos(escopos, nomeId);
            if (entrada == null) {
                // Identificador não declarado — erro será reportado pelo visitor
                adicionarErroSemantico(ctx.identificador().IDENT(0).getSymbol(),
                    "identificador " + nomeId + " nao declarado");
                return TipoLA.NAO_DECLARADO;
            }
            // Se for acesso a campo de registro (ident.campo), retornar o tipo do campo
            if (ctx.identificador().IDENT().size() > 1) {
                String nomeCompleto = "";
                for (int i = 0; i < ctx.identificador().IDENT().size(); i++) {
                    if (i > 0) nomeCompleto += ".";
                    nomeCompleto += ctx.identificador().IDENT(i).getText();
                }
                EntradaTabelaDeSimbolos entradaCompleta = buscarEscopos(escopos, nomeCompleto);
                if (entradaCompleta != null) {
                    return entradaCompleta.tipo;
                }
            }
            return entrada.tipo;
        }

        // Caso 2: chamada de função IDENT '(' expressao ... ')'
        if (ctx.IDENT() != null) {
            String nomeFuncao = ctx.IDENT().getText();
            EntradaTabelaDeSimbolos entrada = buscarEscopos(escopos, nomeFuncao);
            if (entrada == null) {
                adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nomeFuncao + " nao declarado");
                return TipoLA.NAO_DECLARADO;
            }
            // Verificar tipos dos argumentos (inferir tipos para detectar erros internos)
            for (LAParser.ExpressaoContext expr : ctx.expressao()) {
                inferirTipo(escopos, expr);
            }
            return entrada.tipo;
        }

        // Caso 3: NUM_INT
        if (ctx.NUM_INT() != null) {
            return TipoLA.INTEIRO;
        }

        // Caso 4: NUM_REAL
        if (ctx.NUM_REAL() != null) {
            return TipoLA.REAL;
        }

        // Caso 5: '(' expressao ')' — expressão entre parênteses
        if (ctx.expressao() != null && !ctx.expressao().isEmpty()) {
            return inferirTipo(escopos, ctx.expressao(0));
        }

        return TipoLA.INVALIDO;
    }

    /** Infere o tipo de uma parcela não-unária: '&' identificador ou CADEIA. */
    public static TipoLA inferirTipo(Escopo escopos, LAParser.Parcela_nao_unarioContext ctx) {
        // '&' identificador → tipo ENDERECO
        if (ctx.identificador() != null) {
            return TipoLA.ENDERECO;
        }
        // CADEIA → tipo LITERAL
        if (ctx.CADEIA() != null) {
            return TipoLA.LITERAL;
        }
        return TipoLA.INVALIDO;
    }
}
