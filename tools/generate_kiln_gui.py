"""Generate the pixel-perfect clay kiln container texture.

The screen has more moving pieces than a vanilla furnace, so keeping the source
as drawing instructions prevents the slot frames and overlay sprites from
silently drifting away from their Java coordinates.  Pillow is only needed when
regenerating the asset; it is not a runtime dependency of the mod.
"""

from __future__ import annotations

from pathlib import Path
import random

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/swindustry/textures/gui/clay_kiln.png"

SHEET_SIZE = 256
PANEL_WIDTH = 202
PANEL_HEIGHT = 190

# Java-side coordinates.  Keeping them here makes visual drift obvious during review.
INPUT_SLOT = (28, 34)
FUEL_SLOT = (28, 65)
OUTPUT_SLOT = (158, 50)
PLAYER_SLOTS_X = tuple(20 + column * 18 for column in range(9))
PLAYER_SLOTS_Y = (112, 130, 148)
HOTBAR_Y = 170

FIRE_POS = (66, 61)
FIRE_SIZE = (14, 14)
ARROW_POS = (109, 51)
ARROW_SIZE = (38, 15)
HEAT_POS = (91, 39)
HEAT_SIZE = (6, 35)

OVERLAY_U = 202
FIRE_FRAMES = 3
ARROW_V = 16
HEAT_U = 244


# Restrained clay, soot, brass and parchment palette.
TRANSPARENT = (0, 0, 0, 0)
OUTLINE = (39, 24, 19, 255)
DEEP_SHADOW = (27, 18, 16, 255)
CLAY_DARK = (73, 43, 32, 255)
CLAY = (104, 59, 40, 255)
CLAY_LIGHT = (132, 78, 51, 255)
MORTAR = (55, 35, 29, 255)
BRASS_DARK = (91, 56, 25, 255)
BRASS = (174, 119, 49, 255)
BRASS_LIGHT = (222, 170, 76, 255)
PARCHMENT_DARK = (105, 86, 63, 255)
PARCHMENT = (174, 151, 111, 255)
PARCHMENT_LIGHT = (202, 180, 135, 255)
SLOT = (54, 48, 43, 255)
SLOT_LIGHT = (72, 63, 55, 255)


image = Image.new("RGBA", (SHEET_SIZE, SHEET_SIZE), TRANSPARENT)
draw = ImageDraw.Draw(image)
rng = random.Random(0x5EA_70A7)


def rect(box: tuple[int, int, int, int], color: tuple[int, int, int, int]) -> None:
    draw.rectangle(box, fill=color)


def pixel(x: int, y: int, color: tuple[int, int, int, int]) -> None:
    draw.point((x, y), fill=color)


def clipped_panel() -> None:
    """Outer frame with two-pixel cut corners, like a vanilla container."""

    outer = [(2, 0), (199, 0), (201, 2), (201, 187), (199, 189),
             (2, 189), (0, 187), (0, 2)]
    draw.polygon(outer, fill=OUTLINE)
    inner = [(3, 1), (198, 1), (200, 3), (200, 186), (198, 188),
             (3, 188), (1, 186), (1, 3)]
    draw.polygon(inner, fill=BRASS)
    rect((3, 2, 198, 3), BRASS_LIGHT)
    rect((2, 4, 3, 185), BRASS_LIGHT)
    rect((3, 186, 198, 187), BRASS_DARK)
    rect((198, 4, 199, 185), BRASS_DARK)
    rect((4, 4, 197, 185), CLAY_DARK)


def brick_field(x0: int, y0: int, x1: int, y1: int) -> None:
    """Deterministic, low-contrast unfired brickwork."""

    rect((x0, y0, x1, y1), MORTAR)
    row_height = 7
    for row, y in enumerate(range(y0, y1 + 1, row_height)):
        offset = -9 if row % 2 else -2
        x = x0 + offset
        brick_index = 0
        while x <= x1:
            width = (19, 22, 17, 24)[(row * 3 + brick_index) % 4]
            left = max(x0, x + 1)
            right = min(x1, x + width - 1)
            bottom = min(y1, y + row_height - 2)
            if left <= right and y + 1 <= bottom:
                tone = (CLAY_DARK, CLAY, CLAY, CLAY_LIGHT)[rng.randrange(4)]
                rect((left, y + 1, right, bottom), tone)
                if bottom > y + 2:
                    rect((left, y + 1, right, y + 1), CLAY_LIGHT)
                    rect((left, bottom, right, bottom), CLAY_DARK)
                if right - left > 8 and rng.random() < 0.32:
                    pixel(left + 3 + rng.randrange(right - left - 5), y + 3, CLAY_DARK)
            x += width
            brick_index += 1


def inset(box: tuple[int, int, int, int], fill: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    rect((x0, y0, x1, y1), OUTLINE)
    rect((x0 + 1, y0 + 1, x1 - 1, y1 - 1), BRASS_DARK)
    rect((x0 + 2, y0 + 2, x1 - 2, y1 - 2), fill)
    rect((x0 + 2, y0 + 2, x1 - 2, y0 + 2), BRASS_LIGHT)
    rect((x0 + 2, y0 + 2, x0 + 2, y1 - 2), BRASS_LIGHT)


def slot(item_x: int, item_y: int) -> None:
    """Draw an 18x18 frame around Minecraft's 16x16 item position."""

    x0, y0 = item_x - 1, item_y - 1
    rect((x0, y0, x0 + 17, y0 + 17), OUTLINE)
    rect((x0 + 1, y0 + 1, x0 + 16, y0 + 16), BRASS_DARK)
    rect((item_x, item_y, item_x + 15, item_y + 15), SLOT)
    rect((item_x, item_y, item_x + 15, item_y), SLOT_LIGHT)
    rect((item_x, item_y, item_x, item_y + 15), SLOT_LIGHT)
    rect((item_x + 15, item_y + 1, item_x + 15, item_y + 15), DEEP_SHADOW)
    rect((item_x + 1, item_y + 15, item_x + 15, item_y + 15), DEEP_SHADOW)


def firebox() -> None:
    """Soot-black arched opening around the animated flame sprite."""

    # Clay/brass lip.
    rect((62, 55, 83, 76), OUTLINE)
    rect((64, 51, 81, 76), OUTLINE)
    rect((66, 48, 79, 76), OUTLINE)
    rect((68, 46, 77, 76), OUTLINE)
    # Recess.
    rect((69, 48, 76, 48), DEEP_SHADOW)
    rect((67, 50, 78, 54), DEEP_SHADOW)
    rect((65, 53, 80, 75), DEEP_SHADOW)
    rect((66, 60, 79, 74), (35, 24, 20, 255))
    # Uneven clay rim catches the light without becoming steampunk trim.
    rect((64, 55, 64, 73), CLAY_LIGHT)
    rect((66, 50, 66, 54), CLAY_LIGHT)
    rect((68, 48, 68, 49), CLAY_LIGHT)
    rect((65, 75, 80, 76), CLAY_DARK)


def gauge_frame() -> None:
    inset((87, 34, 100, 77), DEEP_SHADOW)
    # Exact dynamic fill cavity: x91..96, y39..73.
    rect((90, 38, 97, 74), OUTLINE)
    rect((91, 39, 96, 73), (43, 29, 24, 255))
    for tick_y in (39, 47, 56, 65, 73):
        rect((98, tick_y, 100, tick_y), BRASS_LIGHT)


def arrow_outline() -> None:
    """Dark empty progress channel matching the 38x15 overlay mask."""

    points = [(108, 55), (136, 55), (136, 50), (148, 58),
              (136, 67), (136, 62), (108, 62)]
    draw.polygon(points, fill=OUTLINE)
    points_inner = [(110, 56), (137, 56), (137, 53), (146, 58),
                    (137, 65), (137, 61), (110, 61)]
    draw.polygon(points_inner, fill=DEEP_SHADOW)


def inventory_panel() -> None:
    rect((4, 99, 197, 185), PARCHMENT_DARK)
    rect((5, 101, 196, 184), PARCHMENT)
    rect((6, 102, 195, 102), PARCHMENT_LIGHT)
    rect((6, 103, 6, 183), PARCHMENT_LIGHT)
    # Sparse paper fibre, visible only as hand-authored pixel variation.
    for _ in range(150):
        x = rng.randrange(8, 194)
        y = rng.randrange(104, 183)
        if rng.random() < 0.55:
            pixel(x, y, PARCHMENT_LIGHT)
        else:
            pixel(x, y, PARCHMENT_DARK)
    for y in PLAYER_SLOTS_Y:
        for x in PLAYER_SLOTS_X:
            slot(x, y)
    rect((11, 166, 190, 168), PARCHMENT_DARK)
    rect((12, 166, 189, 166), PARCHMENT_LIGHT)
    for x in PLAYER_SLOTS_X:
        slot(x, HOTBAR_Y)


def status_band() -> None:
    rect((5, 81, 196, 99), OUTLINE)
    rect((7, 83, 194, 97), (58, 37, 29, 255))
    rect((8, 84, 193, 84), CLAY_LIGHT)
    rect((8, 96, 193, 96), CLAY_DARK)
    # Indicator recess; the current status colour is drawn by Java on top.
    rect((11, 85, 20, 94), DEEP_SHADOW)
    pixel(9, 88, BRASS)
    pixel(192, 88, BRASS_DARK)


def title_and_tier_plates() -> None:
    inset((7, 6, 151, 22), (64, 39, 30, 255))
    # Leave a broad clean title surface for translated text.
    rect((10, 9, 148, 19), (66, 40, 30, 255))
    inset((158, 6, 193, 22), (55, 35, 29, 255))


def draw_flame_frame(frame: int) -> None:
    ox = OVERLAY_U + frame * FIRE_SIZE[0]
    oy = 0
    # Each frame keeps the same base and shifts only the tongue silhouettes.
    dark = (169, 55, 20, 255)
    orange = (238, 104, 26, 255)
    gold = (255, 181, 46, 255)
    pale = (255, 227, 112, 255)
    silhouettes = (
        [(6, 0), (4, 4), (3, 8), (2, 12), (11, 12), (10, 7), (8, 4)],
        [(7, 0), (7, 4), (10, 7), (11, 12), (2, 12), (3, 7), (5, 4)],
        [(5, 0), (6, 4), (9, 6), (11, 12), (2, 12), (3, 8), (4, 5)],
    )
    draw.polygon([(ox + x, oy + y) for x, y in silhouettes[frame]], fill=dark)
    draw.polygon([(ox + 4, oy + 12), (ox + 5, oy + 6), (ox + 7, oy + 3),
                  (ox + 9, oy + 8), (ox + 9, oy + 12)], fill=orange)
    draw.polygon([(ox + 5, oy + 12), (ox + 6, oy + 7), (ox + 8, oy + 6),
                  (ox + 8, oy + 12)], fill=gold)
    rect((ox + 6, oy + 10, ox + 7, oy + 12), pale)
    rect((ox + 2, oy + 13, ox + 11, oy + 13), dark)


def draw_arrow_sprite() -> None:
    ox, oy = OVERLAY_U, ARROW_V
    dark = (151, 83, 22, 255)
    gold = (222, 143, 37, 255)
    light = (246, 192, 74, 255)
    points = [(0, 4), (27, 4), (27, 0), (37, 7), (27, 14), (27, 10), (0, 10)]
    draw.polygon([(ox + x, oy + y) for x, y in points], fill=dark)
    points_inner = [(1, 5), (28, 5), (28, 3), (35, 7), (28, 12), (28, 9), (1, 9)]
    draw.polygon([(ox + x, oy + y) for x, y in points_inner], fill=gold)
    rect((ox + 2, oy + 5, ox + 27, oy + 5), light)


def draw_heat_sprite() -> None:
    ox, oy = HEAT_U, 0
    bands = (
        ((249, 175, 58, 255), (255, 211, 94, 255), 0, 7),
        ((226, 108, 28, 255), (247, 145, 40, 255), 8, 16),
        ((177, 67, 24, 255), (205, 83, 25, 255), 17, 25),
        ((126, 47, 25, 255), (153, 58, 25, 255), 26, 34),
    )
    # The texture is sampled bottom-up: a nearly empty gauge therefore leaves only the dark,
    # cooling band, while a full burn reaches the pale-hot pixels at the top.
    for color, highlight, y0, y1 in bands:
        rect((ox, oy + y0, ox + 5, oy + y1), color)
        rect((ox, oy + y0, ox, oy + y1), highlight)


clipped_panel()
brick_field(4, 4, 197, 98)
title_and_tier_plates()
slot(*INPUT_SLOT)
slot(*FUEL_SLOT)
slot(*OUTPUT_SLOT)
firebox()
gauge_frame()
arrow_outline()
status_band()
inventory_panel()

for frame_index in range(FIRE_FRAMES):
    draw_flame_frame(frame_index)
draw_arrow_sprite()
draw_heat_sprite()

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUTPUT, optimize=True)
print(OUTPUT)
