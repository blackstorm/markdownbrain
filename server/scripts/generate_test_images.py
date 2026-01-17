#!/usr/bin/env python3
"""
Generate simple PNG images for tests without external dependencies.

Example:
  python3 server/scripts/generate_test_images.py \
    --out server/test/fixtures/images/png_100x100.png \
    --width 100 --height 100 --color 1e88e5
"""

from __future__ import annotations

import argparse
import os
import struct
import zlib


def _chunk(chunk_type: bytes, data: bytes) -> bytes:
    length = struct.pack(">I", len(data))
    crc = zlib.crc32(chunk_type)
    crc = zlib.crc32(data, crc) & 0xFFFFFFFF
    return length + chunk_type + data + struct.pack(">I", crc)


def _parse_color(value: str) -> tuple[int, int, int]:
    cleaned = value.strip().lstrip("#")
    if len(cleaned) != 6:
        raise ValueError("color must be 6 hex digits, like 1e88e5")
    r = int(cleaned[0:2], 16)
    g = int(cleaned[2:4], 16)
    b = int(cleaned[4:6], 16)
    return r, g, b


def generate_png(width: int, height: int, color: str) -> bytes:
    r, g, b = _parse_color(color)
    signature = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)

    row = bytes([0]) + bytes([r, g, b]) * width
    raw = row * height
    compressed = zlib.compress(raw)

    return b"".join(
        [
            signature,
            _chunk(b"IHDR", ihdr),
            _chunk(b"IDAT", compressed),
            _chunk(b"IEND", b""),
        ]
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate test images.")
    parser.add_argument("--out", required=True, help="Output file path")
    parser.add_argument("--width", type=int, required=True, help="Image width")
    parser.add_argument("--height", type=int, required=True, help="Image height")
    parser.add_argument(
        "--format",
        default="png",
        choices=["png"],
        help="Image format (png only)",
    )
    parser.add_argument(
        "--color",
        default="000000",
        help="Solid RGB color as 6 hex digits",
    )
    args = parser.parse_args()

    if args.width <= 0 or args.height <= 0:
        raise ValueError("width and height must be positive integers")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)

    if args.format != "png":
        raise ValueError("only png format is supported")

    data = generate_png(args.width, args.height, args.color)
    with open(args.out, "wb") as f:
        f.write(data)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
