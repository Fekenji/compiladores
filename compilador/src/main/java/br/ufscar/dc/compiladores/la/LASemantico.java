package br.ufscar.dc.compiladores.la;

import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.TipoLA;
import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.EntradaTabelaDeSimbolos;


/** Analisador semântico da linguagem LA implementado como um Visitor ANTLR4. */
public class LASemantico extends LAParserBaseVisitor<Void> {

    // Gerenciador de escopos (pilha de tabelas de símbolos)
    Escopo escopos = new Escopo();

    // ==================== Programa ====================

    /** Visita o nó raiz do programa. Cria o escopo global e processa */
    @Override
    public Void visitPrograma(LAParser.ProgramaContext ctx) {
        escopos.criarNovoEscopo();
        super.visitPrograma(ctx);
        escopos.abandonarEscopo();
        return null;
    }

    // ==================== Declarações Locais ====================

    /** Visita uma declaração local, que pode ser: */
    @Override
    public Void visitDeclaracao_local(LAParser.Declaracao_localContext ctx) {
        if (ctx.variavel() != null) {
            // Caso 'declare variavel'
            processarVariavel(ctx.variavel());
        } else if (ctx.valor_constante() != null) {
            // Caso 'constante' IDENT ':' tipo_basico '=' valor_constante
            String nomeConst = ctx.IDENT().getText();
            TipoLA tipo = LASemanticoUtils.getTipoPorNome(ctx.tipo_basico().getText());

            if (escopos.escopoAtual().existe(nomeConst)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nomeConst + " ja declarado anteriormente");
            } else {
                escopos.escopoAtual().inserir(nomeConst, tipo, ctx.tipo_basico().getText());
            }
        } else if (ctx.tipo() != null && ctx.IDENT() != null) {
            // Caso 'tipo' IDENT ':' tipo
            String nomeTipo = ctx.IDENT().getText();

            if (escopos.escopoAtual().existe(nomeTipo)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nomeTipo + " ja declarado anteriormente");
            } else {
                // Registrar o tipo (pode ser registro ou tipo estendido)
                escopos.escopoAtual().inserir(nomeTipo, TipoLA.REGISTRO, nomeTipo);

                // Se for registro, registrar os campos como "nomeTipo.campo"
                if (ctx.tipo().registro() != null) {
                    for (LAParser.VariavelContext varCtx : ctx.tipo().registro().variavel()) {
                        TipoLA tipoCampo = resolverTipoVariavel(varCtx.tipo());
                        for (LAParser.IdentificadorContext idCtx : varCtx.identificador()) {
                            String nomeCampo = nomeTipo + "." + idCtx.IDENT(0).getText();
                            escopos.escopoAtual().inserir(nomeCampo, tipoCampo,
                                varCtx.tipo().getText());
                        }
                    }
                }
            }
        }
        return null;
    }

    // ==================== Declarações Globais (Funções/Procedimentos) ====================

    /** Visita uma declaração global (procedimento ou função). */
    @Override
    public Void visitDeclaracao_global(LAParser.Declaracao_globalContext ctx) {
        String nome = ctx.IDENT().getText();

        // Determinar o tipo de retorno (INVALIDO para procedimentos)
        TipoLA tipoRetorno = TipoLA.INVALIDO;
        if (ctx.tipo_estendido() != null) {
            // É uma função — resolver tipo de retorno
            tipoRetorno = resolverTipoEstendido(ctx.tipo_estendido());
        }

        // Registrar a função/procedimento no escopo atual
        if (escopos.escopoAtual().existe(nome)) {
            LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                "identificador " + nome + " ja declarado anteriormente");
        } else {
            escopos.escopoAtual().inserir(nome, tipoRetorno, nome);
        }

        // Criar novo escopo para o corpo da sub-rotina
        escopos.criarNovoEscopo();

        // Registrar parâmetros no novo escopo
        if (ctx.parametros() != null) {
            for (LAParser.ParametroContext paramCtx : ctx.parametros().parametro()) {
                TipoLA tipoParam = resolverTipoEstendido(paramCtx.tipo_estendido());
                for (LAParser.IdentificadorContext idCtx : paramCtx.identificador()) {
                    String nomeParam = idCtx.IDENT(0).getText();
                    escopos.escopoAtual().inserir(nomeParam, tipoParam,
                        paramCtx.tipo_estendido().getText());
                }
            }
        }

        // Processar declarações locais e comandos dentro da sub-rotina
        for (LAParser.Declaracao_localContext declCtx : ctx.declaracao_local()) {
            visitDeclaracao_local(declCtx);
        }
        for (LAParser.CmdContext cmdCtx : ctx.cmd()) {
            visit(cmdCtx);
        }

        escopos.abandonarEscopo();
        return null;
    }

    // ==================== Comandos ====================

    /** Visita o comando 'leia'. Verifica se cada identificador utilizado */
    @Override
    public Void visitCmdLeia(LAParser.CmdLeiaContext ctx) {
        for (LAParser.IdentificadorContext idCtx : ctx.identificador()) {
            verificarIdentificadorExiste(idCtx);
        }
        return null;
    }

    /** Visita o comando 'escreva'. Infere o tipo de cada expressão */
    @Override
    public Void visitCmdEscreva(LAParser.CmdEscrevaContext ctx) {
        for (LAParser.ExpressaoContext exprCtx : ctx.expressao()) {
            LASemanticoUtils.inferirTipo(escopos, exprCtx);
        }
        return null;
    }

    /** Visita o comando de atribuição: ['^'] identificador '<-' expressao. */
    @Override
    public Void visitCmdAtribuicao(LAParser.CmdAtribuicaoContext ctx) {
        // Obter o nome completo do identificador (com possíveis campos de registro)
        String nomeId = obterNomeCompleto(ctx.identificador());
        boolean temPonteiro = ctx.getText().startsWith("^");

        // Verificar se o identificador existe
        EntradaTabelaDeSimbolos entrada = buscarIdentificador(ctx.identificador());

        if (entrada == null) {
            // Identificador não declarado
            LASemanticoUtils.adicionarErroSemantico(ctx.identificador().IDENT(0).getSymbol(),
                "identificador " + ctx.identificador().IDENT(0).getText() + " nao declarado");
            // Ainda assim, inferir tipo da expressão para detectar outros erros
            LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
            return null;
        }

        // Inferir o tipo da expressão do lado direito
        TipoLA tipoExpressao = LASemanticoUtils.inferirTipo(escopos, ctx.expressao());

        // Determinar o tipo do destino
        TipoLA tipoDestino = entrada.tipo;
        if (temPonteiro) {
            tipoDestino = TipoLA.PONTEIRO;
        }

        // Verificar compatibilidade de tipos na atribuição
        if (!LASemanticoUtils.tiposCompativeis(tipoDestino, tipoExpressao)) {
            // Usar o nome base do identificador (primeiro IDENT) na mensagem de erro
            LASemanticoUtils.adicionarErroSemantico(ctx.identificador().IDENT(0).getSymbol(),
                "atribuicao nao compativel para " + nomeId);
        }

        return null;
    }

    /** Visita o comando 'se'. Infere o tipo da expressão condicional */
    @Override
    public Void visitCmdSe(LAParser.CmdSeContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return super.visitCmdSe(ctx);
    }

    /** Visita o comando 'enquanto'. Infere o tipo da expressão condicional */
    @Override
    public Void visitCmdEnquanto(LAParser.CmdEnquantoContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return super.visitCmdEnquanto(ctx);
    }

    /** Visita o comando 'para'. Verifica se a variável do laço existe */
    @Override
    public Void visitCmdPara(LAParser.CmdParaContext ctx) {
        String nomeVar = ctx.IDENT().getText();
        if (!LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeVar)) {
            LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                "identificador " + nomeVar + " nao declarado");
        }
        return super.visitCmdPara(ctx);
    }

    /** Visita o comando 'retorne'. Infere o tipo da expressão de retorno. */
    @Override
    public Void visitCmdRetorne(LAParser.CmdRetorneContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return null;
    }

    /** Visita o comando 'caso'. Infere o tipo da expressão e processa seleção. */
    @Override
    public Void visitCmdCaso(LAParser.CmdCasoContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.exp_aritmetica());
        return super.visitCmdCaso(ctx);
    }

    /** Visita o comando 'faca...ate'. Infere o tipo da expressão condicional. */
    @Override
    public Void visitCmdFaca(LAParser.CmdFacaContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return super.visitCmdFaca(ctx);
    }

    // ==================== Métodos Auxiliares ====================

    /** Processa uma declaração de variável, registrando cada identificador */
    private void processarVariavel(LAParser.VariavelContext ctx) {
        // Resolver o tipo da variável
        TipoLA tipo = resolverTipoVariavel(ctx.tipo());
        String nomeTipo = ctx.tipo().getText();

        // Verificar se o tipo é válido (se for tipo_estendido com IDENT customizado)
        if (ctx.tipo().tipo_estendido() != null) {
            LAParser.Tipo_basico_identContext tbi = ctx.tipo().tipo_estendido().tipo_basico_ident();
            if (tbi.IDENT() != null) {
                String nomeDoTipo = tbi.IDENT().getText();
                if (!LASemanticoUtils.ehTipoBasico(nomeDoTipo)
                    && !LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeDoTipo)) {
                    LASemanticoUtils.adicionarErroSemantico(tbi.IDENT().getSymbol(),
                        "tipo " + nomeDoTipo + " nao declarado");
                    tipo = TipoLA.INVALIDO;
                }
            }
        }

        // Registrar cada identificador da declaração
        for (LAParser.IdentificadorContext idCtx : ctx.identificador()) {
            String nomeId = idCtx.IDENT(0).getText();

            // Verificar duplicata no escopo atual
            if (escopos.escopoAtual().existe(nomeId)) {
                LASemanticoUtils.adicionarErroSemantico(idCtx.IDENT(0).getSymbol(),
                    "identificador " + nomeId + " ja declarado anteriormente");
            } else {
                escopos.escopoAtual().inserir(nomeId, tipo, nomeTipo);

                // Se o tipo for um registro customizado, registrar campos
                if (tipo == TipoLA.REGISTRO || LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeTipo)) {
                    EntradaTabelaDeSimbolos entradaTipo = LASemanticoUtils.buscarEscopos(escopos, nomeTipo);
                    if (entradaTipo != null && entradaTipo.tipo == TipoLA.REGISTRO) {
                        // Copiar campos do tipo registro para o novo identificador
                        for (TabelaDeSimbolos tabela : escopos.obterTodosEscopos()) {
                            for (String chave : getCamposRegistro(tabela, nomeTipo)) {
                                String campo = chave.substring(nomeTipo.length() + 1);
                                EntradaTabelaDeSimbolos entradaCampo = tabela.verificar(chave);
                                escopos.escopoAtual().inserir(nomeId + "." + campo,
                                    entradaCampo.tipo, entradaCampo.nomeTipo);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Obtém os nomes dos campos de um registro a partir da tabela de símbolos. */
    private java.util.List<String> getCamposRegistro(TabelaDeSimbolos tabela, String nomeTipo) {
        java.util.List<String> campos = new java.util.ArrayList<>();
        String prefixo = nomeTipo + ".";
        // Buscar todas as entradas que começam com "nomeTipo."
        // Como usamos LinkedHashMap, iteramos sobre as chaves
        for (String chave : getChaves(tabela)) {
            if (chave.startsWith(prefixo)) {
                campos.add(chave);
            }
        }
        return campos;
    }

    /** Obtém todas as chaves de uma tabela de símbolos usando reflexão. */
    private java.util.Set<String> getChaves(TabelaDeSimbolos tabela) {
        // Tentamos verificar nomes comuns de campos de registro
        java.util.Set<String> chaves = new java.util.LinkedHashSet<>();
        try {
            java.lang.reflect.Field field = TabelaDeSimbolos.class.getDeclaredField("tabela");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, ?> mapa = (java.util.Map<String, ?>) field.get(tabela);
            chaves.addAll(mapa.keySet());
        } catch (Exception e) {
            // Ignorar exceções de reflexão
        }
        return chaves;
    }

    /** Resolve o tipo de uma declaração de variável a partir do contexto 'tipo'. */
    private TipoLA resolverTipoVariavel(LAParser.TipoContext ctx) {
        if (ctx.registro() != null) {
            return TipoLA.REGISTRO;
        }
        return resolverTipoEstendido(ctx.tipo_estendido());
    }

    /** Resolve o tipo a partir de um tipo_estendido ('^'? tipo_basico_ident). */
    private TipoLA resolverTipoEstendido(LAParser.Tipo_estendidoContext ctx) {
        boolean ponteiro = ctx.getText().startsWith("^");
        if (ponteiro) {
            return TipoLA.PONTEIRO;
        }
        LAParser.Tipo_basico_identContext tbi = ctx.tipo_basico_ident();
        if (tbi.tipo_basico() != null) {
            return LASemanticoUtils.getTipoPorNome(tbi.tipo_basico().getText());
        }
        // Tipo customizado (IDENT) — verificar se existe
        if (tbi.IDENT() != null) {
            String nomeDoTipo = tbi.IDENT().getText();
            if (LASemanticoUtils.ehTipoBasico(nomeDoTipo)) {
                return LASemanticoUtils.getTipoPorNome(nomeDoTipo);
            }
            EntradaTabelaDeSimbolos entrada = LASemanticoUtils.buscarEscopos(escopos, nomeDoTipo);
            if (entrada != null) {
                return entrada.tipo;
            }
        }
        return TipoLA.NAO_DECLARADO;
    }

    /** Verifica se um identificador usado em uma expressão/comando existe em algum escopo. */
    private void verificarIdentificadorExiste(LAParser.IdentificadorContext ctx) {
        String nome = ctx.IDENT(0).getText();
        if (!LASemanticoUtils.existeEmAlgumEscopo(escopos, nome)) {
            LASemanticoUtils.adicionarErroSemantico(ctx.IDENT(0).getSymbol(),
                "identificador " + nome + " nao declarado");
        }
    }

    /** Busca um identificador em todos os escopos, considerando campos de registro. */
    private EntradaTabelaDeSimbolos buscarIdentificador(LAParser.IdentificadorContext ctx) {
        String nome = obterNomeCompleto(ctx);
        EntradaTabelaDeSimbolos entrada = LASemanticoUtils.buscarEscopos(escopos, nome);
        if (entrada == null) {
            // Tentar buscar apenas pelo nome base
            entrada = LASemanticoUtils.buscarEscopos(escopos, ctx.IDENT(0).getText());
        }
        return entrada;
    }

    /** Obtém o nome completo de um identificador, incluindo acessos a campos de registro. */
    private String obterNomeCompleto(LAParser.IdentificadorContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.IDENT(0).getText());
        for (int i = 1; i < ctx.IDENT().size(); i++) {
            sb.append(".").append(ctx.IDENT(i).getText());
        }
        return sb.toString();
    }

    // Inferência de tipo para exp_aritmetica (utilizado pelo cmdCaso)
    private TipoLA inferirTipo(LAParser.Exp_aritmeticaContext ctx) {
        return LASemanticoUtils.inferirTipo(escopos, ctx);
    }
}
