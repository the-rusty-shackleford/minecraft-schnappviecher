"""Build the model's code-native pixel atlas. Run with uv run --no-project python.

Requires: Python 3.14. Effects: writes one deterministic RGBA PNG. Throws: I/O errors.
Copyright (C) 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""
from pathlib import Path
import random
import struct
import zlib


def main() -> None:
    """Requires: repo layout. Effects: regenerates the UV atlas. Throws: OSError."""
    rng = random.Random(1978)
    pixels = bytearray()
    for y in range(128):
        pixels.append(0)
        for x in range(128):
            if x >= 112 and 48 <= y < 56:
                base = (218, 191, 113)
            elif x >= 112 and 56 <= y < 64:
                base = (23, 17, 13)
            elif x >= 96 and 32 <= y < 48:
                base = (193, 170, 121)
            elif x >= 96 and 16 <= y < 32:
                base = (101, 37, 32)
            elif x >= 80 and 64 <= y:
                base = (49, 34, 26)
            elif y >= 64:
                # Long uneven highlights make shaggy fur, distinct from woven cloth.
                stripe = ((x * 13 + y // 5 * 3) % 11) * 2 - 10
                base = (87 + stripe, 64 + stripe, 46 + stripe)
            else:
                weave = (5 if x % 2 == 0 else -4) + (3 if y % 3 == 0 else 0)
                seam = -20 if x % 27 in (0, 1) else 0
                base = (143 + weave + seam, 119 + weave + seam, 82 + weave + seam)
            noise = rng.randrange(-7, 8)
            pixels.extend(max(0, min(255, c + noise)) for c in base)
            pixels.append(255)

    def chunk(name: bytes, content: bytes) -> bytes:
        """Requires: PNG chunk fields. Effects: encodes length and CRC. Throws: none."""
        return struct.pack(">I", len(content)) + name + content + struct.pack(">I", zlib.crc32(name + content))

    out = Path(__file__).resolve().parents[2] / "src/main/resources/assets/schnappviecher/textures/entity/schnappviech.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 128, 128, 8, 6, 0, 0, 0))
                   + chunk(b"IDAT", zlib.compress(pixels, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    main()
