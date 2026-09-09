# R1 live runner: reproducible Forge 1.7.10-1614 proof environment.
#
# This image runs tools/run-live.sh (JDK 8 native: -source/-target, server
# runtime), NOT tools/check.sh etage 1 (which needs a modern javac for
# --release). JAVA8_HOME points at the image JDK.
#
# Build (from a checkout parent holding the 5 sibling repos):
#   docker build -f bridge-1710/tools/live/Dockerfile -t matou-live .
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
# No machine paths inside the image: Java 8 + python3 + curl only.
# Pins and SRG auto-discover live in tools/run-live.sh, not here.
FROM eclipse-temurin:8-jdk-jammy
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 curl ca-certificates \
 && rm -rf /var/lib/apt/lists
ENV JAVA8_HOME=/opt/java/openjdk
WORKDIR /w/bridge-1710
CMD ["sh", "tools/run-live.sh"]
