# Media processor channel protocol (v1)

Survey-media spec 4.5, A-27, A-28; ADR-011 decision 4 form (a); plan PD-1, Task A9. The API and the processing
container exchange bytes only, one request per connection, over a private channel: one port of an internal network
that holds nothing but the API and this container (production, `compose.yaml`), or a port published on `127.0.0.1`
only (local development, `compose.dev.yaml`). The container holds no secret and has no egress.

All integers are unsigned, big-endian.

## Request
| Field | Size | Value |
|---|---|---|
| magic | 4 | `SVM1` |
| op | 1 | `0x01` DETECT, `0x02` NORMALIZE |
| length | 4 | source length in bytes; at most 20,971,520 (spec A-2) |
| source | length | the raw source |

The supervisor refuses a frame whose length is above 20 MiB (status REFUSED, code `FRAME_TOO_LARGE`) before it reads
any byte of the body, a wrong magic (`BAD_MAGIC`) and an unknown op (`BAD_OP`). The test-only ops `0x71` (a child that
sleeps past the wall clock) and `0x72` (a child that exceeds its memory) exist only when the supervisor is started
with `--enable-test-requests`, which no compose file passes.

## Reply
| Field | Size | Value |
|---|---|---|
| magic | 4 | `SVR1` |
| status | 1 | `0` OK, `1` REJECTED, `2` LIMIT_HIT, `3` FAILED, `4` REFUSED |
| code length | 1 | 0..255 |
| code | code length | ASCII: the format on an OK DETECT (`JPEG`, `PNG`, `WEBP`, `HEIF`), the reason on REJECTED (`UNSUPPORTED_FORMAT`, `UNREADABLE`, `ANIMATED`, `MOTION_PHOTO`, `COMPOSITE_FILE`, `TOO_MANY_PIXELS`), a cause otherwise |

An OK NORMALIZE continues with:

| Field | Size | Value |
|---|---|---|
| source width | 4 | composed width of the primary image, px |
| source height | 4 | composed height, px |
| variant count | 1 | 3 |
| per variant: kind | 1 | `1` FULL, `2` PREVIEW, `3` THUMBNAIL |
| per variant: width | 4 | px |
| per variant: height | 4 | px |
| per variant: length | 4 | bytes |
| per variant: data | length | the JPEG |

The reader in the API enforces the output caps of spec A-28 while reading (FULL 12 MiB, PREVIEW 2 MiB, THUMBNAIL
256 KiB, the whole reply 15 MiB) and stops at a cap; it then checks every JPEG's markers and SOF dimensions itself
(spec 4.5 last row) and takes `processor_version` from its own configuration, never from this reply.

## Runs
Each request is one run: the source is written to a server-random file in a 0700 directory of the tmpfs work area,
one child (`pipeline.py`) runs under `prlimit` (60 s CPU, 1.5 GiB address space, 64 MiB file size, no core, 64 open
files) with one decoder thread (`VIPS_CONCURRENCY=1`) and the untrusted loaders blocked (`VIPS_BLOCK_UNTRUSTED=1`), the
supervisor kills its process group after 120 s of wall clock, drains its output with a cap without logging it, and
deletes the run's files in a finally block; the work area is also emptied at start-up. A child killed or crashed
(CPU, memory, wall clock, a signal) answers LIMIT_HIT; the API counts two of them as REJECTED `UNREADABLE` (spec A-7).
At most 3 runs at once (two worker runs and the probe, spec A-27); the container's memory limit covers them.

## NORMALIZE steps (spec 4.5)
1. Format from the bytes: JPEG, PNG, WebP, HEIC/HEIF whose primary item is `hvc1` or a grid of `hvc1` tiles; else
   UNSUPPORTED_FORMAT.
2. Structure: an animation (PNG `acTL`, WebP `ANIM`/`ANMF` or the VP8X animation flag, a HEIF image sequence or more
   than one top-level image that is not a stereo pair) is ANIMATED; a video track or video data appended after the
   still is MOTION_PHOTO; other data after the image end is COMPOSITE_FILE. Thumbnails, auxiliary images (depth, gain
   maps, MPF images after a JPEG), padding and a Samsung metadata trailer belong to the still.
3. Header and bounds before decoding pixels: at most 120,000,000 pixels and 16,384 px per side (TOO_MANY_PIXELS).
4. `vips thumbnail` per variant with `--fail-on truncated`, auto-orientation, sRGB, down-sizing only to 4,096, 1,600 and
   400 px boxes, alpha flattened onto white, JPEG quality 88, 82 and 75 with `keep=none`; a failure is UNREADABLE.
