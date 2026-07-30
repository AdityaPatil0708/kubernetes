# No javac, no Maven: `java App.java` uses the single-file source launcher (JEP 330).
# Cost is a JDK base image (~450 MB) instead of a JRE. See README "Tradeoffs".
FROM eclipse-temurin:25-jdk

WORKDIR /app
COPY App.java .

RUN useradd --uid 1000 --create-home appuser
USER appuser

EXPOSE 8080
CMD ["java", "App.java"]
