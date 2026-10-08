FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --chown=10001:10001 apps/server/build/libs/server.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
