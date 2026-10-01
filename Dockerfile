# syntax=docker/dockerfile:1
# JAVA_VERSION must match <java.version> in pom.xml.
ARG JAVA_VERSION=25

FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:${JAVA_VERSION}-jre
RUN useradd --system --uid 10001 app && mkdir -p /data/uploads && chown app /data/uploads
USER app
WORKDIR /app
COPY --from=build /src/target/reimburse-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
