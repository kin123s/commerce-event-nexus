# Order Service 빌드 스테이지
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# pom.xml 복사 및 의존성 다운로드 (캐싱 최적화)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 소스 코드 복사 및 빌드
COPY src ./src
RUN mvn clean package -DskipTests

# 실행 스테이지
FROM eclipse-temurin:17-jre
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/target/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
