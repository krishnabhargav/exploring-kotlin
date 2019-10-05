FROM openjdk:13-jdk-slim
VOLUME /tmp
COPY build/libs/exploring-kotlin-all.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]