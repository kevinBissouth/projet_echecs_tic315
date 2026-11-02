# Build: the whole Maven project, without the tests, which run before deployment.
FROM eclipse-temurin:25-jdk AS construction
WORKDIR /projet
COPY code/ .
RUN ./mvnw -B -q -DskipTests package

# Run: only the Java runtime, Stockfish and the program.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends stockfish && rm -rf /var/lib/apt/lists/*
RUN useradd --create-home echecs
USER echecs
COPY --from=construction /projet/ihm/target/echecs.jar /app/echecs.jar
ENV STOCKFISH=/usr/games/stockfish
# A small server: a simple garbage collector, and a heap that leaves room for Stockfish.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=50 -Xss512k"
EXPOSE 8080
CMD ["java", "-jar", "/app/echecs.jar"]
