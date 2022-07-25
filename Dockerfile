FROM openjdk:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/url-short-0.0.1-SNAPSHOT-standalone.jar /url-short/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/url-short/app.jar"]
