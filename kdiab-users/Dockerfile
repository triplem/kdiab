# eclipse-temurin 21-jre-alpine (linux/amd64) — update digest after JRE patch releases
FROM eclipse-temurin:21-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b
RUN apk upgrade --no-cache libpng
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-users-all.jar /app/kdiab-users-backend.jar
EXPOSE 8088
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-users-backend.jar"]
