"""Warm and quantize the existing clay masonry without changing its shapes.

The original 16x16 art used seventy near-identical beige colours.  This keeps
the hand-painted pixel placement but maps it onto a small, deliberately warm
unfired-clay palette.  The kiln opening and flame colours are left untouched.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "src/main/resources/assets/swindustry/textures/block"
TEXTURES = (
    "raw_clay_bricks.png",
    "clay_kiln_port_front.png",
    "clay_kiln_port_front_lit.png",
)

CLAY_PALETTE = (
    (79, 55, 42, 255),
    (92, 62, 46, 255),
    (106, 70, 51, 255),
    (121, 79, 57, 255),
    (137, 90, 64, 255),
    (153, 102, 73, 255),
    (170, 116, 84, 255),
    (188, 134, 99, 255),
)


def luminance(color: tuple[int, int, int, int]) -> float:
    red, green, blue, _ = color
    return red * 0.2126 + green * 0.7152 + blue * 0.0722


def build_mapping(source: Image.Image) -> dict[tuple[int, int, int, int], tuple[int, int, int, int]]:
    colors = set(source.convert("RGBA").get_flattened_data())
    darkest = min(luminance(color) for color in colors)
    lightest = max(luminance(color) for color in colors)
    span = max(1.0, lightest - darkest)
    mapping = {}
    for color in colors:
        position = (luminance(color) - darkest) / span
        index = min(len(CLAY_PALETTE) - 1, round(position * (len(CLAY_PALETTE) - 1)))
        mapping[color] = CLAY_PALETTE[index]
    return mapping


def transform(source: Image.Image, mapping: dict) -> Image.Image:
    rgba = source.convert("RGBA")
    output = Image.new("RGBA", rgba.size)
    output.putdata([mapping.get(color, color) for color in rgba.get_flattened_data()])
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=SOURCE_DIR,
        help="Destination directory; defaults to the live resource directory.",
    )
    args = parser.parse_args()

    mapping = build_mapping(Image.open(SOURCE_DIR / TEXTURES[0]))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for filename in TEXTURES:
        with Image.open(SOURCE_DIR / filename) as source:
            transformed = transform(source, mapping)
            destination = args.output_dir / filename
            transformed.save(destination, optimize=True)
            print(destination)


if __name__ == "__main__":
    main()
