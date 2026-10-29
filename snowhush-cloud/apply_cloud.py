#!/usr/bin/env python3
"""Replace all Snowhush PiiWii Windows icons with the exact user-supplied cloud artwork.
Artwork source: user image 'Nuage glacé et flocons scintillants(1).png',
proportionally resized for 256 px with transparency preserved, as WebP quality 90.
This is a build resource conversion, not new artwork.
"""
from pathlib import Path
from io import BytesIO
from PIL import Image
import base64, hashlib, sys
source = Path(sys.argv[1])
repo = Path(sys.argv[2])
data = base64.b64decode(source.read_text(encoding="ascii").strip(), validate=True)
if len(data) < 1000:
    raise RuntimeError("Nuage source is unexpectedly small")
image = Image.open(BytesIO(data)).convert("RGBA")
if image.size != (256, 256) or image.getpixel((0,0))[3] != 0:
    raise RuntimeError(f"Unexpected image / transparency: {image.size}")
sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)]
assets=repo/"windows"/"assets"
assets.mkdir(parents=True, exist_ok=True)
for filename in ("Snowhush.ico","switch-on.ico","switch-off.ico"):
    image.save(assets/filename, format="ICO", sizes=sizes)
    if (assets/filename).stat().st_size < 5000:
        raise RuntimeError(f"ICO file too small: {filename}")
image.save(assets/"icon-256.png",format="PNG",optimize=True)
cargo=repo/"windows"/"Cargo.toml"
content=cargo.read_text(encoding="utf8")
if content.count('version = "0.5.9"') != 1:
    raise RuntimeError("Unexpected Snowhush version")
cargo.write_text(content.replace('version = "0.5.9"','version = "0.5.10"',1),encoding="utf8")
# From the same cloud artwork, all 3 Windows resource IDs render the same icon;
# the tooltip describes ON/OFF and left-click still toggles the snowfall.
print("Updated cloud image:", image.size, "WebP bytes", len(data), "sha256",hashlib.sha256(data).hexdigest())
for filename in ("Snowhush.ico","switch-on.ico","switch-off.ico","icon-256.png"):
    print(filename,(assets/filename).stat().st_size)
print("Version 0.5.10; event handlers and weather mode unchanged")
