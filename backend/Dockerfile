# Stage 1: Build
FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /build
# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn package -Dmaven.test.skip=true -B

# Stage 2: Runtime
FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# Expose port and run
EXPOSE 8080
ENV TZ=Asia/Shanghai
CMD ["java", "-jar", "app.jar"]
