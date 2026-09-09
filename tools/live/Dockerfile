# R1 live runner: reproducible Forge 1.7.10-1614 proof environment.
#
# This image runs tools/run-live.sh (JDK 8 native: -source/-target, server
# runtime), NOT tools/check.sh stage 1 (which needs a modern javac for
# --release). JAVA8_HOME points at the image JDK.
#
# R3: the image runs as a non-root `builder` user so bind-mounted checkouts
# (B3_DIR, /w) keep host ownership — pre-R3 images wrote build/ as root and
# poisoned the shared cache. Match the host user at build time
# (--build-arg UID/GID below); a conflicting id fails the build loudly,
# never silently.
#
# Build (from a checkout parent holding the 5 sibling repos):
#   docker build -f bridge-1710/tools/live/Dockerfile \
#     --build-arg UID=$(id -u) --build-arg GID=$(id -g) -t matou-live .
#
# First run (network once to provision B3_DIR cache):
#   docker run --rm -v "$PWD:/w" -v matou-b3:/tmp/matou-b3-live \
#     -v "$HOME/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/srgs/srg-mcp.srg:/srg/srg-mcp.srg:ro" \
#     -e SRG_MCP=/srg/srg-mcp.srg -w /w/bridge-1710 matou-live
#
# Offline re-run (cache already provisioned):
#   docker run --rm -v "$PWD:/w" -v matou-b3:/tmp/matou-b3-live \
#     -v "$HOME/.gradle/.../srgs/srg-mcp.srg:/srg/srg-mcp.srg:ro" \
#     -e SRG_MCP=/srg/srg-mcp.srg -e B3_OFFLINE=1 \
#     -w /w/bridge-1710 matou-live
#
# Pre-R3 root-owned `build/` leftovers in an old cache must be cleared once
# (rm or fresh B3_DIR): run-live.sh refuses to build over them loudly.
#
# No machine paths inside the image: Java 8 + python3 + curl + git only
# (git serves the R2 release guards: VERSION + clean-tree checks).
# Pins and SRG auto-discover live in tools/run-live.sh, not here.
FROM eclipse-temurin:8-jdk-jammy
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 curl ca-certificates git \
 && rm -rf /var/lib/apt/lists
ENV JAVA8_HOME=/opt/java/openjdk
ARG UID=1000
ARG GID=1000
RUN groupadd -g "$GID" builder \
 && useradd -m -u "$UID" -g "$GID" -s /bin/bash builder \
 && mkdir -p /w/bridge-1710 && chown builder:builder /w /w/bridge-1710
USER builder
WORKDIR /w/bridge-1710
CMD ["sh", "tools/run-live.sh"]
