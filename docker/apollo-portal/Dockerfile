FROM maven:3.8.6-jdk-8 AS builder
WORKDIR /apollo
COPY . .
RUN /bin/bash scripts/build.sh

ARG VERSION=2.4.0-SNAPSHOT

RUN mkdir /apollo-portal \
    && unzip /apollo/apollo-portal/target/apollo-portal-${VERSION}-github.zip -d /apollo-portal \
    && rm -rf /apollo-portal/apollo-portal-${VERSION}-github.zip \
    && chmod +x /apollo-portal/scripts/startup.sh

FROM openjdk:8-jre-slim
LABEL maintainer="K8sCat <k8scat@gmail.com>"

ENV APOLLO_RUN_MODE="Docker" \
    SERVER_PORT=8070

RUN echo "deb http://mirrors.aliyun.com/debian/ bullseye main non-free contrib" > /etc/apt/sources.list \
    && echo "deb http://mirrors.aliyun.com/debian-security/ bullseye-security main" >> /etc/apt/sources.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends procps curl bash tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /apollo-portal /apollo-portal

EXPOSE $SERVER_PORT

CMD ["/apollo-portal/scripts/startup.sh"]
