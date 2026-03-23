# Compiladores - UFSCar (2026/01)

Projeto da disciplina de Construção de Compiladores 2026/01 da UFSCar.

## Alunos

- Gabriel Menoni
- Felipe Kenji
- Renan Zago

## Sobre

Este repositório contém um compilador para a linguagem LA, desenvolvido em etapas incrementais. Atualmente implementa o **analisador léxico** (T1) responsável por tokenizar programas em LA.

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

1. Clone e acesse o repositório:

2. Acesse a pasta do projeto Maven:
   ```bash
   cd compilador
   ```

3. Compile o projeto (gera JAR executável):
   ```bash
   mvn clean package
   ```

## Como Executar o Compilador

### Execução Manual

Após compilar, execute o compilador informando arquivo de entrada (.la) e arquivo de saída (.txt):

```bash
cd compilador

java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    caminho/entrada.la caminho/saida.txt
```

**Parâmetros:**
- `caminho/entrada.la` → Arquivo com código em linguagem LA
- `caminho/saida.txt` → Arquivo onde será escrito o resultado (tokens ou erros)

### Exemplo de Uso

```bash
# A partir da raiz do projeto
cd compilador

java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    ../testes/etapa1/caso1_entrada.la saida.txt

cat saida.txt
```

**Saída esperada:**
```
<'algoritmo','algoritmo'>
```

## Executar Testes Automatizados

Os casos de teste estão organizados em `/testes` por etapa (etapa1, etapa2, etapa3, etc.). O script `run-tests.sh` automatiza a compilação e execução dos testes.


**Nota:** O script `run-tests.sh` detecta e configura `JAVA_HOME` automaticamente.

### Executar Todos os Testes

```bash
./run-tests.sh
```

Compila o projeto e executa todos os testes de todas as etapas, exibindo resultado (PASS/FAIL).

### Executar Testes de uma Etapa Específica

```bash
./run-tests.sh etapa1   # Testa apenas etapa1
./run-tests.sh etapa2   # Testa apenas etapa2
./run-tests.sh etapa3   # Testa apenas etapa3
```

### Resultado dos Testes

Cada teste exibe:
- **PASS** → Saída do compilador corresponde à esperada
- **FAIL** → Saída diferente da esperada (mostra primeiras linhas)