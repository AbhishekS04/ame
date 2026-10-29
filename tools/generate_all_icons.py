#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw

RES = "app/src/main/res"
ICONS_SRC = "/run/media/abhishek/BBC/best-projects/ame/icons"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
FOREGROUND_DP = 108
SAFE_DP = 76
LEGACY_DP = 48

NEW_ICONS = {
    "orbit": {
        "file": "Distressed Ame Orbit Logo.png",
        "title": "Orbit",
        "bg_color": (15, 15, 17),
    },
    "bluedrip": {
        "file": "Distressed Blue Drip Lettering Icon.png",
        "title": "Blue Drip",
        "bg_color": (14, 78, 247),
    },
    "galactic": {
        "file": "Distressed Galactic Ame Logo.png",
        "title": "Galactic",
        "bg_color": (12, 13, 16),
    },
    "gothic": {
        "file": "Distressed Gothic “Ame” Emblem.png",
        "title": "Gothic",
        "bg_color": (14, 14, 16),
    },
    "vinyl": {
        "file": "ChatGPT Image Oct 28, 2026, 08_43_42 PM.png",
        "title": "Vinyl Orbit",
        "bg_color": (14, 14, 16),
    },
    "stars": {
        "file": "ChatGPT Image Oct 28, 2026, 08_43_10 PM.png",
        "title": "Ame Stars",
        "bg_color": (10, 10, 12),
    },
}

os.makedirs(f"{RES}/drawable", exist_ok=True)

for key, cfg in NEW_ICONS.items():
    src_path = os.path.join(ICONS_SRC, cfg["file"])
    im = Image.open(src_path).convert("RGBA")
    
    # 1. Preview for in-app Settings (cropped squircle badge, 512x512)
    # The squircle is roughly in box (92, 92, 1162, 1162)
    crop_box = (90, 90, 1164, 1164)
    badge = im.crop(crop_box).resize((512, 512), Image.LANCZOS)
    badge.save(f"{RES}/drawable/preview_icon_{key}.png")

    # 2. Foreground for adaptive icon (scaled to fit safe zone on 108dp canvas)
    # 3. Mipmaps across densities
    for density, scale in DENSITIES.items():
        fg_px = round(FOREGROUND_DP * scale)
        safe_px = round(SAFE_DP * scale)
        legacy_px = round(LEGACY_DP * scale)
        out_dir = f"{RES}/mipmap-{density}"
        os.makedirs(out_dir, exist_ok=True)

        # Adaptive Background
        bg_canvas = Image.new("RGB", (fg_px, fg_px), cfg["bg_color"])
        bg_canvas.save(f"{out_dir}/ic_launcher_{key}_background.png")

        # Adaptive Foreground (badge scaled to safe_px and centered in fg_px)
        fg_canvas = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
        scaled_badge = badge.resize((safe_px, safe_px), Image.LANCZOS)
        offset = (fg_px - safe_px) // 2
        fg_canvas.paste(scaled_badge, (offset, offset), scaled_badge)
        fg_canvas.save(f"{out_dir}/ic_launcher_{key}_foreground.png")

        # Legacy squircle
        legacy_canvas = badge.resize((legacy_px, legacy_px), Image.LANCZOS)
        legacy_canvas.save(f"{out_dir}/ic_launcher_{key}.png")

        # Legacy round
        round_canvas = legacy_canvas.copy().convert("RGBA")
        circle_mask = Image.new("L", (legacy_px, legacy_px), 0)
        c_draw = ImageDraw.Draw(circle_mask)
        c_draw.ellipse([0, 0, legacy_px - 1, legacy_px - 1], fill=255)
        round_canvas.putalpha(circle_mask)
        round_canvas.save(f"{out_dir}/ic_launcher_{key}_round.png")

    # 4. XMLs in anydpi-v26
    anydpi_dir = f"{RES}/mipmap-anydpi-v26"
    os.makedirs(anydpi_dir, exist_ok=True)
    xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_{key}_background" />
    <foreground android:drawable="@mipmap/ic_launcher_{key}_foreground" />
</adaptive-icon>
"""
    with open(f"{anydpi_dir}/ic_launcher_{key}.xml", "w") as f:
        f.write(xml_content)
    with open(f"{anydpi_dir}/ic_launcher_{key}_round.xml", "w") as f:
        f.write(xml_content)

print("Generated all 6 new badass icon sets successfully!")
