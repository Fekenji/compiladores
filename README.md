# Compiladores - UFSCar (2026/01)

Projeto da disciplina de Construção de Compiladores 2026/01 da UFSCar.

## Alunos

- Gabriel Menoni
- Felipe Kenji
- Renan Zago

## Sobre

Este repositório contém um compilador para a linguagem LA, desenvolvido em etapas incrementais. Atualmente implementa o **analisador sintático e léxico (T2)**, responsável por tokenizar programas em LA e realizar a análise sintática, apontando erros (léxicos ou sintáticos) com a respectiva linha e lexema.

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

### Execução Manual

Após compilar, execute o compilador informando o arquivo de entrada (.la) e o arquivo de saída (.txt). Opcionalmente, pode ser utilizada a flag -t1 para forçar a execução no formato de saída do Trabalho 1 (apenas tokens léxicos).

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    [-t1] caminho/entrada.la caminho/saida.txt
```

**Parâmetros:**
- [-t1] -> **(Opcional)** Força a saída de tokens léxicos válidos (Modo T1). Se omitido, o compilador roda no modo silencioso de análise sintática (Modo T2).
- caminho/entrada.la -> Arquivo com o código-fonte em linguagem LA.
- caminho/saida.txt -> Arquivo onde será escrito o resultado da análise.

### Exemplo de Uso (Modo T2 - Sintático)

Comportamento padrão (exigido para a avaliação do T2):

```bash
# A partir da pasta 'compilador'
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    ../testes/etapa2/caso1_entrada.la saida.txt

cat saida.txt
```

**Saída esperada (se não houver erros):**
```
Fim da compilacao
```

**Saída esperada (em caso de erro sintático):**
```
Linha 10: erro sintatico proximo a leia
Fim da compilacao
```

### Exemplo de Uso (Modo T1 - Léxico com Flag)

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    -t1 ../testes/etapa1/caso1_entrada.la saida_lexica.txt
```
Isso fará o compilador imprimir todos os tokens lidos <'lexema','token'> até o final do arquivo ou até o primeiro erro léxico.

## Executar Testes Automatizados

Os casos de teste estão organizados em /testes por etapa (etapa1, etapa2, etc.). O script run-tests.sh automatiza a compilação e execução dos testes chamando o programa com 2 argumentos.

**Nota:** O nosso compilador possui um fallback de segurança que detecta a pasta etapa1 no caminho do arquivo, garantindo que os testes antigos passem automaticamente mesmo sem a flag -t1. O script também detecta e configura JAVA_HOME automaticamente.

### Executar Todos os Testes

```bash
./run-tests.sh
```

Compila o projeto e executa todos os testes de todas as etapas, exibindo o resultado (PASS/FAIL).

### Executar Testes de uma Etapa Específica

Para focar apenas na validação do analisador sintático (T2):

```bash
./run-tests.sh etapa2
```
