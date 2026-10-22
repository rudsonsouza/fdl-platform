# syntax=docker/dockerfile:1

# --- Build: Maven (wrapper) + JDK 25 ---------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Camada de dependências separada do código, para o cache do Docker valer entre builds.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Os testes rodam no CI; aqui só empacotamos.
RUN ./mvnw -B -q package -DskipTests && mv target/fdl-backend-*.jar app.jar

# --- Runtime: JRE 25, usuário sem privilégios ------------------------------------------------
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --home-dir /app fdl && mkdir -p /app/data && chown -R fdl /app
WORKDIR /app
COPY --from=build /build/app.jar app.jar

USER fdl
ENV FDL_DB_PATH=/app/data/fdl.db
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
