#!/bin/bash

# Script para executar testes do compilador LA
# Uso: ./run-tests.sh [etapa]
# Exemplos:
#   ./run-tests.sh          # Executa todos os testes
#   ./run-tests.sh etapa1   # Executa apenas testes da etapa1

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/compilador"
TESTES_DIR="$SCRIPT_DIR/testes"
JAR_FILE="$PROJECT_DIR/target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar"

# Auto-detectar JAVA_HOME se não estiver definido
if [ -z "$JAVA_HOME" ]; then
    if command -v java &> /dev/null; then
        JAVA_HOME=$(readlink -f "$(which java)" | xargs dirname | xargs dirname)
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
    mvn clean package -q -DskipTests 2>/dev/null || {
        echo -e "${RED}Erro ao compilar o projeto${NC}"
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
    
    local saida_temp="/tmp/test_output_$$.txt"
    local test_name=$(basename "$entrada" .la)
    
    # Executa o compilador
    java -jar "$JAR_FILE" "$entrada" "$saida_temp" 2>/dev/null
    
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

# Função para executar testes de uma etapa
run_etapa() {
    local etapa=$1
    local etapa_dir="$TESTES_DIR/$etapa"
    
    if [ ! -d "$etapa_dir" ]; then
        echo -e "${RED}Etapa '$etapa' não encontrada${NC}"
        return 1
    fi
    
    print_header "Executando testes de $etapa"
    
    local passed=0
    local failed=0
    local test_count=0
    
    # Procura por arquivos de entrada (_entrada.la)
    for entrada in "$etapa_dir"/*_entrada.la; do
        if [ -f "$entrada" ]; then
            test_count=$((test_count + 1))
            # Extrai o nome do caso (remove _entrada.la)
            local case_name=$(basename "$entrada" _entrada.la)
            local esperada="$etapa_dir/${case_name}_saida_esperada.txt"
            
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

# Main
main() {
    compile_project
    
    local total_passed=0
    local total_failed=0
    
    if [ -z "$1" ]; then
        # Executa todas as etapas
        print_header "Executando TODOS os testes"
        
        for etapa_dir in "$TESTES_DIR"/etapa*; do
            if [ -d "$etapa_dir" ]; then
                local etapa=$(basename "$etapa_dir")
                if ! run_etapa "$etapa"; then
                    total_failed=$((total_failed + 1))
                else
                    total_passed=$((total_passed + 1))
                fi
            fi
        done
        
        print_header "Resumo Final"
        echo -e "Etapas com sucesso: ${GREEN}$total_passed${NC}"
        echo -e "Etapas com falha: ${RED}$total_failed${NC}"
    else
        # Executa apenas a etapa especificada
        run_etapa "$1"
    fi
}

# Verifica se o JAR existe
if [ ! -f "$JAR_FILE" ]; then
    compile_project
fi

main "$@"
