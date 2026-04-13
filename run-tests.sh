#!/bin/bash

# Script para executar testes do compilador LA
# Uso: ./run-tests.sh [etapa|corretor]
# Exemplos:
#   ./run-tests.sh          # Executa testes locais (testComp: etapa1..etapa4)
#   ./run-tests.sh etapa1   # Executa apenas testes da etapa1
#   ./run-tests.sh corretor # Executa o corretor oficial

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/compilador"
TESTES_DIR="$SCRIPT_DIR/testes"
JAR_FILE="$PROJECT_DIR/target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar"
CORRETOR_JAR_WITH_EXT="$SCRIPT_DIR/compiladores-corretor-automatico-1.0-SNAPSHOT-jar-with-dependencies.jar"
CORRETOR_JAR_NO_EXT="$SCRIPT_DIR/compiladores-corretor-automatico-1.0-SNAPSHOT-jar-with-dependencies"
CORRETOR_JAR_TESTES_WITH_EXT="$TESTES_DIR/compiladores-corretor-automatico-1.0-SNAPSHOT-jar-with-dependencies.jar"
CORRETOR_JAR_TESTES_NO_EXT="$TESTES_DIR/compiladores-corretor-automatico-1.0-SNAPSHOT-jar-with-dependencies"

# Auto-detectar JAVA_HOME se não estiver definido ou estiver inválido
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    if command -v java &> /dev/null; then
        JAVA_BIN=$(readlink -f "$(command -v java)")
        JAVA_HOME=$(dirname "$(dirname "$JAVA_BIN")")
        export JAVA_HOME
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Função para imprimir seções
print_header() {
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}========================================${NC}"
}

# Função para compilar o projeto
compile_project() {
    print_header "Compilando projeto..."
    cd "$PROJECT_DIR"
    mvn clean package -q -DskipTests || {
        echo -e "${RED}Erro ao compilar o projeto${NC}"
        if [ -n "$JAVA_HOME" ]; then
            echo "JAVA_HOME atual: $JAVA_HOME"
        else
            echo "JAVA_HOME atual: (não definido)"
        fi
        exit 1
    }
    cd "$SCRIPT_DIR"
    echo -e "${GREEN}✓ Projeto compilado com sucesso${NC}"
    echo ""
}

# Função para executar um teste
run_test() {
    local etapa=$1
    local entrada=$2
    local esperada=$3
    local extra_flag=""
    
    local saida_temp="/tmp/test_output_$$.txt"
    local test_name=$(basename "$entrada" .la)
    
    # Etapa1 deve rodar em modo lexico explicito (-t1), conforme README.
    if [ "$etapa" = "etapa1" ]; then
        extra_flag="-t1"
    fi

    # Executa o compilador
    java -jar "$JAR_FILE" $extra_flag "$entrada" "$saida_temp" 2>/dev/null
    
    # Compara a saída
    if diff -q "$esperada" "$saida_temp" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ PASS${NC}: $etapa/$test_name"
        rm -f "$saida_temp"
        return 0
    else
        echo -e "${RED}✗ FAIL${NC}: $etapa/$test_name"
        echo "  Esperado:"
        head -3 "$esperada" | sed 's/^/    /'
        echo "  ---"
        echo "  Obtido:"
        head -3 "$saida_temp" | sed 's/^/    /'
        rm -f "$saida_temp"
        return 1
    fi
}

map_etapa_para_testcomp() {
    case "$1" in
        etapa1) echo "1.casos_teste_t1" ;;
        etapa2) echo "2.casos_teste_t2" ;;
        etapa3) echo "3.casos_teste_t3" ;;
        etapa4) echo "4.casos_teste_t4" ;;
        etapa5) echo "5.casos_teste_t5" ;;
        *) echo "" ;;
    esac
}

# Função para executar testes de uma etapa
run_etapa() {
    local etapa=$1
    local testcomp_case
    local etapa_dir
    local entrada_dir
    local saida_dir

    testcomp_case="$(map_etapa_para_testcomp "$etapa")"
    etapa_dir="$TESTES_DIR/testComp/$testcomp_case"
    entrada_dir="$etapa_dir/entrada"
    saida_dir="$etapa_dir/saida"

    if [ "$etapa" = "etapa5" ]; then
        echo -e "${YELLOW}Aviso:${NC} etapa5 no modo local requer pipeline completo do corretor oficial."
        echo "Use: ./run-tests.sh corretor"
        return 1
    fi
    
    if [ -z "$testcomp_case" ] || [ ! -d "$entrada_dir" ] || [ ! -d "$saida_dir" ]; then
        echo -e "${RED}Etapa '$etapa' não encontrada${NC}"
        return 1
    fi
    
    print_header "Executando testes de $etapa"
    
    local passed=0
    local failed=0
    local test_count=0
    
    # Usa os casos do testComp: entrada/ e saida/ com mesmos nomes de arquivo
    for entrada in "$entrada_dir"/*; do
        if [ -f "$entrada" ]; then
            test_count=$((test_count + 1))
            local case_name
            local esperada
            case_name=$(basename "$entrada")
            esperada="$saida_dir/$case_name"
            
            if [ ! -f "$esperada" ]; then
                echo -e "${RED}✗ FAIL${NC}: $etapa/$case_name (arquivo de saída esperada não encontrado)"
                failed=$((failed + 1))
            else
                if run_test "$etapa" "$entrada" "$esperada"; then
                    passed=$((passed + 1))
                else
                    failed=$((failed + 1))
                fi
            fi
        fi
    done
    
    if [ $test_count -eq 0 ]; then
        echo -e "${YELLOW}Nenhum teste encontrado em $etapa${NC}"
    else
        echo ""
        echo -e "Resultados: ${GREEN}$passed passou${NC}, ${RED}$failed falharam${NC} (total: $test_count)"
        echo ""
    fi
    
    return $([ $failed -eq 0 ] && echo 0 || echo 1)
}

# Função para executar o corretor oficial
run_corretor_oficial() {
    print_header "Executando corretor oficial"

    local corretor_jar=""
    local gcc_path=""
    local pasta_temp="$TESTES_DIR/tempo"
    local pasta_casos="$TESTES_DIR/testComp"
    local ras="815773, 821158, 821302"
    local log_corretor="/tmp/corretor_output_$$.log"
    local compilador_exec=""

    if [ -f "$CORRETOR_JAR_WITH_EXT" ]; then
        corretor_jar="$CORRETOR_JAR_WITH_EXT"
    elif [ -f "$CORRETOR_JAR_TESTES_WITH_EXT" ]; then
        corretor_jar="$CORRETOR_JAR_TESTES_WITH_EXT"
    elif [ -f "$CORRETOR_JAR_NO_EXT" ]; then
        corretor_jar="$CORRETOR_JAR_NO_EXT"
    elif [ -f "$CORRETOR_JAR_TESTES_NO_EXT" ]; then
        corretor_jar="$CORRETOR_JAR_TESTES_NO_EXT"
    else
        echo -e "${RED}Arquivo do corretor oficial não encontrado.${NC}"
        echo "  Procurado em:"
        echo "    $CORRETOR_JAR_WITH_EXT"
        echo "    $CORRETOR_JAR_NO_EXT"
        echo "    $CORRETOR_JAR_TESTES_WITH_EXT"
        echo "    $CORRETOR_JAR_TESTES_NO_EXT"
        return 1
    fi

    gcc_path=$(command -v gcc || true)
    if [ -z "$gcc_path" ]; then
        echo -e "${RED}gcc não encontrado no PATH.${NC}"
        return 1
    fi

    mkdir -p "$pasta_temp"
    rm -f "$pasta_temp"/compilador_exec_*.sh 2>/dev/null || true

    if [ ! -d "$pasta_casos" ]; then
        echo -e "${RED}Pasta de casos do corretor não encontrada: $pasta_casos${NC}"
        return 1
    fi

    # O corretor oficial espera um executavel, nao um .jar diretamente.
    compilador_exec=$(mktemp "$pasta_temp/compilador_exec_XXXXXX.sh")
    cat > "$compilador_exec" << EOF
#!/bin/bash
BASE_DIR="\$(cd "\$(dirname "\$0")/../.." && pwd)"
exec java -jar "\$BASE_DIR/compilador/target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar" "\$@"
EOF
    chmod +x "$compilador_exec"

    local status_tudo=0
    java -jar "$corretor_jar" "$compilador_exec" "$gcc_path" "$pasta_temp" "$pasta_casos" "$ras" "t1 t2 t3 t4 t5" > "$log_corretor" 2>&1 || status_tudo=$?

    cat "$log_corretor"
    rm -f "$compilador_exec"
    rm -f "$log_corretor"
    return $status_tudo
}

# Main
main() {
    compile_project
    
    local total_passed=0
    local total_failed=0
    
    if [ "$1" = "corretor" ]; then
        if [ $# -ne 1 ]; then
            echo -e "${RED}A flag 'corretor' não pode ser combinada com etapa1, etapa2, etc.${NC}"
            echo "Uso: ./run-tests.sh corretor"
            exit 1
        fi
        run_corretor_oficial
    elif [ -z "$1" ]; then
        # Executa etapas locais baseadas em testComp
        print_header "Executando testes locais (testComp)"

        for etapa in etapa1 etapa2 etapa3 etapa4; do
            if ! run_etapa "$etapa"; then
                total_failed=$((total_failed + 1))
            else
                total_passed=$((total_passed + 1))
            fi
        done
        
        print_header "Resumo Final"
        echo -e "Etapas com sucesso: ${GREEN}$total_passed${NC}"
        echo -e "Etapas com falha: ${RED}$total_failed${NC}"
    else
        if [ $# -ne 1 ]; then
            echo -e "${RED}Uso inválido.${NC}"
            echo "Use apenas um argumento: etapaX OU corretor"
            exit 1
        fi
        if [ "$1" != "etapa1" ] && [ "$1" != "etapa2" ] && [ "$1" != "etapa3" ] && [ "$1" != "etapa4" ] && [ "$1" != "etapa5" ]; then
            echo -e "${RED}Etapa inválida: $1${NC}"
            echo "Use: etapa1, etapa2, etapa3, etapa4, etapa5 ou corretor"
            exit 1
        fi
        # Executa apenas a etapa especificada
        run_etapa "$1"
    fi
}

# Verifica se o JAR existe
if [ ! -f "$JAR_FILE" ]; then
    compile_project
fi

main "$@"
