# eclipse-temurin 21-jre-alpine (linux/amd64) — update digest after JRE patch releases
FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN apk upgrade --no-cache

RUN addgroup -S ktor && adduser -S ktor -G ktor

USER ktor
WORKDIR /app

COPY --chown=ktor:ktor build/libs/kdiab-treatments-all.jar /app/kdiab-treatments-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-treatments-backend.jar"]
