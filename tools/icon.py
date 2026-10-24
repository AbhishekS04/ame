"""Turns the Ame mascot icon source into every icon resource the app ships.

Run it after changing the artwork:

    python3 tools/icon.py
"""

from PIL import Image, ImageDraw, ImageFilter
import os

RES = "app/src/main/res"
SOURCE_3D = "app/icon-src/ame-mascot-3d.png"
SOURCE_TILE = "app/icon-src/ame-mascot-tile.png"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

FOREGROUND_DP = 108
SAFE_DP = 70
LEGACY_DP = 48


def build():
    cutout = Image.open(SOURCE_3D).convert("RGBA")
    full_tile = Image.open(SOURCE_TILE).convert("RGBA")

    bbox = cutout.getbbox()
    left, top, right, bottom = bbox
    mascot_w = right - left
    mascot_h = bottom - top
    mascot_size = max(mascot_w, mascot_h)
    mascot_crop = cutout.crop(bbox)

    for density, scale in DENSITIES.items():
        fg_px = round(FOREGROUND_DP * scale)
        safe_px = round(SAFE_DP * scale)
        legacy_px = round(LEGACY_DP * scale)

        # 1. FOREGROUND (Adaptive icon: 108dp, transparent canvas)
        fg_canvas = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
        scale_factor = safe_px / mascot_size
        scaled_w = round(mascot_w * scale_factor)
        scaled_h = round(mascot_h * scale_factor)
        scaled_mascot = mascot_crop.resize((scaled_w, scaled_h), Image.LANCZOS)

        paste_x = round((fg_px - scaled_w) / 2.0)
        paste_y = round((fg_px - scaled_h) / 2.0) - round(2 * scale)
        fg_canvas.paste(scaled_mascot, (paste_x, paste_y), scaled_mascot)

        # 2. BACKGROUND (Adaptive icon: 108dp, Liquid Glass gradient)
        bg_canvas = Image.new("RGB", (fg_px, fg_px), (10, 10, 14))
        bg_draw = ImageDraw.Draw(bg_canvas)
        for y in range(fg_px):
            t = y / float(fg_px)
            r = round(24 * (1 - t) + 9 * t)
            g = round(19 * (1 - t) + 9 * t)
            b = round(43 * (1 - t) + 12 * t)
            bg_draw.line([(0, y), (fg_px, y)], fill=(r, g, b))

        glow = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
        g_draw = ImageDraw.Draw(glow)
        g_radius = round(fg_px * 0.4)
        g_center = (fg_px // 2, round(fg_px * 0.6))
        g_draw.ellipse(
            [
                g_center[0] - g_radius,
                g_center[1] - round(g_radius * 0.4),
                g_center[0] + g_radius,
                g_center[1] + round(g_radius * 0.4),
            ],
            fill=(139, 92, 246, 60),
        )
        glow = glow.filter(ImageFilter.GaussianBlur(fg_px * 0.12))
        bg_canvas.paste(glow, (0, 0), glow)

        # 3. LEGACY ICONS (48dp squircle & round)
        scaled_tile = full_tile.resize((legacy_px, legacy_px), Image.LANCZOS)
        round_canvas = scaled_tile.copy()
        circle_mask = Image.new("L", (legacy_px, legacy_px), 0)
        c_draw = ImageDraw.Draw(circle_mask)
        c_draw.ellipse([0, 0, legacy_px - 1, legacy_px - 1], fill=255)
        round_canvas.putalpha(circle_mask)

        out_dir = f"{RES}/mipmap-{density}"
        os.makedirs(out_dir, exist_ok=True)
        fg_canvas.save(f"{out_dir}/ic_launcher_foreground.png")
        bg_canvas.save(f"{out_dir}/ic_launcher_background.png")
        scaled_tile.save(f"{out_dir}/ic_launcher.png")
        round_canvas.save(f"{out_dir}/ic_launcher_round.png")

    print("Generated all mipmap densities for Ame mascot.")


if __name__ == "__main__":
    build()
