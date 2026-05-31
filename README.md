# Compiladores - UFSCar (2026/01)

Projeto da disciplina de Construção de Compiladores 2026/01 da UFSCar.

## Alunos

- Gabriel Menoni
- Felipe Kenji
- Renan Zago

## Sobre

Este repositório contém um compilador completo para a linguagem LA, desenvolvido em etapas incrementais:

- **T1** — Analisador léxico (tokenização)
- **T2** — Analisador sintático
- **T3/T4** — Analisador semântico (identificadores duplicados/não declarados, tipos incompatíveis, retorne fora de função, erros em chamadas de função, etc.)
- **T5** — Gerador de código C: produz código C compilável e executável equivalente ao programa LA de entrada

## Pré-requisitos

Certifique-se de ter os seguintes programas e versões instalados:

### Java

- **Versão mínima:** Java 11 ou superior
- **Como verificar:**
```bash
java -version
```

### Maven

- **Versão mínima:** Maven 3.8.0 ou superior
- **Como verificar:**
```bash
mvn -version
```

## Configuração de JAVA_HOME

Se aparecer erro ao compilar dizendo `JAVA_HOME environment variable is not defined correctly`, execute o seguinte script:

```bash
export JAVA_HOME=$(readlink -f /usr/bin/java | xargs dirname | xargs dirname)
export PATH="$JAVA_HOME/bin:$PATH"

echo $JAVA_HOME
java -version
```

## Como Compilar

1. Clone e acesse o repositório.

2. Acesse a pasta do projeto Maven:
   ```bash
   cd compilador
   ```

3. Compile o projeto (gera o JAR executável):
   ```bash
   mvn clean package
   ```

## Como Executar o Compilador

### Pré-requisito adicional para T5

O gerador de código (T5) produz um arquivo `.c` que precisa ser compilado com GCC:

```bash
gcc --version   # qualquer versão recente serve
```

### Execução Manual

Após compilar, execute o compilador informando o arquivo de entrada e o arquivo de saída. São dois argumentos obrigatórios; a flag opcional `-t1` ou `-t2` seleciona o modo léxico/sintático.

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    [-t1|-t2] caminho/entrada.la caminho/saida.txt
```

**Parâmetros:**
- `[-t1]` → **(Opcional)** Emite tokens léxicos (Modo T1).
- `[-t2]` → **(Opcional)** Valida a sintaxe (Modo T2).
- Se omitido, o compilador roda no modo padrão (T5): se houver erros léxicos/sintáticos/semânticos emite os erros; caso contrário, emite o código C gerado.
- `caminho/entrada.la` → Arquivo com o código-fonte em linguagem LA.
- `caminho/saida.txt` → Arquivo onde será escrito o resultado.

### Exemplo de Uso (Modo T5 — Geração de Código C)

Comportamento padrão quando o programa LA não tem erros:

```bash
# A partir da pasta 'compilador'
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    ../testes/testComp/5.casos_teste_t5/1.entrada/1.declaracao_leitura_impressao_inteiro.alg \
    saida.c

cat saida.c
```

**Saída produzida:**
```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main() {
    int x;
    scanf("%d",&x);
    printf("%d",x);
    return 0;
}
```

Para compilar e executar o código gerado:

```bash
gcc -o programa saida.c
echo "4" | ./programa   # → imprime: 4
```

### Exemplo de Uso (Modo T3/T4 — Erros Semânticos)

Quando o programa LA contém erros, o compilador os reporta (em vez de gerar código):

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    ../testes/testComp/3.casos_teste_t3/entrada/1.algoritmo_2-2_apostila_LA.txt saida.txt

cat saida.txt
```

**Saída esperada:**
```
Linha 7: tipo inteir nao declarado
Linha 11: identificador idades nao declarado
Fim da compilacao
```

### Exemplo de Uso (Modo T2 — Análise Sintática)

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    -t2 ../testes/testComp/2.casos_teste_t2/entrada/1-algoritmo_2-2_apostila_LA_1_erro_linha_3_acusado_linha_10.txt saida.txt

cat saida.txt
# → Linha 10: erro sintatico proximo a leia
#   Fim da compilacao
```

### Exemplo de Uso (Modo T1 — Análise Léxica)

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    -t1 ../testes/testComp/1.casos_teste_t1/entrada/1-algoritmo_2-2_apostila_LA.txt saida.txt

cat saida.txt
# → lista de tokens no formato <'lexema','TOKEN'>
```

## Executar Testes Automatizados

Os casos de teste utilizados pelo script estão em /testes/testComp, separados por tipo (T1..T5). O script run-tests.sh automatiza a compilação e execução.

**Nota:** O script detecta e configura JAVA_HOME automaticamente quando necessário. Para T1, ele executa o compilador com a flag -t1.

### Executar Todos os Testes

```bash
./run-tests.sh
```

Compila o projeto e executa os testes locais de T1 a T5, exibindo o resultado (PASS/FAIL).

### Executar Testes de uma Etapa Específica

```bash
./run-tests.sh etapa2   # apenas T2
./run-tests.sh etapa5   # apenas T5
```

Etapas válidas no modo local: `etapa1`, `etapa2`, `etapa3`, `etapa4`, `etapa5`.

> **Nota sobre T5:** o script compila o código C gerado com GCC e compara a entrada/saída com o esperado. Requer `gcc` instalado.

### Executar Corretor Original

Corretor original utilizado na correção, criado pelo professor Lucrédio.

```bash
./run-tests.sh corretor
```