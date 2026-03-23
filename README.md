# Compiladores - UFSCar (2026/01)

Projeto da disciplina de Construção de Compiladores 2026/01 da UFSCar.

## Alunos:

Gabriel Menoni
Felipe Kenji
Renan Zago

## Sobre

Este repositório contém um compilador (até o momento apenas a etapa de analisador léxico) para a linguagem LA.

## Pré-requisitos e como rodar

- Java 11
- Maven 3.8

1. Entre na pasta do projeto Maven:

2. Compile o projeto:

```bash
mvn clean package
```

Caso aparecer erro de `JAVA_HOME`, configure a variável antes de rodar o Maven:

```bash
export JAVA_HOME=/caminho/do/seu/java
export PATH="$JAVA_HOME/bin:$PATH"
```

3. Execute o compilador informando arquivo de entrada e arquivo de saída:

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    caminho/entrada.la caminho/saida.txt
```

## Exemplo

```bash
java -jar target/la-lexico-1.0-SNAPSHOT-jar-with-dependencies.jar \
    ../exemplos/entrada.la ../exemplos/saida.txt
```

Após a execução, o resultado da análise léxica será escrito no arquivo de saída.
