FROM eclipse-temurin:17-jre-alpine

# Add metadata
LABEL maintainer="Chronos Cache"
LABEL description="High-performance intelligent distributed cache system"
LABEL version="1.0"

# Create app directory
WORKDIR /app

# Copy the JAR file
COPY target/Chronos-1.0-SNAPSHOT.jar chronos.jar

# Expose ports
# 6380: Redis protocol (RESP)
# 8080: HTTP API / Metrics
EXPOSE 6380 8080

# JVM options optimized for containerized deployment
ENV JAVA_OPTS="-Xmx2g \
               -Xms2g \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UseCompressedOops \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/heap-dumps \
               -Dcom.sun.management.jmxremote \
               -Dcom.sun.management.jmxremote.port=9010 \
               -Dcom.sun.management.jmxremote.local.only=false \
               -Dcom.sun.management.jmxremote.authenticate=false \
               -Dcom.sun.management.jmxremote.ssl=false"

# Create directory for heap dumps
RUN mkdir -p /app/heap-dumps /app/data

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar chronos.jar"]
