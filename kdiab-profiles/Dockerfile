FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache libpng

RUN addgroup -S ktor && adduser -S ktor -G ktor

USER ktor
WORKDIR /app

COPY --chown=ktor:ktor build/libs/kdiab-profiles-all.jar /app/kdiab-profiles-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-profiles-backend.jar"]
