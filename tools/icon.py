"""Turns the Ame mascot icon sources in icons/ into every icon resource the app ships.

Sources:
- icons/iconNoBg.png: 3D mascot with transparent background
- icons/iconC2-p1.png: Mascot with clean studio background
- icons/finalsvg.svg: Official vector mascot glyph

Run it after changing the artwork:

    python3 tools/icon.py
"""

from PIL import Image, ImageDraw, ImageFilter
import os
import shutil
import subprocess

RES = "app/src/main/res"
SOURCE_3D = "icons/iconNoBg.png"
SOURCE_TILE = "icons/iconC2-p1.png"
SOURCE_SVG = "icons/finalsvg.svg"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

FOREGROUND_DP = 108
SAFE_DP = 72
LEGACY_DP = 48


def rdp(points, epsilon):
    if len(points) < 3:
        return points
    start, end = points[0], points[-1]
    dx, dy = end[0] - start[0], end[1] - start[1]
    mag = (dx * dx + dy * dy) ** 0.5
    if mag == 0:
        dists = [((p[0] - start[0]) ** 2 + (p[1] - start[1]) ** 2) ** 0.5 for p in points]
    else:
        dists = [abs(dy * p[0] - dx * p[1] + end[0] * start[1] - end[1] * start[0]) / mag for p in points]
    max_d = max(dists)
    max_idx = dists.index(max_d)
    if max_d > epsilon:
        left = rdp(points[:max_idx + 1], epsilon)
        right = rdp(points[max_idx:], epsilon)
        return left[:-1] + right
    else:
        return [start, end]


def trace_silhouette():
    subprocess.run(["magick", "-background", "none", SOURCE_SVG, "-resize", "512x512", "/tmp/finalsvg_512.png"], check=True)
    im = Image.open("/tmp/finalsvg_512.png").convert("L")
    thresh = im.point(lambda p: 255 if p > 30 else 0)
    w, h = thresh.size
    pixels = thresh.load()

    visited = set()
    loops = []
    dirs = [(0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)]

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if pixels[x, y] == 255 and (x, y) not in visited:
                if pixels[x - 1, y] == 0 or pixels[x + 1, y] == 0 or pixels[x, y - 1] == 0 or pixels[x, y + 1] == 0:
                    loop = [(x, y)]
                    curr = (x, y)
                    back_dir = 6
                    start = curr
                    for _ in range(50000):
                        visited.add(curr)
                        found = False
                        start_dir = (back_dir + 1) % 8
                        for i in range(8):
                            d_idx = (start_dir + i) % 8
                            dx, dy = dirs[d_idx]
                            nx, ny = curr[0] + dx, curr[1] + dy
                            if 0 <= nx < w and 0 <= ny < h and pixels[nx, ny] == 255:
                                curr = (nx, ny)
                                back_dir = (d_idx + 4) % 8
                                loop.append(curr)
                                found = True
                                break
                        if not found or curr == start:
                            break
                    if len(loop) > 15:
                        simplified = rdp(loop, 1.2)
                        loops.append(simplified)

    path_parts = []
    for loop in loops:
        cmd = f"M {loop[0][0]},{loop[0][1]}"
        for pt in loop[1:]:
            cmd += f" L {pt[0]},{pt[1]}"
        cmd += " Z"
        path_parts.append(cmd)

    return " ".join(path_parts)


def build():
    cutout = Image.open(SOURCE_3D).convert("RGBA")
    full_tile = Image.open(SOURCE_TILE).convert("RGBA")

    bbox = cutout.getbbox()
    left, top, right, bottom = bbox
    mascot_w = right - left
    mascot_h = bottom - top
    mascot_size = max(mascot_w, mascot_h)
    mascot_crop = cutout.crop(bbox)

    # 1. Update app/icon-src/ sources
    os.makedirs("app/icon-src", exist_ok=True)
    cutout.resize((1024, 1024), Image.LANCZOS).save("app/icon-src/ame-mascot-3d.png")
    full_tile.resize((1024, 1024), Image.LANCZOS).save("app/icon-src/ame-mascot-tile.png")
    shutil.copy(SOURCE_SVG, "app/icon-src/ame-mascot.svg")

    # 2. Update root logos
    shutil.copy(SOURCE_SVG, "ame-logo.svg")
    cutout.resize((512, 512), Image.LANCZOS).save("ame-logo-light.png")
    cutout.resize((512, 512), Image.LANCZOS).save("ame-logo-dark.png")

    # 3. Generate launcher mipmaps
    for density, scale in DENSITIES.items():
        fg_px = round(FOREGROUND_DP * scale)
        safe_px = round(SAFE_DP * scale)
        legacy_px = round(LEGACY_DP * scale)

        # FOREGROUND (Adaptive icon: 108dp, transparent canvas)
        fg_canvas = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
        scale_factor = safe_px / mascot_size
        scaled_w = round(mascot_w * scale_factor)
        scaled_h = round(mascot_h * scale_factor)
        scaled_mascot = mascot_crop.resize((scaled_w, scaled_h), Image.LANCZOS)

        paste_x = round((fg_px - scaled_w) / 2.0)
        paste_y = round((fg_px - scaled_h) / 2.0)
        fg_canvas.paste(scaled_mascot, (paste_x, paste_y), scaled_mascot)

        # BACKGROUND (Adaptive icon: 108dp, clean studio background)
        bg_canvas = Image.new("RGB", (fg_px, fg_px), (254, 254, 254))

        # LEGACY ICONS (48dp squircle & round)
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

    # 4. Generate Vector Drawables (ic_app_mark, ic_launcher_monochrome, ic_notification)
    path_data = trace_silhouette()

    app_mark_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  Ame Music Mascot mark ("Ame-chan" with headphones).
  Clean vector glyph with negative-space cutouts for face expression and headphones.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="512"
    android:viewportHeight="512">

    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{path_data}" />

</vector>
"""
    with open(f"{RES}/drawable/ic_app_mark.xml", "w") as f:
        f.write(app_mark_xml)

    monochrome_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  Themed Material You adaptive icon form: single flat colour.
  Ame Music Mascot silhouette.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="512"
    android:viewportHeight="512">

    <group
        android:scaleX="0.72"
        android:scaleY="0.72"
        android:pivotX="256"
        android:pivotY="256">
        <path
            android:fillColor="#FFFFFFFF"
            android:fillType="evenOdd"
            android:pathData="{path_data}" />
    </group>

</vector>
"""
    with open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w") as f:
        f.write(monochrome_xml)

    notification_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  Notification small icon: status bar playback indicator.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="512"
    android:viewportHeight="512">

    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{path_data}" />

</vector>
"""
    with open(f"{RES}/drawable/ic_notification.xml", "w") as f:
        f.write(notification_xml)

    # 5. Update colors.xml launcher background
    colors_xml = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Light studio background plate tone matching Ame adaptive icon -->
    <color name="ic_launcher_background">#FEFEFE</color>
</resources>
"""
    with open(f"{RES}/values/colors.xml", "w") as f:
        f.write(colors_xml)

    print("Successfully generated all Ame icons, mipmaps, and vector drawables!")


if __name__ == "__main__":
    build()
