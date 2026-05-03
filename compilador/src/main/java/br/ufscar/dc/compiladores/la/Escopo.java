package br.ufscar.dc.compiladores.la;

import java.util.LinkedList;
import java.util.List;

/** Gerenciador de escopos para o analisador semântico. */
public class Escopo {

    // Pilha de escopos
    private final LinkedList<TabelaDeSimbolos> pilhaDeTabelas;

    /** Cria um gerenciador de escopos vazio. */
    public Escopo() {
        pilhaDeTabelas = new LinkedList<>();
    }

    /** Cria e empilha um novo escopo. */
    public void criarNovoEscopo() {
        pilhaDeTabelas.push(new TabelaDeSimbolos());
    }

    /** Retorna o escopo atual (topo da pilha). */
    public TabelaDeSimbolos escopoAtual() {
        return pilhaDeTabelas.peek();
    }

    /** Remove o escopo atual da pilha. */
    public void abandonarEscopo() {
        pilhaDeTabelas.pop();
    }

    /** Retorna todos os escopos ativos. */
    public List<TabelaDeSimbolos> obterTodosEscopos() {
        return pilhaDeTabelas;
    }
}
