package br.ufscar.dc.compiladores.la;

import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.TipoLA;
import br.ufscar.dc.compiladores.la.TabelaDeSimbolos.EntradaTabelaDeSimbolos;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerador de código C para a linguagem LA.
 * Percorre a AST gerada pelo ANTLR e emite código C equivalente.
 */
public class LAGeradorCodigo extends LAParserBaseVisitor<Void> {

    private final PrintWriter out;
    private final Escopo escopos = new Escopo();
    private int indentacao = 0;

    // Tipos de registro definidos globalmente (tipo X: registro)
    private final List<String> tiposRegistroDefinidos = new ArrayList<>();

    public LAGeradorCodigo(PrintWriter out) {
        this.out = out;
    }

    // ── utilitários ──────────────────────────────────────────────────────────

    private void emitirLinha(String linha) {
        out.println("    ".repeat(indentacao) + linha);
    }

    /** Converte tipo LA em tipo C (para declarações simples). */
    private String tipoCPorNome(String nomeTipo) {
        switch (nomeTipo) {
            case "inteiro": return "int";
            case "real":    return "float";
            case "literal": return "char";
            case "logico":  return "int";
            default:        return nomeTipo; // tipo definido pelo usuário
        }
    }

    /** Formato printf/scanf para um tipo LA. */
    private String formatoParaTipo(TipoLA tipo) {
        switch (tipo) {
            case INTEIRO: return "%d";
            case REAL:    return "%f";
            case LITERAL: return "%s";
            case LOGICO:  return "%d";
            default:      return "%d";
        }
    }

    /** Resolve o tipo LA de um identificador buscando em todos os escopos. */
    private TipoLA resolverTipoIdent(String nome) {
        EntradaTabelaDeSimbolos e = LASemanticoUtils.buscarEscopos(escopos, nome);
        if (e != null) return e.tipo;
        return TipoLA.INVALIDO;
    }

    /** Nome original do tipo de uma entrada (para registro/ponteiro). */
    private String resolverNomeTipoIdent(String nome) {
        EntradaTabelaDeSimbolos e = LASemanticoUtils.buscarEscopos(escopos, nome);
        if (e != null) return e.nomeTipo;
        return "";
    }

    // ── declaração de variável em C ───────────────────────────────────────────

    /**
     * Emite declaração C de um conjunto de identificadores com um tipo LA.
     * Lida com: básicos, ponteiros, literais (char[80]), registros inline e arrays.
     */
    private void emitirDeclaracaoVariavel(LAParser.VariavelContext ctx) {
        LAParser.TipoContext tipoCtx = ctx.tipo();
        List<LAParser.IdentificadorContext> ids = ctx.identificador();

        if (tipoCtx.registro() != null) {
            // registro inline: struct { ... } var1, var2;
            for (LAParser.IdentificadorContext id : ids) {
                String nomeVar = id.IDENT(0).getText();
                emitirLinha("struct {");
                indentacao++;
                for (LAParser.VariavelContext campo : tipoCtx.registro().variavel()) {
                    emitirDeclaracaoCampo(campo);
                    // registrar campos no escopo: nomeVar.nomeCampo
                    String nomeTipoCampo = campo.tipo().tipo_estendido() != null
                            ? campo.tipo().tipo_estendido().tipo_basico_ident().getText() : "";
                    TipoLA tipoCampo = LASemanticoUtils.getTipoPorNome(nomeTipoCampo);
                    for (LAParser.IdentificadorContext campoId : campo.identificador()) {
                        String chave = nomeVar + "." + campoId.IDENT(0).getText();
                        escopos.escopoAtual().inserir(chave, tipoCampo, nomeTipoCampo);
                    }
                }
                indentacao--;
                emitirLinha("} " + nomeVar + ";");
                escopos.escopoAtual().inserir(nomeVar, TipoLA.REGISTRO, nomeVar);
            }
        } else {
            // tipo_estendido: básico, ponteiro ou nome de tipo
            LAParser.Tipo_estendidoContext te = tipoCtx.tipo_estendido();
            boolean ponteiro = te.getText().startsWith("^");
            String nomeTipoBase = te.tipo_basico_ident().getText();

            for (LAParser.IdentificadorContext id : ids) {
                String nomeVar = id.IDENT(0).getText();
                // dimensão de array
                String dimC = "";
                if (!id.dimensao().getText().isEmpty()) {
                    dimC = "[" + id.dimensao().exp_aritmetica(0).getText() + "]";
                }

                if (ponteiro) {
                    String tipoC = tipoCPorNome(nomeTipoBase);
                    emitirLinha(tipoC + "* " + nomeVar + ";");
                } else if (nomeTipoBase.equals("literal")) {
                    if (dimC.isEmpty()) {
                        emitirLinha("char " + nomeVar + "[80];");
                    } else {
                        emitirLinha("char " + nomeVar + dimC + "[80];");
                    }
                } else {
                    String tipoC = tipoCPorNome(nomeTipoBase);
                    emitirLinha(tipoC + " " + nomeVar + dimC + ";");
                }

                // registrar no escopo para consulta posterior de tipo
                TipoLA tipo = LASemanticoUtils.getTipoPorNome(nomeTipoBase);
                if (tipo == TipoLA.NAO_DECLARADO) tipo = TipoLA.REGISTRO;
                if (ponteiro) tipo = TipoLA.PONTEIRO;
                escopos.escopoAtual().inserir(nomeVar, tipo, nomeTipoBase);

                // Se for tipo nomeado de registro, copiar campos para o escopo
                if (tipo == TipoLA.REGISTRO) {
                    copiarCamposRegistro(nomeTipoBase, nomeVar);
                }
            }
        }
    }

    /** Copia campos de um tipo registro para uma variável no escopo atual. */
    private void copiarCamposRegistro(String nomeTipo, String nomeVar) {
        for (TabelaDeSimbolos tabela : escopos.obterTodosEscopos()) {
            // Iterar sobre todas as entradas procurando "nomeTipo.campo"
            // TabelaDeSimbolos não expõe keySet, mas podemos usar entradas conhecidas
            // via busca por prefixo usando reflexão (mesmo padrão do LASemantico)
            try {
                java.lang.reflect.Field field = TabelaDeSimbolos.class.getDeclaredField("tabela");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, EntradaTabelaDeSimbolos> mapa =
                        (java.util.Map<String, EntradaTabelaDeSimbolos>) field.get(tabela);
                for (java.util.Map.Entry<String, EntradaTabelaDeSimbolos> e : mapa.entrySet()) {
                    if (e.getKey().startsWith(nomeTipo + ".")) {
                        String campo = e.getKey().substring(nomeTipo.length() + 1);
                        String chave = nomeVar + "." + campo;
                        if (!escopos.escopoAtual().existe(chave)) {
                            escopos.escopoAtual().inserir(chave, e.getValue().tipo, e.getValue().nomeTipo);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /** Emite declaração de campo de registro (sem bloco). */
    private void emitirDeclaracaoCampo(LAParser.VariavelContext campo) {
        LAParser.Tipo_estendidoContext te = campo.tipo().tipo_estendido();
        if (te == null) return;
        String nomeTipoBase = te.tipo_basico_ident().getText();
        boolean ponteiro = te.getText().startsWith("^");
        for (LAParser.IdentificadorContext id : campo.identificador()) {
            String nomeCampo = id.IDENT(0).getText();
            if (ponteiro) {
                emitirLinha(tipoCPorNome(nomeTipoBase) + "* " + nomeCampo + ";");
            } else if (nomeTipoBase.equals("literal")) {
                emitirLinha("char " + nomeCampo + "[80];");
            } else {
                emitirLinha(tipoCPorNome(nomeTipoBase) + " " + nomeCampo + ";");
            }
        }
    }

    // ── programa ──────────────────────────────────────────────────────────────

    @Override
    public Void visitPrograma(LAParser.ProgramaContext ctx) {
        out.println("#include <stdio.h>");
        out.println("#include <stdlib.h>");
        out.println("#include <string.h>");
        out.println("");

        escopos.criarNovoEscopo();

        // Primeiro: declarações globais (#define de constantes e funções/procedimentos)
        for (LAParser.Decl_local_globalContext dlg : ctx.declaracoes().decl_local_global()) {
            if (dlg.declaracao_local() != null) {
                visitDeclaracaoLocalGlobal(dlg.declaracao_local());
            }
        }
        for (LAParser.Decl_local_globalContext dlg : ctx.declaracoes().decl_local_global()) {
            if (dlg.declaracao_global() != null) {
                visitDeclaracao_global(dlg.declaracao_global());
            }
        }

        out.println("int main() {");
        indentacao++;

        // Declarações locais do corpo
        for (LAParser.Declaracao_localContext dl : ctx.corpo().declaracao_local()) {
            visitDeclaracao_local(dl);
        }

        // Comandos do corpo
        for (LAParser.CmdContext cmd : ctx.corpo().cmd()) {
            visit(cmd);
        }

        emitirLinha("return 0;");
        indentacao--;
        out.println("}");

        escopos.abandonarEscopo();
        return null;
    }

    /** Processa declarações locais que aparecem no nível global (tipo, constante). */
    private void visitDeclaracaoLocalGlobal(LAParser.Declaracao_localContext ctx) {
        if (ctx.valor_constante() != null) {
            // constante IDENT : tipo = valor
            String nome = ctx.IDENT().getText();
            String valor = ctx.valor_constante().getText();
            out.println("#define " + nome + " " + valor);
            // registrar no escopo global
            TipoLA tipo = LASemanticoUtils.getTipoPorNome(ctx.tipo_basico().getText());
            escopos.escopoAtual().inserir(nome, tipo, ctx.tipo_basico().getText());
        } else if (ctx.tipo() != null && ctx.IDENT() != null) {
            // tipo IDENT : registro ... fim_registro  → typedef struct
            String nomeTipo = ctx.IDENT().getText();
            tiposRegistroDefinidos.add(nomeTipo);
            escopos.escopoAtual().inserir(nomeTipo, TipoLA.REGISTRO, nomeTipo);
            if (ctx.tipo().registro() != null) {
                out.println("typedef struct {");
                for (LAParser.VariavelContext campo : ctx.tipo().registro().variavel()) {
                    // registrar campos no escopo global para cópia posterior
                    TipoLA tipoCampo = LASemanticoUtils.getTipoPorNome(
                            campo.tipo().tipo_estendido().tipo_basico_ident().getText());
                    for (LAParser.IdentificadorContext idCtx : campo.identificador()) {
                        escopos.escopoAtual().inserir(nomeTipo + "." + idCtx.IDENT(0).getText(),
                                tipoCampo, campo.tipo().tipo_estendido().tipo_basico_ident().getText());
                        // emitir campo
                        emitirCampoStruct(campo.tipo().tipo_estendido(), idCtx.IDENT(0).getText());
                    }
                }
                out.println("} " + nomeTipo + ";");
                out.println("");
            }
        }
        // declare variavel: não emitido no nível global aqui (são do corpo)
    }

    private void emitirCampoStruct(LAParser.Tipo_estendidoContext te, String nomeCampo) {
        boolean ponteiro = te.getText().startsWith("^");
        String nomeTipoBase = te.tipo_basico_ident().getText();
        if (nomeTipoBase.equals("literal")) {
            out.println("    char " + nomeCampo + "[80];");
        } else if (ponteiro) {
            out.println("    " + tipoCPorNome(nomeTipoBase) + "* " + nomeCampo + ";");
        } else {
            out.println("    " + tipoCPorNome(nomeTipoBase) + " " + nomeCampo + ";");
        }
    }

    // ── declarações locais (dentro de main/função) ────────────────────────────

    @Override
    public Void visitDeclaracao_local(LAParser.Declaracao_localContext ctx) {
        if (ctx.variavel() != null) {
            emitirDeclaracaoVariavel(ctx.variavel());
        } else if (ctx.valor_constante() != null) {
            // constante dentro de função — usar #define antes ou simplesmente const int
            String nome = ctx.IDENT().getText();
            // #define já foi emitido no nível global; apenas registrar
            TipoLA tipo = LASemanticoUtils.getTipoPorNome(ctx.tipo_basico().getText());
            escopos.escopoAtual().inserir(nome, tipo, ctx.tipo_basico().getText());
        } else if (ctx.tipo() != null && ctx.IDENT() != null) {
            String nomeTipo = ctx.IDENT().getText();
            tiposRegistroDefinidos.add(nomeTipo);
            escopos.escopoAtual().inserir(nomeTipo, TipoLA.REGISTRO, nomeTipo);
            if (ctx.tipo().registro() != null) {
                emitirLinha("typedef struct {");
                indentacao++;
                for (LAParser.VariavelContext campo : ctx.tipo().registro().variavel()) {
                    TipoLA tipoCampo = LASemanticoUtils.getTipoPorNome(
                            campo.tipo().tipo_estendido().tipo_basico_ident().getText());
                    for (LAParser.IdentificadorContext idCtx : campo.identificador()) {
                        escopos.escopoAtual().inserir(nomeTipo + "." + idCtx.IDENT(0).getText(),
                                tipoCampo, campo.tipo().tipo_estendido().tipo_basico_ident().getText());
                    }
                    emitirDeclaracaoCampo(campo);
                }
                indentacao--;
                emitirLinha("} " + nomeTipo + ";");
            }
        }
        return null;
    }

    // ── declarações globais (procedimento / funcao) ───────────────────────────

    @Override
    public Void visitDeclaracao_global(LAParser.Declaracao_globalContext ctx) {
        String nome = ctx.IDENT().getText();
        boolean ehFuncao = ctx.tipo_estendido() != null;

        // Cabeçalho
        StringBuilder cabecalho = new StringBuilder();
        if (ehFuncao) {
            String tipoRetC = tipoCParaTipoEstendido(ctx.tipo_estendido());
            cabecalho.append(tipoRetC).append(" ").append(nome).append("(");
        } else {
            cabecalho.append("void ").append(nome).append("(");
        }

        // Parâmetros
        escopos.criarNovoEscopo();
        if (ctx.parametros() != null) {
            List<String> params = new ArrayList<>();
            for (LAParser.ParametroContext p : ctx.parametros().parametro()) {
                boolean porRef = p.getText().startsWith("var");
                LAParser.Tipo_estendidoContext te = p.tipo_estendido();
                boolean ponteiro = te.getText().startsWith("^");
                String nomeTipoBase = te.tipo_basico_ident().getText();
                for (LAParser.IdentificadorContext id : p.identificador()) {
                    String nomeParam = id.IDENT(0).getText();
                    String tipoC;
                    if (nomeTipoBase.equals("literal")) {
                        tipoC = porRef ? "char*" : "char*";
                        params.add(tipoC + " " + nomeParam);
                    } else if (ponteiro) {
                        tipoC = tipoCPorNome(nomeTipoBase) + "*";
                        params.add(tipoC + " " + nomeParam);
                    } else {
                        tipoC = tipoCPorNome(nomeTipoBase);
                        if (porRef) tipoC += "*";
                        params.add(tipoC + " " + nomeParam);
                    }
                    TipoLA tipoLA = LASemanticoUtils.getTipoPorNome(nomeTipoBase);
                    escopos.escopoAtual().inserir(nomeParam, tipoLA, nomeTipoBase);
                }
            }
            cabecalho.append(String.join(", ", params));
        }
        cabecalho.append(") {");
        out.println(cabecalho.toString());
        indentacao++;

        // Declarações locais
        for (LAParser.Declaracao_localContext dl : ctx.declaracao_local()) {
            visitDeclaracao_local(dl);
        }

        // Comandos
        for (LAParser.CmdContext cmd : ctx.cmd()) {
            visit(cmd);
        }

        indentacao--;
        out.println("}");
        out.println("");
        escopos.abandonarEscopo();
        return null;
    }

    private String tipoCParaTipoEstendido(LAParser.Tipo_estendidoContext te) {
        if (te == null) return "void";
        boolean pont = te.getText().startsWith("^");
        String nome = te.tipo_basico_ident().getText();
        if (pont) return tipoCPorNome(nome) + "*";
        return tipoCPorNome(nome);
    }

    // ── comandos ──────────────────────────────────────────────────────────────

    @Override
    public Void visitCmdLeia(LAParser.CmdLeiaContext ctx) {
        for (LAParser.IdentificadorContext id : ctx.identificador()) {
            String nomeVar = nomeCompletoC(id);
            String nomeBase = id.IDENT(0).getText();
            TipoLA tipo = resolverTipoIdent(nomeBase);
            // Se for campo de struct, resolver pelo nome completo
            if (id.IDENT().size() > 1) {
                String nomeCompleto = nomeCompletoLA(id);
                EntradaTabelaDeSimbolos e = LASemanticoUtils.buscarEscopos(escopos, nomeCompleto);
                if (e != null) tipo = e.tipo;
            }
            if (tipo == TipoLA.LITERAL) {
                emitirLinha("gets(" + nomeVar + ");");
            } else {
                String fmt = formatoParaTipo(tipo);
                emitirLinha("scanf(\"" + fmt + "\",&" + nomeVar + ");");
            }
        }
        return null;
    }

    @Override
    public Void visitCmdEscreva(LAParser.CmdEscrevaContext ctx) {
        for (LAParser.ExpressaoContext expr : ctx.expressao()) {
            TipoLA tipo = inferirTipoExpr(expr);
            String textoExpr = gerarExpressao(expr);

            // Caso especial: CADEIA literal direta
            if (ehCadeiaLiteral(expr)) {
                // Remover as aspas duplas externas
                String cadeia = textoExpr;
                if (cadeia.startsWith("\"") && cadeia.endsWith("\"")) {
                    cadeia = cadeia.substring(1, cadeia.length() - 1);
                }
                emitirLinha("printf(\"%s\",\"" + cadeia + "\");");
            } else {
                String fmt = formatoParaTipo(tipo);
                emitirLinha("printf(\"" + fmt + "\"," + textoExpr + ");");
            }
        }
        return null;
    }

    /** Verifica se uma expressão é uma cadeia literal simples. */
    private boolean ehCadeiaLiteral(LAParser.ExpressaoContext expr) {
        try {
            return expr.termo_logico(0).fator_logico(0).parcela_logica()
                    .exp_relacional().exp_aritmetica(0).termo(0).fator(0)
                    .parcela(0).parcela_nao_unario() != null
                    && expr.termo_logico(0).fator_logico(0).parcela_logica()
                    .exp_relacional().exp_aritmetica(0).termo(0).fator(0)
                    .parcela(0).parcela_nao_unario().CADEIA() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Void visitCmdAtribuicao(LAParser.CmdAtribuicaoContext ctx) {
        boolean ponteiro = ctx.getText().startsWith("^");
        String nomeVar = nomeCompletoC(ctx.identificador());
        String nomeBase = ctx.identificador().IDENT(0).getText();
        EntradaTabelaDeSimbolos entrada = LASemanticoUtils.buscarEscopos(escopos, nomeBase);
        TipoLA tipo = entrada != null ? entrada.tipo : TipoLA.INVALIDO;

        // Para campos de struct
        if (ctx.identificador().IDENT().size() > 1) {
            String nomeCompleto = nomeCompletoLA(ctx.identificador());
            EntradaTabelaDeSimbolos e = LASemanticoUtils.buscarEscopos(escopos, nomeCompleto);
            if (e != null) tipo = e.tipo;
        }

        // Para deref de ponteiro, descobrir tipo apontado
        if (ponteiro && entrada != null) {
            String nomeTipoBase = entrada.nomeTipo.replace("^", "");
            TipoLA tipoApontado = LASemanticoUtils.getTipoPorNome(nomeTipoBase);
            if (tipoApontado != TipoLA.NAO_DECLARADO) tipo = tipoApontado;
        }

        String rhs = gerarExpressao(ctx.expressao());

        if (tipo == TipoLA.LITERAL) {
            if (ponteiro) {
                emitirLinha("strcpy(*" + nomeVar + "," + rhs + ");");
            } else {
                emitirLinha("strcpy(" + nomeVar + "," + rhs + ");");
            }
        } else {
            if (ponteiro) {
                emitirLinha("*" + nomeVar + " = " + rhs + ";");
            } else {
                emitirLinha(nomeVar + " = " + rhs + ";");
            }
        }
        return null;
    }

    @Override
    public Void visitCmdSe(LAParser.CmdSeContext ctx) {
        String cond = gerarExpressao(ctx.expressao());
        emitirLinha("if (" + cond + ") {");
        indentacao++;
        // cmds do entao (todos antes de senao)
        int totalCmds = ctx.cmd().size();
        int idxSenao = ctx.SENAO() != null ? encontrarIndiceSenao(ctx) : totalCmds;
        for (int i = 0; i < idxSenao; i++) visit(ctx.cmd(i));
        indentacao--;
        if (ctx.SENAO() != null) {
            emitirLinha("} else {");
            indentacao++;
            for (int i = idxSenao; i < totalCmds; i++) visit(ctx.cmd(i));
            indentacao--;
        }
        emitirLinha("}");
        return null;
    }

    /** A gramática coloca entao e senao cmds na mesma lista; acha onde começa o senao. */
    private int encontrarIndiceSenao(LAParser.CmdSeContext ctx) {
        // O token SENAO existe; cmds depois do "senao" vêm depois do token
        org.antlr.v4.runtime.Token senaoTok = ctx.SENAO().getSymbol();
        List<LAParser.CmdContext> cmds = ctx.cmd();
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).getStart().getTokenIndex() > senaoTok.getTokenIndex()) {
                return i;
            }
        }
        return cmds.size();
    }

    @Override
    public Void visitCmdCaso(LAParser.CmdCasoContext ctx) {
        String expr = gerarExpAritmetica(ctx.exp_aritmetica());
        emitirLinha("switch (" + expr + ") {");
        indentacao++;

        for (LAParser.Item_selecaoContext item : ctx.selecao().item_selecao()) {
            // Expandir todos os intervalos e valores
            for (LAParser.Numero_intervaloContext ni : item.constantes().numero_intervalo()) {
                expandirIntervalo(ni);
            }
            indentacao++;
            for (LAParser.CmdContext cmd : item.cmd()) visit(cmd);
            emitirLinha("break;");
            indentacao--;
        }

        if (ctx.SENAO() != null && !ctx.cmd().isEmpty()) {
            emitirLinha("default:");
            indentacao++;
            for (LAParser.CmdContext cmd : ctx.cmd()) visit(cmd);
            indentacao--;
        }

        indentacao--;
        emitirLinha("}");
        return null;
    }

    /** Emite case labels para um numero_intervalo (pode ser range a..b). */
    private void expandirIntervalo(LAParser.Numero_intervaloContext ni) {
        List<org.antlr.v4.runtime.tree.TerminalNode> nums = ni.NUM_INT();
        boolean temRange = ni.PONTO_PONTO() != null;

        // Determinar início e fim do intervalo
        int inicio, fim;
        try {
            if (!temRange) {
                // valor único: [op_unario?] NUM_INT
                int v = Integer.parseInt(nums.get(0).getText());
                if (!ni.op_unario().isEmpty()) v = -v;
                inicio = v; fim = v;
            } else {
                // intervalo: NUM_INT '..' NUM_INT
                int v1 = Integer.parseInt(nums.get(0).getText());
                int v2 = Integer.parseInt(nums.get(1).getText());
                if (ni.op_unario().size() >= 1) v1 = -v1;
                if (ni.op_unario().size() >= 2) v2 = -v2;
                inicio = v1; fim = v2;
            }
        } catch (Exception e) {
            inicio = 0; fim = 0;
        }
        for (int i = inicio; i <= fim; i++) {
            emitirLinha("case " + i + ":");
        }
    }

    @Override
    public Void visitCmdPara(LAParser.CmdParaContext ctx) {
        String var = ctx.IDENT().getText();
        String inicio = gerarExpAritmetica(ctx.exp_aritmetica(0));
        String fim = gerarExpAritmetica(ctx.exp_aritmetica(1));
        emitirLinha("for (" + var + " = " + inicio + "; " + var + " <= " + fim + "; " + var + "++) {");
        indentacao++;
        for (LAParser.CmdContext cmd : ctx.cmd()) visit(cmd);
        indentacao--;
        emitirLinha("}");
        return null;
    }

    @Override
    public Void visitCmdEnquanto(LAParser.CmdEnquantoContext ctx) {
        String cond = gerarExpressao(ctx.expressao());
        emitirLinha("while (" + cond + ") {");
        indentacao++;
        for (LAParser.CmdContext cmd : ctx.cmd()) visit(cmd);
        indentacao--;
        emitirLinha("}");
        return null;
    }

    @Override
    public Void visitCmdFaca(LAParser.CmdFacaContext ctx) {
        emitirLinha("do {");
        indentacao++;
        for (LAParser.CmdContext cmd : ctx.cmd()) visit(cmd);
        indentacao--;
        String cond = gerarExpressao(ctx.expressao());
        // faca...ate cond is "do...while cond" (cond is the continuation condition in C)
        emitirLinha("} while (" + cond + ");");
        return null;
    }

    @Override
    public Void visitCmdChamada(LAParser.CmdChamadaContext ctx) {
        String nome = ctx.IDENT().getText();
        List<String> args = new ArrayList<>();
        for (LAParser.ExpressaoContext expr : ctx.expressao()) {
            args.add(gerarExpressao(expr));
        }
        emitirLinha(nome + "(" + String.join(", ", args) + ");");
        return null;
    }

    @Override
    public Void visitCmdRetorne(LAParser.CmdRetorneContext ctx) {
        emitirLinha("return " + gerarExpressao(ctx.expressao()) + ";");
        return null;
    }

    // ── geração de expressões ─────────────────────────────────────────────────

    private String gerarExpressao(LAParser.ExpressaoContext ctx) {
        List<LAParser.Termo_logicoContext> termos = ctx.termo_logico();
        if (termos.size() == 1) return gerarTermoLogico(termos.get(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < termos.size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(gerarTermoLogico(termos.get(i)));
        }
        return sb.toString();
    }

    private String gerarTermoLogico(LAParser.Termo_logicoContext ctx) {
        List<LAParser.Fator_logicoContext> fatores = ctx.fator_logico();
        if (fatores.size() == 1) return gerarFatorLogico(fatores.get(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fatores.size(); i++) {
            if (i > 0) sb.append(" && ");
            sb.append(gerarFatorLogico(fatores.get(i)));
        }
        return sb.toString();
    }

    private String gerarFatorLogico(LAParser.Fator_logicoContext ctx) {
        String parcela = gerarParcelaLogica(ctx.parcela_logica());
        if (ctx.NAO() != null) {
            // Avoid double-wrapping if already parenthesized
            if (parcela.startsWith("(") && parcela.endsWith(")")) return "!" + parcela;
            return "!(" + parcela + ")";
        }
        return parcela;
    }

    private String gerarParcelaLogica(LAParser.Parcela_logicaContext ctx) {
        if (ctx.VERDADEIRO() != null) return "1";
        if (ctx.FALSO() != null) return "0";
        return gerarExpRelacional(ctx.exp_relacional());
    }

    private String gerarExpRelacional(LAParser.Exp_relacionalContext ctx) {
        String ea1 = gerarExpAritmetica(ctx.exp_aritmetica(0));
        if (ctx.op_relacional() == null) return ea1;
        String op = ctx.op_relacional().getText();
        String ea2 = gerarExpAritmetica(ctx.exp_aritmetica(1));
        String opC;
        switch (op) {
            case "=":  opC = "=="; break;
            case "<>": opC = "!="; break;
            default:   opC = op;
        }
        return ea1 + " " + opC + " " + ea2;
    }

    private String gerarExpAritmetica(LAParser.Exp_aritmeticaContext ctx) {
        StringBuilder sb = new StringBuilder();
        List<LAParser.TermoContext> termos = ctx.termo();
        List<LAParser.Op1Context> ops = ctx.op1();
        sb.append(gerarTermo(termos.get(0)));
        for (int i = 0; i < ops.size(); i++) {
            sb.append(" ").append(ops.get(i).getText()).append(" ");
            sb.append(gerarTermo(termos.get(i + 1)));
        }
        return sb.toString();
    }

    private String gerarTermo(LAParser.TermoContext ctx) {
        StringBuilder sb = new StringBuilder();
        List<LAParser.FatorContext> fatores = ctx.fator();
        List<LAParser.Op2Context> ops = ctx.op2();
        sb.append(gerarFator(fatores.get(0)));
        for (int i = 0; i < ops.size(); i++) {
            sb.append(" ").append(ops.get(i).getText()).append(" ");
            sb.append(gerarFator(fatores.get(i + 1)));
        }
        return sb.toString();
    }

    private String gerarFator(LAParser.FatorContext ctx) {
        StringBuilder sb = new StringBuilder();
        List<LAParser.ParcelaContext> parcelas = ctx.parcela();
        List<LAParser.Op3Context> ops = ctx.op3();
        sb.append(gerarParcela(parcelas.get(0)));
        for (int i = 0; i < ops.size(); i++) {
            sb.append(" ").append(ops.get(i).getText()).append(" ");
            sb.append(gerarParcela(parcelas.get(i + 1)));
        }
        return sb.toString();
    }

    private String gerarParcela(LAParser.ParcelaContext ctx) {
        if (ctx.parcela_unario() != null) {
            String u = gerarParcelaUnario(ctx.parcela_unario());
            if (ctx.op_unario() != null) return "-" + u;
            return u;
        }
        return gerarParcelaNaoUnario(ctx.parcela_nao_unario());
    }

    private String gerarParcelaUnario(LAParser.Parcela_unarioContext ctx) {
        if (ctx.identificador() != null) {
            boolean deref = ctx.CIRCUNFLEXO() != null;
            String nome = nomeCompletoC(ctx.identificador());
            return deref ? "*" + nome : nome;
        }
        if (ctx.IDENT() != null) {
            // chamada de função
            String nome = ctx.IDENT().getText();
            List<String> args = new ArrayList<>();
            for (LAParser.ExpressaoContext e : ctx.expressao()) args.add(gerarExpressao(e));
            return nome + "(" + String.join(", ", args) + ")";
        }
        if (ctx.NUM_INT() != null) return ctx.NUM_INT().getText();
        if (ctx.NUM_REAL() != null) return ctx.NUM_REAL().getText();
        if (ctx.expressao() != null && !ctx.expressao().isEmpty()) {
            return "(" + gerarExpressao(ctx.expressao(0)) + ")";
        }
        return "";
    }

    private String gerarParcelaNaoUnario(LAParser.Parcela_nao_unarioContext ctx) {
        if (ctx.identificador() != null) return "&" + nomeCompletoC(ctx.identificador());
        if (ctx.CADEIA() != null) return ctx.CADEIA().getText();
        return "";
    }

    // ── helpers de nome ───────────────────────────────────────────────────────

    /** Nome completo do identificador para uso em C (struct.campo, var[idx]). */
    private String nomeCompletoC(LAParser.IdentificadorContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.IDENT(0).getText());
        for (int i = 1; i < ctx.IDENT().size(); i++) {
            sb.append(".").append(ctx.IDENT(i).getText());
        }
        // Dimensões (arrays)
        for (LAParser.Exp_aritmeticaContext dim : ctx.dimensao().exp_aritmetica()) {
            sb.append("[").append(gerarExpAritmetica(dim)).append("]");
        }
        return sb.toString();
    }

    /** Nome completo LA para lookup na tabela de símbolos. */
    private String nomeCompletoLA(LAParser.IdentificadorContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.IDENT(0).getText());
        for (int i = 1; i < ctx.IDENT().size(); i++) {
            sb.append(".").append(ctx.IDENT(i).getText());
        }
        return sb.toString();
    }

    // ── inferência de tipo para escreva ───────────────────────────────────────

    private TipoLA inferirTipoExpr(LAParser.ExpressaoContext ctx) {
        return LASemanticoUtils.inferirTipo(escopos, ctx);
    }
}
