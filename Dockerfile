# Multi-stage build for Spring Boot backend

# D-M2: pin the minor/patch tag so builds are reproducible.
FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# D-M2: pin the runtime base too, not just the builder. This is the image that actually
# runs in production; a floating `21-jre-alpine` makes the deployed JVM unreproducible.
FROM eclipse-temurin:21.0.5_11-jre-alpine
# H1: run as a dedicated non-root user so an RCE through the wide attack surface
# (source-file reading endpoints, archive import) does not own the container.
# su-exec lets the entrypoint start as root just long enough to fix ownership of
# pre-existing root-owned named volumes, then drop to `app` before the JVM starts.
RUN addgroup -S app && adduser -S -G app app && apk add --no-cache su-exec
WORKDIR /app
# D-M3: explicit jar name (pom.xml sets <finalName>app</finalName>) — a wildcard
# copy breaks as soon as a second jar appears in target/.
COPY --from=builder /build/target/app.jar app.jar
# Fresh named volumes inherit this ownership; the entrypoint fixes older volumes.
RUN mkdir -p /uploads && chown -R app:app /app /uploads
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
