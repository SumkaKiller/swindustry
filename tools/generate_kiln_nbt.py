"""
Generates the Minecraft 1.21.1 StructureTemplate NBT file for the Clay Kiln.
Outputs: src/main/resources/data/swindustry/structures/clay_kiln.nbt
"""

import gzip
import struct
import io
import os

# Simple NBT Writer
class NbtWriter:
    TAG_END = 0
    TAG_BYTE = 1
    TAG_SHORT = 2
    TAG_INT = 3
    TAG_LONG = 4
    TAG_FLOAT = 5
    TAG_DOUBLE = 6
    TAG_BYTE_ARRAY = 7
    TAG_STRING = 8
    TAG_LIST = 9
    TAG_COMPOUND = 10
    TAG_INT_ARRAY = 11

    def __init__(self):
        self.buf = io.BytesIO()

    def write_string(self, s: str):
        encoded = s.encode('utf-8')
        self.buf.write(struct.pack('>H', len(encoded)))
        self.buf.write(encoded)

    def write_tag_header(self, tag_type: int, name: str):
        self.buf.write(struct.pack('>b', tag_type))
        self.write_string(name)

    def write_compound_start(self, name: str):
        self.write_tag_header(self.TAG_COMPOUND, name)

    def write_compound_end(self):
        self.buf.write(struct.pack('>b', self.TAG_END))

    def write_int(self, name: str, value: int):
        self.write_tag_header(self.TAG_INT, name)
        self.buf.write(struct.pack('>i', value))

    def write_string_tag(self, name: str, value: str):
        self.write_tag_header(self.TAG_STRING, name)
        self.write_string(value)

    def write_int_array(self, name: str, values: list[int]):
        self.write_tag_header(self.TAG_INT_ARRAY, name)
        self.buf.write(struct.pack('>i', len(values)))
        for v in values:
            self.buf.write(struct.pack('>i', v))

    def write_list_start(self, name: str, element_type: int, count: int):
        self.write_tag_header(self.TAG_LIST, name)
        self.buf.write(struct.pack('>b', element_type))
        self.buf.write(struct.pack('>i', count))

    def get_bytes(self) -> bytes:
        return self.buf.getvalue()


def build_kiln_nbt():
    layers = [
        # y = 0 (Full brick floor)
        [
            " CCC ",
            "CCCCC",
            "CCCCC",
            "CCCCC",
            " CCC "
        ],
        # y = 1 (Port + lower chamber)
        [
            " CPC ",
            "C___C",
            "C___C",
            "C___C",
            " CCC "
        ],
        # y = 2 (Upper chamber)
        [
            " CCC ",
            "C___C",
            "C___C",
            "C___C",
            " CCC "
        ],
        # y = 3 (Dome)
        [
            "     ",
            " CCC ",
            " C_C ",
            " CCC ",
            "     "
        ],
        # y = 4 (1-block tall chimney)
        [
            "     ",
            "  C  ",
            " C_C ",
            "  C  ",
            "     "
        ]
    ]

    palette = [
        "minecraft:structure_void",    # 0: ' ' (ignored)
        "minecraft:air",               # 1: '_' (cavity)
        "swindustry:raw_clay_bricks",  # 2: 'C' (wall)
        "swindustry:clay_kiln_port",   # 3: 'P' (controller)
    ]

    char_to_state = {
        ' ': 0,
        '_': 1,
        'C': 2,
        'P': 3,
    }

    size_x = 5
    size_y = 5
    size_z = 5

    blocks = []
    for y in range(size_y):
        for z in range(size_z):
            for x in range(size_x):
                ch = layers[y][z][x]
                state_idx = char_to_state[ch]
                blocks.append((x, y, z, state_idx))

    w = NbtWriter()
    # Root compound (name is empty in standard NBT files)
    w.write_compound_start("")

    # size
    w.write_int_array("size", [size_x, size_y, size_z])
    w.write_int("DataVersion", 3955)

    # palette
    w.write_list_start("palette", NbtWriter.TAG_COMPOUND, len(palette))
    for block_name in palette:
        w.write_string_tag("Name", block_name)
        w.write_compound_end()

    # blocks
    w.write_list_start("blocks", NbtWriter.TAG_COMPOUND, len(blocks))
    for x, y, z, state_idx in blocks:
        w.write_int_array("pos", [x, y, z])
        w.write_int("state", state_idx)
        w.write_compound_end()

    # entities
    w.write_list_start("entities", NbtWriter.TAG_COMPOUND, 0)

    # end root
    w.write_compound_end()

    nbt_data = w.get_bytes()
    compressed = gzip.compress(nbt_data)
    return compressed


if __name__ == "__main__":
    out_dir = "src/main/resources/data/swindustry/structures"
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "clay_kiln.nbt")
    data = build_kiln_nbt()
    with open(out_path, "wb") as f:
        f.write(data)
    print(f"Generated {out_path} ({len(data)} bytes compressed).")
