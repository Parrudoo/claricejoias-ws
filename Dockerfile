# Estágio 1: Build da aplicação (Usando Maven com Java 21)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia o pom e baixa as dependências (ajuda no cache do Docker)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia o código fonte e compila
COPY src ./src
RUN mvn clean package -DskipTests

# Estágio 2: Execução (Imagem mais leve apenas com a JRE do Java 21)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copia o .jar gerado no estágio 1
COPY --from=build /app/target/*.jar app.jar

# Expõe a porta do Spring Boot
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]