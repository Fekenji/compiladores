package br.ufscar.dc.compiladores.la;

import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.TipoLA;
import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.EntradaTabelaDeSimbolos;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Classe utilitária para o analisador semântico da linguagem LA. */
public class LASemanticoUtils {

    public static List<String> errosSemanticos = new ArrayList<>();

    // Mapa de nome de função/procedimento → lista de tipos dos parâmetros formais
    public static Map<String, List<TipoLA>> parametrosFuncoes = new HashMap<>();

    public static void adicionarErroSemantico(Token t, String mensagem) {
        int linha = t.getLine();
        errosSemanticos.add(String.format("Linha %d: %s", linha, mensagem));
    }

    public static TipoLA getTipoPorNome(String nomeDoTipo) {
        switch (nomeDoTipo) {
            case "inteiro": return TipoLA.INTEIRO;
            case "real":    return TipoLA.REAL;
            case "literal": return TipoLA.LITERAL;
            case "logico":  return TipoLA.LOGICO;
            default:        return TipoLA.NAO_DECLARADO;
        }
    }

    public static boolean ehTipoBasico(String nome) {
        return nome.equals("inteiro") || nome.equals("real")
            || nome.equals("literal") || nome.equals("logico");
    }

    public static EntradaTabelaDeSimbolos buscarEscopos(Escopo escopos, String nome) {
        for (TabelaDeSimbolos tabela : escopos.obterTodosEscopos()) {
            if (tabela.existe(nome)) {
                return tabela.verificar(nome);
            }
        }
        return null;
    }

    public static boolean existeEmAlgumEscopo(Escopo escopos, String nome) {
        return buscarEscopos(escopos, nome) != null;
    }

    public static boolean tiposCompativeis(TipoLA tipoEsquerda, TipoLA tipoDireita) {
        if (tipoEsquerda == TipoLA.INVALIDO || tipoDireita == TipoLA.INVALIDO
         || tipoEsquerda == TipoLA.NAO_DECLARADO || tipoDireita == TipoLA.NAO_DECLARADO) {
            return false;
        }
        if (tipoEsquerda == TipoLA.PONTEIRO && tipoDireita == TipoLA.ENDERECO) {
            return true;
        }
        if (ehNumerico(tipoEsquerda) && ehNumerico(tipoDireita)) {
            return true;
        }
        return tipoEsquerda == tipoDireita;
    }

    public static boolean ehNumerico(TipoLA tipo) {
        return tipo == TipoLA.INTEIRO || tipo == TipoLA.REAL;
    }

    public static TipoLA inferirTipoOperacao(TipoLA tipo1, TipoLA tipo2) {
        if (tipo1 == TipoLA.INVALIDO || tipo2 == TipoLA.INVALIDO) {
            return TipoLA.INVALIDO;
        }
        if (tipo1 == TipoLA.NAO_DECLARADO || tipo2 == TipoLA.NAO_DECLARADO) {
            return TipoLA.INVALIDO;
        }
        if (ehNumerico(tipo1) && ehNumerico(tipo2)) {
            if (tipo1 == TipoLA.REAL || tipo2 == TipoLA.REAL) {
                return TipoLA.REAL;
            }
            return TipoLA.INTEIRO;
        }
        if (tipo1 == TipoLA.LITERAL && tipo2 == TipoLA.LITERAL) {
            return TipoLA.LITERAL;
        }
        return TipoLA.INVALIDO;
    }

    /** Verifica compatibilidade estrita de tipos para passagem de parâmetros. */
    public static boolean tiposCompativeisParametro(TipoLA tipoParam, TipoLA tipoArg) {
        if (tipoArg == TipoLA.INVALIDO || tipoArg == TipoLA.NAO_DECLARADO) return true; // skip
        if (tipoParam == TipoLA.INVALIDO || tipoParam == TipoLA.NAO_DECLARADO) return false;
        if (tipoParam == TipoLA.PONTEIRO && tipoArg == TipoLA.ENDERECO) return true;
        return tipoParam == tipoArg;
    }

    /** Verifica se os argumentos são compatíveis com os parâmetros formais. */
    public static boolean parametrosCompativeis(List<TipoLA> tiposParams, List<TipoLA> tiposArgs) {
        if (tiposParams.size() != tiposArgs.size()) return false;
        for (int i = 0; i < tiposParams.size(); i++) {
            if (!tiposCompativeisParametro(tiposParams.get(i), tiposArgs.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ==================== Inferência de tipo ====================

    public static TipoLA inferirTipo(Escopo escopos, LAParser.ExpressaoContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.termo_logico(0));
        for (int i = 1; i < ctx.termo_logico().size(); i++) {
            tipo = TipoLA.LOGICO;
        }
        return tipo;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Termo_logicoContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.fator_logico(0));
        for (int i = 1; i < ctx.fator_logico().size(); i++) {
            tipo = TipoLA.LOGICO;
        }
        return tipo;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Fator_logicoContext ctx) {
        return inferirTipo(escopos, ctx.parcela_logica());
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Parcela_logicaContext ctx) {
        if (ctx.getText().equals("verdadeiro") || ctx.getText().equals("falso")) {
            return TipoLA.LOGICO;
        }
        return inferirTipo(escopos, ctx.exp_relacional());
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Exp_relacionalContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.exp_aritmetica(0));
        if (ctx.op_relacional() != null) {
            inferirTipo(escopos, ctx.exp_aritmetica(1));
            return TipoLA.LOGICO;
        }
        return tipo;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Exp_aritmeticaContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.termo(0));
        for (int i = 1; i < ctx.termo().size(); i++) {
            TipoLA tipoTermo = inferirTipo(escopos, ctx.termo(i));
            tipo = inferirTipoOperacao(tipo, tipoTermo);
        }
        return tipo;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.TermoContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.fator(0));
        for (int i = 1; i < ctx.fator().size(); i++) {
            TipoLA tipoFator = inferirTipo(escopos, ctx.fator(i));
            tipo = inferirTipoOperacao(tipo, tipoFator);
        }
        return tipo;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.FatorContext ctx) {
        TipoLA tipo = inferirTipo(escopos, ctx.parcela(0));
        for (int i = 1; i < ctx.parcela().size(); i++) {
            TipoLA tipoParcela = inferirTipo(escopos, ctx.parcela(i));
            tipo = inferirTipoOperacao(tipo, tipoParcela);
        }
        return tipo;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.ParcelaContext ctx) {
        if (ctx.parcela_unario() != null) {
            return inferirTipo(escopos, ctx.parcela_unario());
        }
        return inferirTipo(escopos, ctx.parcela_nao_unario());
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Parcela_unarioContext ctx) {
        // Caso 1: identificador (com possível ^)
        if (ctx.identificador() != null) {
            String nomeBase = ctx.identificador().IDENT(0).getText();
            // Construir nome completo para erros e lookup
            StringBuilder sbNome = new StringBuilder(nomeBase);
            for (int i = 1; i < ctx.identificador().IDENT().size(); i++) {
                sbNome.append(".").append(ctx.identificador().IDENT(i).getText());
            }
            String nomeCompleto = sbNome.toString();

            EntradaTabelaDeSimbolos entrada = buscarEscopos(escopos, nomeBase);
            if (entrada == null) {
                adicionarErroSemantico(ctx.identificador().IDENT(0).getSymbol(),
                    "identificador " + nomeCompleto + " nao declarado");
                return TipoLA.NAO_DECLARADO;
            }
            if (ctx.identificador().IDENT().size() > 1) {
                EntradaTabelaDeSimbolos entradaCompleta = buscarEscopos(escopos, nomeCompleto);
                if (entradaCompleta != null) {
                    return entradaCompleta.tipo;
                }
                // Campo não encontrado mas base existe: retornar tipo base
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
            // Inferir tipos dos argumentos
            List<TipoLA> tiposArgs = new ArrayList<>();
            for (LAParser.ExpressaoContext expr : ctx.expressao()) {
                tiposArgs.add(inferirTipo(escopos, expr));
            }
            // Verificar compatibilidade com parâmetros formais
            List<TipoLA> tiposParams = parametrosFuncoes.get(nomeFuncao);
            if (tiposParams != null && !parametrosCompativeis(tiposParams, tiposArgs)) {
                adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "incompatibilidade de parametros na chamada de " + nomeFuncao);
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

        // Caso 5: '(' expressao ')'
        if (ctx.expressao() != null && !ctx.expressao().isEmpty()) {
            return inferirTipo(escopos, ctx.expressao(0));
        }

        return TipoLA.INVALIDO;
    }

    public static TipoLA inferirTipo(Escopo escopos, LAParser.Parcela_nao_unarioContext ctx) {
        if (ctx.identificador() != null) {
            return TipoLA.ENDERECO;
        }
        if (ctx.CADEIA() != null) {
            return TipoLA.LITERAL;
        }
        return TipoLA.INVALIDO;
    }
}
