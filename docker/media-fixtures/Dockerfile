# Builds the synthetic survey-media test corpus (survey-media spec SM-1, SM-2; plan PD-6, Task A10). Tools that never
# enter the decoder image: ImageMagick, exiftool, ffmpeg, libheif's encoder with x265, the WebP tools, zip, Python.
# gpac is not packaged in Debian trixie: the HEIF files with a video track or an AV1 primary item are assembled as
# ISO BMFF boxes by generate.py instead.
FROM debian:trixie-slim@sha256:f6b08b09a05725f9431502d3b5714f5c4576e82c1a84fa1debe0c24a7e0c3ec4

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      imagemagick=8:7.1.1.43+dfsg1-1+deb13u13 \
      libimage-exiftool-perl=13.25+dfsg-1 \
      ffmpeg=7:7.1.5-0+deb13u1 \
      libheif-examples=1.23.6-2~deb13u1 \
      libheif-plugin-x265=1.23.6-2~deb13u1 \
      webp=1.5.0-0.1 \
      zip=3.0-15+deb13u1 \
      python3 \
 && rm -rf /var/lib/apt/lists/*

COPY generate.sh generate.py /opt/fixtures/
ENTRYPOINT ["bash", "/opt/fixtures/generate.sh"]
