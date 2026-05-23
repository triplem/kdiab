# eclipse-temurin 21-jre-alpine (linux/amd64) — update digest after JRE patch releases
FROM eclipse-temurin:21-jre-alpine@sha256:089ffb495d17108fd0a9f3f05a87d20e4b37ceea2db3fb55c07d4283b9bebe7d
RUN apk upgrade --no-cache libpng
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-calc-all.jar /app/kdiab-calc-backend.jar
EXPOSE 8086
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-calc-backend.jar"]
