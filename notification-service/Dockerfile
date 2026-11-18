FROM eclipse-temurin:21-jre
WORKDIR /app
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.9.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
