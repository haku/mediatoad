FROM openjdk:22-slim

RUN mkdir /app

RUN apt-get update && \
    apt-get install -y ffmpeg && \
    rm -rf /var/lib/apt/lists/*

COPY mediatoad-1-SNAPSHOT-jar-with-dependencies.jar /app/mediatoad.jar

WORKDIR /app

EXPOSE 8192
ENTRYPOINT ["java", "-jar", "mediatoad.jar", "--port", "8192"]
