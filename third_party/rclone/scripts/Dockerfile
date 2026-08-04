# VehicleExpenses librclone build image (Go + NDK + gomobile).
# Source tree is mounted at build time (not cloned here).
FROM ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive
ENV GO_VERSION=1.25.0
ENV SDK_TOOLS_VERSION=11076708

RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    wget \
    unzip \
    curl \
    openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

RUN wget -q https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz && \
    tar -C /usr/local -xzf go${GO_VERSION}.linux-amd64.tar.gz && \
    rm go${GO_VERSION}.linux-amd64.tar.gz
ENV PATH=$PATH:/usr/local/go/bin
ENV GOPATH=/go
ENV PATH=$PATH:$GOPATH/bin

ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-${SDK_TOOLS_VERSION}_latest.zip && \
    unzip -q commandlinetools-linux-${SDK_TOOLS_VERSION}_latest.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm commandlinetools-linux-${SDK_TOOLS_VERSION}_latest.zip
ENV PATH=$PATH:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

RUN yes | sdkmanager --licenses && \
    sdkmanager "platforms;android-30" "build-tools;30.0.3" "ndk;21.4.7075529"
ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/21.4.7075529

RUN go install golang.org/x/mobile/cmd/gomobile@latest && \
    go install golang.org/x/mobile/cmd/gobind@latest && \
    gomobile init

WORKDIR /workspace
