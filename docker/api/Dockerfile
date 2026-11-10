# The API image (cleanup spec S-13; W35a plan Task S1). Built where it runs, with BuildKit (Docker Engine 23 or later):
#   docker compose -f docker/test-env/compose.yaml build      (or: docker build -f docker/api/Dockerfile .)
# The build context is the repo root; .dockerignore keeps env files, build output and .git out of it, and the build stage
# copies only what Gradle needs. Tests run in CI, not here. No secret is read at build time: every value comes from the
# environment of the running container. JVM options come from JAVA_TOOL_OPTIONS, set by the compose file.
# Each base image is pinned by version tag and the digest of its multi-arch index (linux/amd64 and linux/arm64).

FROM eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY modules/ modules/
COPY app/ app/
# The Gradle home (wrapper distribution and dependencies) is a cache mount, so it never lands in a layer.
# Only the boot jar is kept; the -plain jar is not runnable.
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew :app:bootJar --no-daemon --console=plain \
 && find app/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /src/app.jar \; \
 && test "$(find app/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | wc -l)" -eq 1

FROM eclipse-temurin:21.0.13_9-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
RUN groupadd --system --gid 10001 sunvibe \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin sunvibe \
 && install -d -o 0 -g 0 -m 0755 /opt/sunvibe
# Owned by root and read-only: the process cannot change its own code. The directory exists first, or COPY --chmod would
# create it without the execute bit and the user could not reach the jar.
COPY --from=build --chown=0:0 --chmod=0444 /src/app.jar /opt/sunvibe/app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/opt/sunvibe/app.jar"]
