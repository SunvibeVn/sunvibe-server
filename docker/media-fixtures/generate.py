"""Synthetic fixtures assembled from boxes and chunks (survey-media SM-2; plan PD-6).

    generate.py OUT STILL_HEIC

heif-video.heic   the HEIC still with a top-level moov box holding a video track (hdlr 'vide'): MOTION_PHOTO
av1-primary.heif  a HEIF (brand mif1) whose primary item is av01: UNSUPPORTED_FORMAT
image.avif        an AVIF file header (brand avif): UNSUPPORTED_FORMAT
huge-pixels.png   a PNG declaring 70,000 x 70,000 pixels in a few bytes: TOO_MANY_PIXELS before any decode
"""
import struct
import sys
import zlib


def box(kind, payload):
    return struct.pack(">I", 8 + len(payload)) + kind + payload


def full_box(kind, version, payload):
    return box(kind, bytes([version, 0, 0, 0]) + payload)


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def main():
    out, still = sys.argv[1], sys.argv[2]
    with open(still, "rb") as handle:
        heic = handle.read()

    hdlr = full_box(b"hdlr", 0, b"\0\0\0\0" + b"vide" + b"\0" * 12 + b"VideoHandler\0")
    moov = box(b"moov", box(b"trak", box(b"mdia", hdlr)))
    with open(out + "/heif-video.heic", "wb") as handle:
        handle.write(heic + moov)

    ftyp = box(b"ftyp", b"mif1" + b"\0\0\0\0" + b"mif1" + b"miaf")
    pitm = full_box(b"pitm", 0, struct.pack(">H", 1))
    infe = full_box(b"infe", 2, struct.pack(">HH", 1, 0) + b"av01" + b"\0")
    iinf = full_box(b"iinf", 0, struct.pack(">H", 1) + infe)
    meta = full_box(b"meta", 0, full_box(b"hdlr", 0, b"\0\0\0\0pict" + b"\0" * 12 + b"\0") + pitm + iinf)
    with open(out + "/av1-primary.heif", "wb") as handle:
        handle.write(ftyp + meta + box(b"mdat", b"\0" * 64))

    avif = box(b"ftyp", b"avif" + b"\0\0\0\0" + b"avif" + b"mif1" + b"miaf")
    with open(out + "/image.avif", "wb") as handle:
        handle.write(avif + meta + box(b"mdat", b"\0" * 64))

    ihdr = struct.pack(">IIBBBBB", 70000, 70000, 8, 0, 0, 0, 0)
    idat = zlib.compress(b"\0" * 1024)
    with open(out + "/huge-pixels.png", "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", idat)
                     + png_chunk(b"IEND", b""))


if __name__ == "__main__":
    main()
