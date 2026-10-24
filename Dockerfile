# Stage 1: Build Spring Boot JAR with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user and staging buffer directory
RUN addgroup -S dropcode && adduser -S dropcode -G dropcode && \
    mkdir -p /app/staging-data && \
    chown -R dropcode:dropcode /app

USER dropcode:dropcode

# Copy built application from build stage
COPY --from=build /app/target/dropcode-backend-1.0.0.jar app.jar

# Render exposes PORT environment variable dynamically
ENV PORT=10000
EXPOSE 10000

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
