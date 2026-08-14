# eclipse-temurin 21-jre-alpine (linux/amd64) — update digest after JRE patch releases
FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
RUN apk upgrade --no-cache libpng libxml2
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-nightscout-all.jar /app/kdiab-nightscout-backend.jar
EXPOSE 8087
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-nightscout-backend.jar"]
