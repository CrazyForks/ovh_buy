# OVH自动锁单并通过Bark通知

## java -jar

`在 src/main/resources/application.properties 中配置你的信息即可开始使用，
示例中
PLAN_CODE=25sklec01
OPTIONS=bandwidth-300-25skle ram-32g-ecc-1600-25skle softraid-2x2000sa-25skle
为 抢购 ks-le-c 仅用于验证是否可正常锁单`

## docker

`制作 docker 镜像流程`
ps: 需先配置好src/main/resources/application.properties
1. `mvn clean package`
2. `docker build --platform linux/amd64 -t ovh_buy .`
3. `docker save -o ovh_buy.tar ovh_buy:latest`
4. `将ovh_buy.tar上传到 vps`
5. `在 vps 上执行 docker load -i /pathToTar/ovh_buy.tar`
6. `在 vps 上执行 docker run -d --name ovh_buy ovh_buy:latest`
