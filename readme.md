# java -jar

`在 src/main/resources/application.properties 中配置你的信息即可开始使用`

# docker

`制作 docker 镜像流程`

1. `mvn clean package`
2. `docker build --platform linux/amd64 -t ovh_buy .`
3. `docker save -o ovh_buy.tar ovh_buy:latest`
