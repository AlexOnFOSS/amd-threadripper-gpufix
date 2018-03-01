FROM openjdk:8-jdk-slim AS build
ADD . /app
WORKDIR /app
RUN javac *.java
RUN jar cfe main.jar ZenBridgeBaconRecovery *.class 

FROM gcr.io/distroless/java
COPY --from=build /app /app
WORKDIR /app
CMD ["main.jar"]
