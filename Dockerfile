# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the jar (skip tests for speed)
RUN mvn -B -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# Create non‑root user
ARG USER=appuser
RUN addgroup -S $USER && adduser -S -G $USER $USER
COPY --from=build /app/target/*.jar app.jar
RUN chown $USER:$USER app.jar
USER $USER
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

