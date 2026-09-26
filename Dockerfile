FROM eclipse-temurin:24-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

CMD ["sh", "-c", "java -jar target/resume-ai-telegram-bot-0.0.1-SNAPSHOT.jar"]