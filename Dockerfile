# Etapa 1: compilar con Maven (JDK 21)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# Etapa 2: ejecutar solo con el JRE 21, sin usuario root
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/ms-cleanfresh-mq-admin-*.jar app.jar
USER appuser
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
