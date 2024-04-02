FROM openjdk:22-slim

RUN mkdir /app

COPY dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar /app/dlnatoad.jar

WORKDIR /app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "dlnatoad.jar"]
