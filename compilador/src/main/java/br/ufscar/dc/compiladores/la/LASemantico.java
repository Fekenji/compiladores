package br.ufscar.dc.compiladores.la;

import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.TipoLA;
import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.EntradaTabelaDeSimbolos;

import java.util.ArrayList;
import java.util.List;

/** Analisador semântico da linguagem LA implementado como um Visitor ANTLR4. */
public class LASemantico extends LAParserBaseVisitor<Void> {

    Escopo escopos = new Escopo();

    // Rastreia se estamos dentro de uma funcao
    private boolean dentroFuncao = false;

    // ==================== Programa ====================

    @Override
    public Void visitPrograma(LAParser.ProgramaContext ctx) {
        escopos.criarNovoEscopo();
        super.visitPrograma(ctx);
        escopos.abandonarEscopo();
        return null;
    }

    // ==================== Declarações Locais ====================

    @Override
    public Void visitDeclaracao_local(LAParser.Declaracao_localContext ctx) {
        if (ctx.variavel() != null) {
            processarVariavel(ctx.variavel());
        } else if (ctx.valor_constante() != null) {
            String nomeConst = ctx.IDENT().getText();
            TipoLA tipo = LASemanticoUtils.getTipoPorNome(ctx.tipo_basico().getText());
            if (escopos.escopoAtual().existe(nomeConst)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                        "identificador " + nomeConst + " ja declarado anteriormente");
            } else {
                escopos.escopoAtual().inserir(nomeConst, tipo, ctx.tipo_basico().getText());
            }
        } else if (ctx.tipo() != null && ctx.IDENT() != null) {
            String nomeTipo = ctx.IDENT().getText();
            if (escopos.escopoAtual().existe(nomeTipo)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                        "identificador " + nomeTipo + " ja declarado anteriormente");
            } else {
                escopos.escopoAtual().inserir(nomeTipo, TipoLA.REGISTRO, nomeTipo);
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

    // ==================== Declarações Globais ====================

    @Override
    public Void visitDeclaracao_global(LAParser.Declaracao_globalContext ctx) {
        String nome = ctx.IDENT().getText();
        boolean ehFuncao = ctx.tipo_estendido() != null;

        TipoLA tipoRetorno = TipoLA.INVALIDO;
        if (ehFuncao) {
            tipoRetorno = resolverTipoEstendido(ctx.tipo_estendido());
        }

        if (escopos.escopoAtual().existe(nome)) {
            LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nome + " ja declarado anteriormente");
        } else {
            escopos.escopoAtual().inserir(nome, tipoRetorno, nome);
        }

        // Coletar tipos dos parâmetros formais
        List<TipoLA> tiposParametros = new ArrayList<>();
        if (ctx.parametros() != null) {
            for (LAParser.ParametroContext paramCtx : ctx.parametros().parametro()) {
                TipoLA tipoParam = resolverTipoEstendido(paramCtx.tipo_estendido());
                for (int i = 0; i < paramCtx.identificador().size(); i++) {
                    tiposParametros.add(tipoParam);
                }
            }
        }
        LASemanticoUtils.parametrosFuncoes.put(nome, tiposParametros);

        escopos.criarNovoEscopo();

        // Registrar parâmetros no novo escopo
        if (ctx.parametros() != null) {
            for (LAParser.ParametroContext paramCtx : ctx.parametros().parametro()) {
                TipoLA tipoParam = resolverTipoEstendido(paramCtx.tipo_estendido());
                String nomeTipoParam = paramCtx.tipo_estendido().getText().replace("^", "");
                for (LAParser.IdentificadorContext idCtx : paramCtx.identificador()) {
                    String nomeParam = idCtx.IDENT(0).getText();
                    escopos.escopoAtual().inserir(nomeParam, tipoParam, nomeTipoParam);
                    // Se for registro, copiar campos
                    if (tipoParam == TipoLA.REGISTRO) {
                        registrarCamposRegistro(nomeTipoParam, nomeParam);
                    }
                }
            }
        }

        boolean dentroFuncaoAnterior = dentroFuncao;
        dentroFuncao = ehFuncao;

        for (LAParser.Declaracao_localContext declCtx : ctx.declaracao_local()) {
            visitDeclaracao_local(declCtx);
        }
        for (LAParser.CmdContext cmdCtx : ctx.cmd()) {
            visit(cmdCtx);
        }

        dentroFuncao = dentroFuncaoAnterior;
        escopos.abandonarEscopo();
        return null;
    }

    // ==================== Comandos ====================

    @Override
    public Void visitCmdLeia(LAParser.CmdLeiaContext ctx) {
        for (LAParser.IdentificadorContext idCtx : ctx.identificador()) {
            verificarIdentificadorExiste(idCtx);
        }
        return null;
    }

    @Override
    public Void visitCmdEscreva(LAParser.CmdEscrevaContext ctx) {
        for (LAParser.ExpressaoContext exprCtx : ctx.expressao()) {
            LASemanticoUtils.inferirTipo(escopos, exprCtx);
        }
        return null;
    }

    @Override
    public Void visitCmdAtribuicao(LAParser.CmdAtribuicaoContext ctx) {
        String nomeId = obterNomeCompleto(ctx.identificador());
        boolean temPonteiro = ctx.getText().startsWith("^");

        EntradaTabelaDeSimbolos entrada = buscarIdentificador(ctx.identificador());

        if (entrada == null) {
            String nomeErro = temPonteiro ? "^" + nomeId : nomeId;
            LASemanticoUtils.adicionarErroSemantico(ctx.identificador().IDENT(0).getSymbol(),
                    "identificador " + nomeErro + " nao declarado");
            LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
            return null;
        }

        TipoLA tipoExpressao = LASemanticoUtils.inferirTipo(escopos, ctx.expressao());

        TipoLA tipoDestino = entrada.tipo;
        if (temPonteiro) {
            tipoDestino = TipoLA.PONTEIRO;
        }

        if (!LASemanticoUtils.tiposCompativeis(tipoDestino, tipoExpressao)) {
            String nomeErro = temPonteiro ? "^" + nomeId : nomeId;
            LASemanticoUtils.adicionarErroSemantico(ctx.identificador().IDENT(0).getSymbol(),
                    "atribuicao nao compativel para " + nomeErro);
        }

        return null;
    }

    @Override
    public Void visitCmdSe(LAParser.CmdSeContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return super.visitCmdSe(ctx);
    }

    @Override
    public Void visitCmdEnquanto(LAParser.CmdEnquantoContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return super.visitCmdEnquanto(ctx);
    }

    @Override
    public Void visitCmdPara(LAParser.CmdParaContext ctx) {
        String nomeVar = ctx.IDENT().getText();
        if (!LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeVar)) {
            LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nomeVar + " nao declarado");
        }
        return super.visitCmdPara(ctx);
    }

    @Override
    public Void visitCmdRetorne(LAParser.CmdRetorneContext ctx) {
        if (!dentroFuncao) {
            LASemanticoUtils.adicionarErroSemantico(ctx.getStart(),
                    "comando retorne nao permitido nesse escopo");
        }
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return null;
    }

    @Override
    public Void visitCmdCaso(LAParser.CmdCasoContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.exp_aritmetica());
        return super.visitCmdCaso(ctx);
    }

    @Override
    public Void visitCmdFaca(LAParser.CmdFacaContext ctx) {
        LASemanticoUtils.inferirTipo(escopos, ctx.expressao());
        return super.visitCmdFaca(ctx);
    }

    @Override
    public Void visitCmdChamada(LAParser.CmdChamadaContext ctx) {
        String nome = ctx.IDENT().getText();
        EntradaTabelaDeSimbolos entrada = LASemanticoUtils.buscarEscopos(escopos, nome);
        if (entrada == null) {
            LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nome + " nao declarado");
        } else {
            // Verificar compatibilidade de parâmetros
            List<TipoLA> tiposArgs = new ArrayList<>();
            for (LAParser.ExpressaoContext expr : ctx.expressao()) {
                tiposArgs.add(LASemanticoUtils.inferirTipo(escopos, expr));
            }
            List<TipoLA> tiposParams = LASemanticoUtils.parametrosFuncoes.get(nome);
            if (tiposParams != null && !LASemanticoUtils.parametrosCompativeis(tiposParams, tiposArgs)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                        "incompatibilidade de parametros na chamada de " + nome);
            }
        }
        return null;
    }

    // ==================== Métodos Auxiliares ====================

    private void processarVariavel(LAParser.VariavelContext ctx) {
        TipoLA tipo = resolverTipoVariavel(ctx.tipo());
        String nomeTipo = ctx.tipo().getText();
        boolean inlineRegistro = ctx.tipo().registro() != null;

        if (!inlineRegistro && ctx.tipo().tipo_estendido() != null) {
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

        for (LAParser.IdentificadorContext idCtx : ctx.identificador()) {
            String nomeId = idCtx.IDENT(0).getText();
            if (escopos.escopoAtual().existe(nomeId)) {
                LASemanticoUtils.adicionarErroSemantico(idCtx.IDENT(0).getSymbol(),
                        "identificador " + nomeId + " ja declarado anteriormente");
            } else {
                escopos.escopoAtual().inserir(nomeId, tipo, nomeTipo);
                if (inlineRegistro) {
                    // Registro inline: registrar campos diretamente
                    for (LAParser.VariavelContext varCtx : ctx.tipo().registro().variavel()) {
                        TipoLA tipoCampo = resolverTipoVariavel(varCtx.tipo());
                        for (LAParser.IdentificadorContext campoCtx : varCtx.identificador()) {
                            String nomeCampo = campoCtx.IDENT(0).getText();
                            escopos.escopoAtual().inserir(nomeId + "." + nomeCampo,
                                    tipoCampo, varCtx.tipo().getText());
                        }
                    }
                } else {
                    // Tipo nomeado: copiar campos do tipo registro
                    EntradaTabelaDeSimbolos entradaTipo = LASemanticoUtils.buscarEscopos(escopos, nomeTipo);
                    if (entradaTipo != null && entradaTipo.tipo == TipoLA.REGISTRO) {
                        registrarCamposRegistro(nomeTipo, nomeId);
                    }
                }
            }
        }
    }

    /**
     * Copia os campos de um tipo registro para um novo identificador no escopo
     * atual.
     */
    private void registrarCamposRegistro(String nomeTipo, String nomeVar) {
        for (TabelaDeSimbolos tabela : escopos.obterTodosEscopos()) {
            for (String chave : getChaves(tabela)) {
                if (chave.startsWith(nomeTipo + ".")) {
                    String campo = chave.substring(nomeTipo.length() + 1);
                    EntradaTabelaDeSimbolos entradaCampo = tabela.verificar(chave);
                    if (!escopos.escopoAtual().existe(nomeVar + "." + campo)) {
                        escopos.escopoAtual().inserir(nomeVar + "." + campo,
                                entradaCampo.tipo, entradaCampo.nomeTipo);
                    }
                }
            }
        }
    }

    private java.util.Set<String> getChaves(TabelaDeSimbolos tabela) {
        java.util.Set<String> chaves = new java.util.LinkedHashSet<>();
        try {
            java.lang.reflect.Field field = TabelaDeSimbolos.class.getDeclaredField("tabela");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, ?> mapa = (java.util.Map<String, ?>) field.get(tabela);
            chaves.addAll(mapa.keySet());
        } catch (Exception e) {
            // ignore
        }
        return chaves;
    }

    private TipoLA resolverTipoVariavel(LAParser.TipoContext ctx) {
        if (ctx.registro() != null) {
            return TipoLA.REGISTRO;
        }
        return resolverTipoEstendido(ctx.tipo_estendido());
    }

    private TipoLA resolverTipoEstendido(LAParser.Tipo_estendidoContext ctx) {
        boolean ponteiro = ctx.getText().startsWith("^");
        if (ponteiro) {
            return TipoLA.PONTEIRO;
        }
        LAParser.Tipo_basico_identContext tbi = ctx.tipo_basico_ident();
        if (tbi.tipo_basico() != null) {
            return LASemanticoUtils.getTipoPorNome(tbi.tipo_basico().getText());
        }
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

    /** Verifica se um identificador (com possível campo ou subscrito) existe. */
    private void verificarIdentificadorExiste(LAParser.IdentificadorContext ctx) {
        boolean temDimensao = !ctx.dimensao().getText().isEmpty();
        boolean temCampo = ctx.IDENT().size() > 1;

        if (temDimensao) {
            // Acesso a array: verificar pelo nome base (sem subscrito)
            String nomeBase = ctx.IDENT(0).getText();
            if (!LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeBase)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT(0).getSymbol(),
                        "identificador " + obterNomeCompleto(ctx) + " nao declarado");
            }
        } else if (temCampo) {
            // Acesso a campo de registro: verificar caminho completo
            String nomeCompleto = obterNomeCompleto(ctx);
            if (!LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeCompleto)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT(0).getSymbol(),
                        "identificador " + nomeCompleto + " nao declarado");
            }
        } else {
            String nomeBase = ctx.IDENT(0).getText();
            if (!LASemanticoUtils.existeEmAlgumEscopo(escopos, nomeBase)) {
                LASemanticoUtils.adicionarErroSemantico(ctx.IDENT(0).getSymbol(),
                        "identificador " + nomeBase + " nao declarado");
            }
        }
    }

    private EntradaTabelaDeSimbolos buscarIdentificador(LAParser.IdentificadorContext ctx) {
        String nome = obterNomeCompleto(ctx);
        EntradaTabelaDeSimbolos entrada = LASemanticoUtils.buscarEscopos(escopos, nome);
        if (entrada == null) {
            entrada = LASemanticoUtils.buscarEscopos(escopos, ctx.IDENT(0).getText());
        }
        return entrada;
    }

    /**
     * Retorna o nome completo do identificador, incluindo campos e subscrito de
     * array.
     */
    private String obterNomeCompleto(LAParser.IdentificadorContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.IDENT(0).getText());
        for (int i = 1; i < ctx.IDENT().size(); i++) {
            sb.append(".").append(ctx.IDENT(i).getText());
        }
        String dim = ctx.dimensao().getText();
        if (!dim.isEmpty()) {
            sb.append(dim);
        }
        return sb.toString();
    }
}
