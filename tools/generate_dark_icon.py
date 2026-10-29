#!/usr/bin/env python3
"""Generates dark AMOLED launcher icons for Ame App Icon Switcher.
"""
import os
import shutil
from PIL import Image, ImageDraw

RES = "app/src/main/res"
SOURCE_3D = "icons/iconNoBg.png"
SOURCE_TILE = "icons/iconC2-p1.png"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

FOREGROUND_DP = 108
SAFE_DP = 72
LEGACY_DP = 48

def generate():
    cutout = Image.open(SOURCE_3D).convert("RGBA")
    bbox = cutout.getbbox()
    left, top, right, bottom = bbox
    mascot_w = right - left
    mascot_h = bottom - top
    mascot_size = max(mascot_w, mascot_h)
    mascot_crop = cutout.crop(bbox)

    # 1. Generate preview images for In-App Settings UI
    os.makedirs(f"{RES}/drawable", exist_ok=True)
    # Light preview (512x512)
    preview_light = Image.new("RGBA", (512, 512), (254, 254, 254, 255))
    scale_light = 360.0 / mascot_size
    pw_light = round(mascot_w * scale_light)
    ph_light = round(mascot_h * scale_light)
    m_light = mascot_crop.resize((pw_light, ph_light), Image.LANCZOS)
    preview_light.paste(m_light, ((512 - pw_light) // 2, (512 - ph_light) // 2), m_light)
    preview_light.save(f"{RES}/drawable/preview_icon_light.png")

    # Dark preview (512x512)
    preview_dark = Image.new("RGBA", (512, 512), (12, 13, 16, 255))
    scale_dark = 360.0 / mascot_size
    pw_dark = round(mascot_w * scale_dark)
    ph_dark = round(mascot_h * scale_dark)
    m_dark = mascot_crop.resize((pw_dark, ph_dark), Image.LANCZOS)
    preview_dark.paste(m_dark, ((512 - pw_dark) // 2, (512 - ph_dark) // 2), m_dark)
    preview_dark.save(f"{RES}/drawable/preview_icon_dark.png")

    # 2. Generate dark launcher mipmaps across densities
    for density, scale in DENSITIES.items():
        fg_px = round(FOREGROUND_DP * scale)
        safe_px = round(SAFE_DP * scale)
        legacy_px = round(LEGACY_DP * scale)

        # Background (Adaptive: pure pitch-black #0C0D10)
        bg_canvas = Image.new("RGB", (fg_px, fg_px), (12, 13, 16))

        # Legacy squircle
        legacy_canvas = Image.new("RGB", (legacy_px, legacy_px), (12, 13, 16))
        scale_legacy = (legacy_px * 0.72) / mascot_size
        lw = round(mascot_w * scale_legacy)
        lh = round(mascot_h * scale_legacy)
        scaled_legacy_mascot = mascot_crop.resize((lw, lh), Image.LANCZOS)
        legacy_canvas.paste(
            scaled_legacy_mascot,
            ((legacy_px - lw) // 2, (legacy_px - lh) // 2),
            scaled_legacy_mascot,
        )

        # Legacy round
        round_canvas = legacy_canvas.copy().convert("RGBA")
        circle_mask = Image.new("L", (legacy_px, legacy_px), 0)
        c_draw = ImageDraw.Draw(circle_mask)
        c_draw.ellipse([0, 0, legacy_px - 1, legacy_px - 1], fill=255)
        round_canvas.putalpha(circle_mask)

        out_dir = f"{RES}/mipmap-{density}"
        os.makedirs(out_dir, exist_ok=True)
        bg_canvas.save(f"{out_dir}/ic_launcher_dark_background.png")
        legacy_canvas.save(f"{out_dir}/ic_launcher_dark.png")
        round_canvas.save(f"{out_dir}/ic_launcher_dark_round.png")

    # 3. Create anydpi-v26 XMLs
    anydpi_dir = f"{RES}/mipmap-anydpi-v26"
    os.makedirs(anydpi_dir, exist_ok=True)
    xml_content = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_dark_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    with open(f"{anydpi_dir}/ic_launcher_dark.xml", "w") as f:
        f.write(xml_content)
    with open(f"{anydpi_dir}/ic_launcher_dark_round.xml", "w") as f:
        f.write(xml_content)

    print("Dark icon mipmaps and previews generated successfully!")

if __name__ == "__main__":
    generate()
