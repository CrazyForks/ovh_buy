FROM openjdk:8-jre-alpine

WORKDIR /app

# 复制 JAR 文件
COPY target/ovh_buy-1.0-SNAPSHOT.jar /app/ovh_buy.jar

# 复制配置文件
COPY src/main/resources/application.properties /app/application.properties

CMD ["java", "-jar", "ovh_buy.jar"]
