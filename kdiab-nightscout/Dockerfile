# eclipse-temurin 21-jre-alpine (linux/amd64) — update digest after JRE patch releases
FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699
RUN apk upgrade --no-cache
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-nightscout-all.jar /app/kdiab-nightscout-backend.jar
EXPOSE 8087
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-nightscout-backend.jar"]
