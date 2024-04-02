FROM openjdk:22-slim

RUN mkdir /app

RUN apt-get update && \
    apt-get install -y ffmpeg && \
    rm -rf /var/lib/apt/lists/*

COPY dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar /app/dlnatoad.jar

WORKDIR /app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "dlnatoad.jar"]
