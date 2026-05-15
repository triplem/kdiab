FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-nightscout-all.jar /app/kdiab-nightscout-backend.jar
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8087/healthz || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-nightscout-backend.jar"]
