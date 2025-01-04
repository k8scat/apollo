# FROM alpine:3.15.5

# ARG VERSION
# ENV VERSION $VERSION

# COPY apollo-configservice-${VERSION}-github.zip /apollo-configservice/apollo-configservice-${VERSION}-github.zip

# RUN unzip /apollo-configservice/apollo-configservice-${VERSION}-github.zip -d /apollo-configservice \
#     && rm -rf /apollo-configservice/apollo-configservice-${VERSION}-github.zip \
#     && chmod +x /apollo-configservice/scripts/startup.sh

FROM maven:3.8.6-jdk-8 AS builder
WORKDIR /apollo
COPY . .
RUN /bin/bash scripts/build.sh

ARG VERSION=2.4.0-SNAPSHOT

RUN mkdir /apollo-configservice \
    && unzip /apollo/apollo-configservice/target/apollo-configservice-${VERSION}-github.zip -d /apollo-configservice \
    && rm -rf /apollo-configservice/apollo-configservice-${VERSION}-github.zip \
    && chmod +x /apollo-configservice/scripts/startup.sh

FROM openjdk:8-jre-slim
LABEL maintainer="K8sCat <k8scat@gmail.com>"

ENV APOLLO_RUN_MODE="Docker" \
    SERVER_PORT=8080

RUN echo "deb http://mirrors.aliyun.com/debian/ bullseye main non-free contrib" > /etc/apt/sources.list \
    && echo "deb http://mirrors.aliyun.com/debian-security/ bullseye-security main" >> /etc/apt/sources.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends procps curl bash tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

COPY --from=0 /apollo-configservice /apollo-configservice

EXPOSE $SERVER_PORT

CMD ["/apollo-configservice/scripts/startup.sh"]
