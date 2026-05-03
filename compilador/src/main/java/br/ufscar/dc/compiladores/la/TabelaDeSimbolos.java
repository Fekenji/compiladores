package br.ufscar.dc.compiladores.la;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tabela de símbolos para o analisador semântico. */
public class TabelaDeSimbolos {

    /** Enumeração dos tipos possíveis na linguagem LA. */
    public enum TipoLA {
        INTEIRO,
        REAL,
        LITERAL,
        LOGICO,
        PONTEIRO,
        ENDERECO,
        REGISTRO,
        INVALIDO,       // Tipo inválido
        NAO_DECLARADO   // Tipo não declarado
    }

    /** Entrada na tabela de símbolos. */
    public static class EntradaTabelaDeSimbolos {
        String nome;
        TipoLA tipo;
        String nomeTipo; // Nome do tipo original

        /** Construtor de entrada da tabela de símbolos. */
        public EntradaTabelaDeSimbolos(String nome, TipoLA tipo, String nomeTipo) {
            this.nome = nome;
            this.tipo = tipo;
            this.nomeTipo = nomeTipo;
        }
    }

    // Símbolos declarados neste escopo
    private final Map<String, EntradaTabelaDeSimbolos> tabela;

    /** Cria uma tabela vazia. */
    public TabelaDeSimbolos() {
        this.tabela = new LinkedHashMap<>();
    }

    /** Insere um novo identificador. */
    public void inserir(String nome, TipoLA tipo, String nomeTipo) {
        tabela.put(nome, new EntradaTabelaDeSimbolos(nome, tipo, nomeTipo));
    }

    /** Verifica se um identificador existe. */
    public boolean existe(String nome) {
        return tabela.containsKey(nome);
    }

    /** Recupera a entrada de um identificador. */
    public EntradaTabelaDeSimbolos verificar(String nome) {
        return tabela.get(nome);
    }
}
