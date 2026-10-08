FROM eclipse-temurin:21-jre
WORKDIR /app
ARG APP_JAR=apps/server/build/libs/apps-server.jar
COPY --chown=10001:10001 ${APP_JAR} app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
