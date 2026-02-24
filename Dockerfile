# STAGE 1: Build the app
# Maven image with Java 17
FROM maven:3.9-eclipse-temurin-17-noble AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the jar (skip tests for faster build)
RUN mvn clean package -DskipTests

# STAGE 2: Run the app
# Use minimal Java 17 runtime
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# Copy the jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Start the service
ENTRYPOINT ["java", "-jar", "app.jar"]