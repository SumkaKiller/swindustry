"""Paint the kiln control into its PNG atlas; no generated artwork or runtime shapes.

Also writes a 3x preview using Minecraft's actual bitmap font from the local dev jar.
Run from any directory: python tools/draw_kiln_button.py
"""
from pathlib import Path
from io import BytesIO
import json
import zipfile
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / 'src/main/resources/assets/swindustry/textures/gui/clay_kiln.png'
X, Y, W, H = 74, 60, 72, 14
atlas = Image.open(ROOT / 'tools/gui/clay_kiln_base.png').convert('RGBA')
# Build from the unmodified source every time, never from the previous output.
base = atlas.copy()
atlas.paste((0, 0, 0, 0), (0, 0, 176, 256))
atlas.paste(base.crop((0, 0, 176, 72)), (0, 0))
atlas.paste(base.crop((0, 72, 176, 166)), (0, 80))
# Extend only the blank panel and its existing side borders.
for row in range(72, 80):
    atlas.paste(base.crop((0, 79, 176, 80)), (0, row))
atlas.paste((0, 0, 0, 0), (0, 176, 72, 240))

def button(state):
    palettes = [
        ('#373737', '#b5b5b5', '#737373', '#555555', '#ac724b'),
        ('#6f4931', '#eeeeee', '#8b8b8b', '#555555', '#e2a258'),
        ('#737373', '#b5b5b5', '#9e9e9e', '#8b8b8b', '#8b8b8b'),
        ('#6f4931', '#b5b5b5', '#737373', '#555555', '#e2a258'),
    ]
    border, light, face, shadow, ember = palettes[state]
    sprite = Image.new('RGBA', (W, H), border)
    d = ImageDraw.Draw(sprite)
    d.rectangle((1, 1, W-2, H-2), fill=face)
    d.line((1, 1, W-2, 1), fill=light)
    d.line((1, 1, 1, H-3), fill=light)
    d.line((2, H-2, W-2, H-2), fill=shadow)
    d.line((W-2, 2, W-2, H-2), fill=shadow)
    # A short inset ember mark ties the otherwise neutral control to the kiln.
    d.line((W//2-5, H-2, W//2+4, H-2), fill=ember)
    return sprite

for state in range(4):
    atlas.paste(button(state), (0, 176 + state*16))
atlas.paste(button(0), (X, Y))
atlas.save(PATH, optimize=True)

# Preview is a composition of the exact atlas and font; not an in-game screenshot.
jar = ROOT / 'build/moddev/artifacts/neoforge-21.1.234-client-extra-aka-minecraft-resources.jar'
glyphs = {}
with zipfile.ZipFile(jar) as z:
    for p in json.loads(z.read('assets/minecraft/font/include/default.json'))['providers']:
        if p['type'] != 'bitmap':
            continue
        sheet = Image.open(BytesIO(z.read('assets/minecraft/textures/' + p['file'].split(':')[1]))).convert('RGBA')
        cw, ch = sheet.width//len(p['chars'][0]), sheet.height//len(p['chars'])
        for row, chars in enumerate(p['chars']):
            for col, char in enumerate(chars):
                if char == '\0' or char in glyphs:
                    continue
                g = sheet.crop((col*cw, row*ch, (col+1)*cw, (row+1)*ch))
                box = g.getchannel('A').getbbox()
                advance = round((box[2] if box else 0) * 8/ch) + 1
                glyphs[char] = (g.resize((round(cw*8/ch), 8), Image.Resampling.NEAREST), advance)

def text_width(text):
    return sum(4 if c == ' ' else glyphs[c][1] for c in text)

def text(img, value, x, y, color, shadow=False):
    if shadow:
        text(img, value, x+1, y+1, '#333333')
    for char in value:
        if char == ' ':
            x += 4
            continue
        glyph, advance = glyphs[char]
        ink = Image.new('RGBA', glyph.size, color)
        img.paste(ink, (x, y), glyph.getchannel('A'))
        x += advance

preview = Image.new('RGBA', (176*2+24, 174*2+36), '#242424')
for state, label in enumerate(('Растопить', 'Растопить', 'Растопить', 'Обжиг задан')):
    panel = atlas.crop((0, 0, 176, 174))
    panel.paste(button(state), (X, Y))
    title = 'Глиняная печь'
    text(panel, title, (176-text_width(title))//2, 6, '#404040')
    text(panel, 'Инвентарь', 8, 80, '#404040')
    text(panel, label, X+(W-text_width(label))//2, Y+2,
         '#dddddd' if state == 2 else '#ffffff', shadow=True)
    if state == 0:
        normal_preview = panel.resize((528, 522), Image.Resampling.NEAREST)
    preview.paste(panel, (8 + (state%2)*184, 8 + (state//2)*186))
out = ROOT / 'build/gui-preview'
out.mkdir(parents=True, exist_ok=True)
normal_preview.save(out/'kiln-button.png')
preview.resize((preview.width*3, preview.height*3), Image.Resampling.NEAREST).save(out/'kiln-button-states.png')
print('Painted atlas:', PATH)
print('Preview:', out/'kiln-button-states.png')
