# ==========================================
# Stage 1: Build JAR using Maven
# ==========================================
FROM maven:3.9.9-amazoncorretto-17 AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package production JAR
COPY src ./src
RUN mvn clean package -DskipTests -Pprod

# ==========================================
# Stage 2: Production Minimal JRE Runtime
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Set timezone to Asia/Ho_Chi_Minh and install wget for healthcheck
RUN apk add --no-cache tzdata wget && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

# Create non-root user and group (posuser:posgroup)
RUN addgroup -S posgroup && adduser -S posuser -G posgroup

# Create directory for persistent application logs and set ownership
RUN mkdir -p /app/logs && chown -R posuser:posgroup /app

# Default environment configuration
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Copy compiled jar from build stage
COPY --from=build --chown=posuser:posgroup /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Switch to non-root user for security compliance
USER posuser:posgroup

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/backend/actuator/health || exit 1

# Use exec to ensure signals (SIGTERM/SIGINT) pass directly to the JVM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
