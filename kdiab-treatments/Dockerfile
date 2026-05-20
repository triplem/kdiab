FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S ktor && adduser -S ktor -G ktor

USER ktor
WORKDIR /app

COPY --chown=ktor:ktor build/libs/kdiab-treatments-all.jar /app/kdiab-treatments-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-treatments-backend.jar"]
