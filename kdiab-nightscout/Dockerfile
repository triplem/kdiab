FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-nightscout-all.jar /app/kdiab-nightscout-backend.jar
EXPOSE 8087
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-nightscout-backend.jar"]
