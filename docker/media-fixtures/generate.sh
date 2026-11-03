#!/usr/bin/env bash
# Generates the synthetic survey-media corpus into /out (survey-media spec SM-1, SM-2; plan PD-6). Deterministic
# content (fixed seeds, fixed dates); only file timestamps differ between runs.
set -euo pipefail
out=/out
work=$(mktemp -d)
cd "$work"

# A still photo-like JPEG with GPS EXIF, XMP, IPTC, a comment, an embedded thumbnail and an ICC profile
magick -size 1200x900 -seed 7 plasma:fractal -quality 90 base.jpg
magick -size 160x120 xc:gray thumb.jpg
exiftool -q -overwrite_original \
  -GPSLatitude=16.0544 -GPSLatitudeRef=N -GPSLongitude=108.2022 -GPSLongitudeRef=E \
  -DateTimeOriginal="2026:10:20 09:00:00" -Make="SunvibeTestCam" -Model="Fixture 1" \
  -XMP-dc:Description="secret xmp caption" -IPTC:Caption-Abstract="secret iptc caption" \
  -Comment="secret jpeg comment" "-ThumbnailImage<=thumb.jpg" base.jpg
magick base.jpg -profile /usr/share/color/icc/ghostscript/srgb.icc still.jpg 2>/dev/null || cp base.jpg still.jpg
exiftool -q -overwrite_original -tagsFromFile base.jpg -all:all still.jpg
cp still.jpg "$out/still.jpg"

# A large JPEG rotated by its EXIF orientation (6: 90 degrees clockwise)
magick -size 6000x4000 -seed 9 plasma:fractal -quality 80 rotated.jpg
exiftool -q -overwrite_original -Orientation#=6 rotated.jpg
cp rotated.jpg "$out/rotated.jpg"

# A small PNG with alpha and a text chunk (never enlarged; alpha flattened onto white)
magick -size 800x600 xc:none -fill "rgba(200,30,30,0.6)" -draw "circle 400,300 400,100" \
  -set comment "secret png text" small-alpha.png
cp small-alpha.png "$out/small-alpha.png"

# A WebP still with EXIF and XMP
cwebp -quiet -q 80 -metadata all still.jpg -o still.webp
cp still.webp "$out/still.webp"

# A HEIC still (HEVC primary item) with EXIF and a thumbnail
heif-enc -q 60 --thumb 128 -o still.heic still.jpg >/dev/null
cp still.heic "$out/still.heic"

# Animations: an animated WebP and an APNG
magick -size 320x240 xc:red frame1.png
magick -size 320x240 xc:blue frame2.png
img2webp -loop 0 -d 200 frame1.png -d 200 frame2.png -o animated.webp >/dev/null
cp animated.webp "$out/animated.webp"
ffmpeg -loglevel error -y -framerate 2 -i frame%d.png -pix_fmt rgb24 -plays 0 -f apng animated.png
cp animated.png "$out/animated.png"

# A motion photo: an MP4 appended after the JPEG end; a composite: a ZIP appended
ffmpeg -loglevel error -y -f lavfi -i color=c=green:s=320x240:d=1 -c:v libx264 -pix_fmt yuv420p clip.mp4
cat still.jpg clip.mp4 > "$out/motion.jpg"
echo "payload" > payload.txt
zip -q -X payload.zip payload.txt
cat still.jpg payload.zip > "$out/composite.jpg"

# A truncated JPEG: its first 60 percent
size=$(stat -c %s still.jpg)
head -c $((size * 60 / 100)) still.jpg > "$out/truncated.jpg"

# Unsupported formats: SVG, PDF, GIF, a renamed text file
printf '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10"/></svg>' \
  > "$out/image.svg"
magick -size 100x100 xc:white "$out/document.pdf"
magick -size 100x100 xc:white "$out/still.gif"
printf 'not an image, only text\n' > "$out/notes.jpg"

# The ISO BMFF fixtures and the huge-pixel PNG
python3 /opt/fixtures/generate.py "$out" still.heic

ls -l "$out"
