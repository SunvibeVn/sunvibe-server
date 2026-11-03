"""One processing run, as a child of the supervisor under OS limits (survey-media spec 4.5, A-3, A-4).

    pipeline.py detect    WORKDIR SOURCE   format from the bytes: JPEG, PNG, WEBP, HEIF or UNSUPPORTED_FORMAT
    pipeline.py normalize WORKDIR SOURCE   structure, header and pixel bounds, then the three JPEG variants

Writes WORKDIR/result.json: {"status": "OK" | "REJECTED" | "FAILED", "code": ..., ...}. The decoders run as child
processes of this one ("vips"/"vipsheader", argument lists, never a shell) and inherit its limits; a decoder killed
by a signal ends this run with a non-zero exit, which the supervisor reports as a limit hit.
"""
import json
import os
import struct
import subprocess
import sys

MAX_PIXELS = 120_000_000  # spec A-3
MAX_SIDE = 16_384  # spec A-3
VARIANTS = (  # spec A-4: name, bounding box, JPEG quality
    ("FULL", 4096, 88),
    ("PREVIEW", 1600, 82),
    ("THUMBNAIL", 400, 75),
)
HEIF_BRANDS = {b"heic", b"heix", b"heim", b"heis", b"hevc", b"hevx", b"mif1", b"msf1"}
IMAGE_ITEM_TYPES = {b"hvc1", b"grid", b"iden", b"iovl", b"av01", b"jpeg", b"unci", b"j2k1", b"vvc1", b"avc1",
                    b"tmap"}
TOOL_TIMEOUT_SECONDS = 110


class LimitHit(Exception):
    """A decoder was killed or crashed: the run ends as a limit hit."""


class Malformed(Exception):
    """The container structure is broken."""


def u16(data, i):
    return struct.unpack_from(">H", data, i)[0]


def u32(data, i):
    return struct.unpack_from(">I", data, i)[0]


def u64(data, i):
    return struct.unpack_from(">Q", data, i)[0]


# ==================== format detection (spec 4.5 row 2) ====================

def detect(data):
    if data[:3] == b"\xff\xd8\xff":
        return "JPEG"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "PNG"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "WEBP"
    if len(data) >= 12 and data[4:8] == b"ftyp":
        try:
            heif = analyse_heif(data)
        except (Malformed, struct.error):
            return None
        if heif["is_heif"] and heif["primary_hevc"]:
            return "HEIF"
    return None


# ==================== ISO BMFF (HEIF) ====================

def boxes(data, start, end):
    i = start
    while i + 8 <= end:
        size = u32(data, i)
        kind = data[i + 4:i + 8]
        header = 8
        if size == 1:
            size = u64(data, i + 8)
            header = 16
        elif size == 0:
            size = end - i
        if size < header or i + size > end:
            raise Malformed()
        yield kind, i + header, i + size
        i += size
    if i != end:
        raise Malformed()


def analyse_heif(data):
    """Brands, the primary item's type, the top-level images and the tracks of a HEIF file."""
    top = list(boxes(data, 0, len(data)))
    info = {"is_heif": False, "primary_hevc": False, "top_level": 0, "stereo": False, "tracks": []}
    for kind, start, end in top:
        if kind == b"ftyp":
            brands = {data[start:start + 4]}
            brands.update(data[j:j + 4] for j in range(start + 8, end - 3, 4))
            info["is_heif"] = bool(brands & HEIF_BRANDS) and data[start:start + 4] not in (b"avif", b"avis")
        elif kind == b"meta":
            analyse_meta(data, start + 4, end, info)
        elif kind == b"moov":
            for trak, ts, te in boxes(data, start, end):
                if trak == b"trak":
                    info["tracks"].append(track_handler(data, ts, te))
    return info


def analyse_meta(data, start, end, info):
    items = {}
    primary = None
    refs = []
    groups = []
    for kind, s, e in boxes(data, start, end):
        if kind == b"pitm":
            primary = u16(data, s + 4) if data[s] == 0 else u32(data, s + 4)
        elif kind == b"iinf":
            version = data[s]
            first = s + 4 + (2 if version == 0 else 4)
            for infe, fs, fe in boxes(data, first, e):
                if infe == b"infe" and data[fs] >= 2:
                    if data[fs] == 2:
                        item_id, item_type = u16(data, fs + 4), data[fs + 8:fs + 12]
                    else:
                        item_id, item_type = u32(data, fs + 4), data[fs + 10:fs + 14]
                    items[item_id] = item_type
        elif kind == b"iref":
            wide = data[s] != 0
            for ref, rs, re in boxes(data, s + 4, e):
                from_id = u32(data, rs) if wide else u16(data, rs)
                j = rs + (4 if wide else 2)
                count = u16(data, j)
                j += 2
                targets = []
                for _ in range(count):
                    targets.append(u32(data, j) if wide else u16(data, j))
                    j += 4 if wide else 2
                refs.append((ref, from_id, targets))
        elif kind == b"grpl":
            for group, gs, ge in boxes(data, s, e):
                ids_count = u32(data, gs + 8)
                groups.append((group, [u32(data, gs + 12 + 4 * k) for k in range(ids_count)]))
    hidden = set()
    for ref, from_id, targets in refs:
        if ref in (b"thmb", b"auxl"):
            hidden.add(from_id)
        elif ref == b"dimg":
            hidden.update(targets)
    alternatives = set()
    for group, ids in groups:
        if group == b"altr" and primary in ids:
            alternatives.update(i for i in ids if i != primary)
        if group == b"ster":
            info["stereo"] = True
    top_level = [i for i, t in items.items() if t in IMAGE_ITEM_TYPES and i not in hidden and i not in alternatives
                 and t != b"tmap"]
    info["top_level"] = len(top_level)
    primary_type = items.get(primary)
    if primary_type == b"hvc1":
        info["primary_hevc"] = True
    elif primary_type == b"grid":
        tiles = [t for ref, from_id, targets in refs if ref == b"dimg" and from_id == primary for t in targets]
        info["primary_hevc"] = bool(tiles) and all(items.get(t) == b"hvc1" for t in tiles)


def track_handler(data, start, end):
    for kind, s, e in boxes(data, start, end):
        if kind == b"mdia":
            for sub, ss, se in boxes(data, s, e):
                if sub == b"hdlr":
                    return data[ss + 8:ss + 12]
    return b"????"


# ==================== structure (spec 4.5 row 4) ====================

def structure_reason(data, fmt):
    """ANIMATED, MOTION_PHOTO or COMPOSITE_FILE, or None for a still image with its own auxiliary items."""
    if fmt == "PNG":
        i = 8
        while i + 12 <= len(data):
            length, kind = u32(data, i), data[i + 4:i + 8]
            if kind == b"acTL":
                return "ANIMATED"
            i += 12 + length
            if kind == b"IEND":
                return trailing_reason(data[i:])
        return None
    if fmt == "WEBP":
        riff_end = 8 + struct.unpack_from("<I", data, 4)[0]
        i = 12
        while i + 8 <= min(riff_end, len(data)):
            kind, size = data[i:i + 4], struct.unpack_from("<I", data, i + 4)[0]
            if kind in (b"ANIM", b"ANMF"):
                return "ANIMATED"
            if kind == b"VP8X" and data[i + 8] & 0x02:
                return "ANIMATED"
            i += 8 + size + (size & 1)
        return trailing_reason(data[riff_end:])
    if fmt == "HEIF":
        info = analyse_heif(data)
        if any(handler != b"pict" for handler in info["tracks"]):
            return "MOTION_PHOTO"
        if info["tracks"]:
            return "ANIMATED"
        if info["top_level"] > 1 and not (info["stereo"] and info["top_level"] == 2):
            return "ANIMATED"
        return None
    end = jpeg_end(data, 0)
    if end is None:
        return None  # broken: the decoder decides (UNREADABLE)
    return trailing_reason(data[end:])


def jpeg_end(data, start):
    """The offset after the EOI of the JPEG starting at start, or None."""
    if data[start:start + 2] != b"\xff\xd8":
        return None
    i = start + 2
    n = len(data)
    while i + 4 <= n:
        if data[i] != 0xFF:
            return None
        marker = data[i + 1]
        if marker == 0xFF:
            i += 1
            continue
        if marker == 0xD9:
            return i + 2
        if 0xD0 <= marker <= 0xD7 or marker == 0x01:
            i += 2
            continue
        length = u16(data, i + 2)
        i += 2 + length
        if marker == 0xDA:
            while True:
                j = data.find(b"\xff", i)
                if j < 0 or j + 1 >= n:
                    return None
                following = data[j + 1]
                if following == 0x00 or 0xD0 <= following <= 0xD7 or following == 0xFF:
                    i = j + 1 if following == 0xFF else j + 2
                    continue
                i = j
                break
    return None


def trailing_reason(tail):
    """Data after the image end: embedded JPEGs (gain maps, MPF images), padding and vendor trailers are allowed."""
    while tail:
        stripped = tail.lstrip(b"\x00\r\n\t ")
        if not stripped:
            return None
        if b"ftyp" in stripped[:4096] or b"MotionPhoto_Data" in stripped or b"moov" in stripped[:4096]:
            return "MOTION_PHOTO"
        if b"PK\x03\x04" in stripped:
            return "COMPOSITE_FILE"
        if stripped[:2] == b"\xff\xd8":
            end = jpeg_end(stripped, 0)
            if end is None:
                return "COMPOSITE_FILE"
            tail = stripped[end:]
            continue
        if b"SEFH" in stripped[-64:]:
            return None  # a Samsung trailer of metadata without video
        return "COMPOSITE_FILE"
    return None


# ==================== decoding (spec 4.5 rows 5-7, A-4) ====================

def tool(arguments):
    """Runs a libvips tool; True when it succeeded, False on an error answer; LimitHit when it was killed."""
    try:
        done = subprocess.run(arguments, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                              stderr=subprocess.DEVNULL, timeout=TOOL_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        raise LimitHit()
    if done.returncode < 0:
        raise LimitHit()
    return done.returncode == 0


def header_field(path, field):
    try:
        done = subprocess.run(["vipsheader", "-f", field, path], stdin=subprocess.DEVNULL, capture_output=True,
                              timeout=TOOL_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        raise LimitHit()
    if done.returncode < 0:
        raise LimitHit()
    if done.returncode != 0:
        return None
    try:
        return int(done.stdout.strip()[:20])
    except ValueError:
        return None


def normalize(workdir, source, data):
    fmt = detect(data)
    if fmt is None:
        return {"status": "REJECTED", "code": "UNSUPPORTED_FORMAT"}
    try:
        reason = structure_reason(data, fmt)
    except (Malformed, struct.error, IndexError):
        return {"status": "REJECTED", "code": "UNREADABLE"}
    if reason:
        return {"status": "REJECTED", "code": reason}
    width, height = header_field(source, "width"), header_field(source, "height")
    if not width or not height:
        return {"status": "REJECTED", "code": "UNREADABLE"}
    if width > MAX_SIDE or height > MAX_SIDE or width * height > MAX_PIXELS:
        return {"status": "REJECTED", "code": "TOO_MANY_PIXELS"}
    variants = []
    for name, box, quality in VARIANTS:
        out = os.path.join(workdir, name.lower() + ".jpg")
        # --fail-on truncated: truncated or corrupt data fails the decode (never a blank or gray output)
        ok = tool(["vips", "thumbnail", source,
                   out + "[Q=%d,keep=none,background=255,optimize_coding=true]" % quality,
                   str(box), "--height", str(box), "--size", "down", "--export-profile", "srgb",
                   "--fail-on", "truncated"])
        if not ok:
            return {"status": "REJECTED", "code": "UNREADABLE"}
        out_width, out_height = header_field(out, "width"), header_field(out, "height")
        if not out_width or not out_height:
            return {"status": "FAILED", "code": "NO_OUTPUT"}
        variants.append({"kind": name, "file": os.path.basename(out), "width": out_width, "height": out_height})
    return {"status": "OK", "code": fmt, "width": width, "height": height, "variants": variants}


def main():
    op, workdir, source = sys.argv[1], sys.argv[2], sys.argv[3]
    if op == "test-sleep":
        import time
        time.sleep(3600)
    if op == "test-memory":
        hog = bytearray(4 * 1024 * 1024 * 1024)
        hog[-1] = 1
    with open(source, "rb") as handle:
        data = handle.read()
    if op == "detect":
        fmt = detect(data)
        result = {"status": "OK", "code": fmt} if fmt else {"status": "REJECTED", "code": "UNSUPPORTED_FORMAT"}
    else:
        try:
            result = normalize(workdir, source, data)
        except LimitHit:
            sys.exit(3)
    with open(os.path.join(workdir, "result.json"), "w") as handle:
        json.dump(result, handle)


if __name__ == "__main__":
    main()
