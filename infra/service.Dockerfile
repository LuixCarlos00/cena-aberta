FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY services ./services
ARG SERVICE
RUN mvn -B -pl services/${SERVICE} -am package -DskipTests && cp services/${SERVICE}/target/*.jar /service.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /service.jar /app/service.jar
USER 10001
ENTRYPOINT ["java","-jar","/app/service.jar"]
