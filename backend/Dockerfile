# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 阿里云 Maven 镜像（服务器在 cn-hangzhou，直连 Maven Central 会卡死，见 settings.xml）
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
# 先拉依赖缓存层
RUN mvn -q dependency:go-offline || true
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 时区（Asia/Shanghai）
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" > /etc/timezone
COPY --from=build /app/target/learn-notes-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
