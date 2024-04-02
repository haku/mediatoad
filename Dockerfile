FROM openjdk:8-jdk

RUN mkdir /app

COPY dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar /app/dlnatoad.jar

WORKDIR /app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "dlnatoad.jar"]
