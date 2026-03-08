FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 安装必要工具
RUN apk add --no-cache tzdata
ENV TZ=Asia/Shanghai

# 复制 JAR 文件
COPY target/wechat-openclaw-connector-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
