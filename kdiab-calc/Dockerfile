FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache libpng
RUN addgroup -S ktor && adduser -S ktor -G ktor
USER ktor
WORKDIR /app
COPY --chown=ktor:ktor build/libs/kdiab-calc-all.jar /app/kdiab-calc-backend.jar
EXPOSE 8086
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/kdiab-calc-backend.jar"]
