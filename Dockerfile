# 后端构建
FROM maven:3.8-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# 运行镜像
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/z-brain.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
