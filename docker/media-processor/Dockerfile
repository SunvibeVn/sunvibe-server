# The isolated survey-media processor (ADR-011 decision 4, form (a); survey-media spec 4.5, A-27, A-28; plan PD-1).
# It holds no secret and gets no egress: the API sends source bytes over a private channel and gets variants back.
# A supervisor starts one child per run with OS limits; the decoders (libvips with libheif and the libde265 HEVC
# plugin, libjpeg-turbo, libspng/libpng, libwebp) run only in those children, each run slot as its own non-root uid
# (10101 and up) without capabilities in its own 0700 directory of a tmpfs work area (security review M-1, SV-069).
# The supervisor starts as root so it can give each slot its uid; compose.yaml leaves it SETUID, SETGID, CHOWN and KILL.
# Base and packages are pinned; a Debian security update replaces the pins (rebuild, new digest, new processor.version).
FROM debian:trixie-slim@sha256:f6b08b09a05725f9431502d3b5714f5c4576e82c1a84fa1debe0c24a7e0c3ec4

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      libvips-tools=8.16.1-1+deb13u2 \
      libvips42t64=8.16.1-1+deb13u2 \
      libheif-plugin-libde265=1.23.6-2~deb13u1 \
      python3=3.13.5-1 \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin svproc \
 && mkdir -p /work \
 && chown 10001:10001 /work \
 && chmod 0700 /work

COPY supervisor/ /opt/sv/

# One thread per decode, untrusted loaders (ImageMagick, Matlab, ...) blocked, bounded malloc arenas, temp files in
# the work area only.
ENV VIPS_CONCURRENCY=1 \
    VIPS_BLOCK_UNTRUSTED=1 \
    MALLOC_ARENA_MAX=2 \
    TMPDIR=/work \
    PYTHONDONTWRITEBYTECODE=1

USER 0:0
EXPOSE 7300
ENTRYPOINT ["python3", "-I", "/opt/sv/supervisor.py"]
CMD ["--listen", "tcp:0.0.0.0:7300"]
