# ---- Stage 1: 빌드 ----
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Stage 2: 실행 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# ffmpeg(ffprobe) + 영상 화질 readiness 프레임 샘플링 (video_readiness.py)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    fontconfig \
    fonts-noto-cjk \
    python3 \
    python3-pip \
    libglib2.0-0 \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*

COPY scripts/readiness/requirements.txt /tmp/readiness-requirements.txt
RUN pip3 install --no-cache-dir --break-system-packages -r /tmp/readiness-requirements.txt \
    && rm /tmp/readiness-requirements.txt

RUN mkdir -p /app/readiness \
    && groupadd -r spring \
    && useradd -r -g spring spring

COPY scripts/readiness/video_readiness.py /app/readiness/video_readiness.py
RUN chown -R spring:spring /app/readiness

USER spring:spring
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
