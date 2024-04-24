FROM eclipse-temurin:21
RUN mkdir /opt/app
COPY target/translation-preview-demo-1.0-SNAPSHOT.jar /opt/app/app.jar
EXPOSE 8090
CMD ["java", "-jar", "/opt/app/app.jar"]
