# ==================== Stage 1: Build ====================
# Uses Maven + JDK to compile the project and create a JAR file.
# This stage includes all build tools (Maven, JDK, dependencies) — ~500MB.
# We don't want this in the final image.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first — Docker caches this layer.
# If only source code changes, Docker skips re-downloading dependencies.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source code and build the JAR (skip tests — they ran in CI)
COPY src ./src
RUN mvn package -DskipTests -B

# ==================== Stage 2: Run ====================
# Uses only JRE (no JDK, no Maven) — much smaller image (~200MB vs ~500MB).
# Only the compiled JAR is copied from the build stage.

FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port this service runs on
EXPOSE 8081

# Run the application
# -Djava.security.egd: faster startup (uses non-blocking random source)
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
