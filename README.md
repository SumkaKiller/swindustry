# SW: Industry

The multiblock half of the **Sea Worthy** pack. Ships alongside [SW: Worldgen Core](../swgencore),
which owns the ocean, the islands and the bronze ore this mod's progression is built around.

Minecraft 1.21.1 / NeoForge 21.1.234 / Java 21.

```bash
cd ../swgencore && ./gradlew build     # build the sibling first; runs need its jar
cd ../swindustry && ./gradlew build    # compiles, jars, and runs the geometry checks
./gradlew runClient                    # copies the sibling jar into run/mods, then launches
```

---

## The multiblock framework

Everything in this pack is meant to prove that a factory is not one block, so the framework comes
first and the machines are written on top of it.

### Declaring a shape

A `MultiblockPattern` can be loaded directly from an exported in-game structure template (`.nbt`) via `StructurePatternLoader` (recommended for medium and large multiblocks) or declared via layer strings with `MultiblockPattern.builder()`.

```java
// KilnPatterns binds the template at server start and recompiles it on every datapack reload:
public static void initialize(ResourceManager resources) {
    clayKiln = StructurePatternLoader.builder()
        .fromResource(resources, SWIndustry.id("structures/clay_kiln")) // datapack-overridable
        .map(ModBlocks.RAW_CLAY_BRICKS, BlockMatcher.tag(ModTags.Blocks.KILN_WALL))
        .controller(ModBlocks.CLAY_KILN_PORT)
        .build();
}
```

Alternatively, smaller 3x3 machines can still be authored via matrix layers:

```java
public static final MultiblockPattern MY_MACHINE = MultiblockPatterns.register(
    MultiblockPattern.builder()
        .layer(" CPC ",
               "C___C",
               " CCC ")
        .where('C', BlockMatcher.tag(ModTags.Blocks.MY_WALL))
        .where('P', BlockMatcher.block(ModBlocks.MY_CONTROLLER))
        .where('_', BlockMatcher.cavity())
        .where(' ', BlockMatcher.ignored())
        .controller('P')
        .build());
```

Four roles of cell, and getting them right matters as much as the shape:

| Role | Meaning |
|---|---|
| `WALL` | part of the machine; breaking one takes the machine apart |
| `CAVITY` | must stay clear for the machine to work |
| `IGNORED` | only there to keep the strings rectangular; players may build here |

Match walls by **tag**, not by block. A later tier of the same machine is then a datapack entry
rather than a second pattern.

### Orientation

Author the pattern as if the controller faced **north**, which puts the body along +Z behind it.
`MultiblockPattern#match` rotates into any of the four horizontal facings, and nothing else in the
codebase has to think about rotation. `./gradlew multiblockChecks` verifies the arithmetic in all
four facings, including that the body ends up *behind* the controller rather than in front of it.

### Building a machine on it

1. A wall block extends `MultiblockPartBlock`. That is all — no block entity, no stored owner. Its
   only job is telling its machine when it is mined, via the reverse lookup in `MultiblockPatterns`.
2. The controller's block entity extends `MultiblockControllerEntity` and answers two questions:
   which pattern, and which way is the controller facing.
3. Its ticker calls `revalidateIfStale()`. That runs the pattern at most once a second; a broken
   wall triggers a check immediately, so a machine can never keep working after being taken apart.

`MultiblockControllerEntity` never writes the assembled state to disk. On load it simply checks
again, which means a world edited while the server was down can't leave a machine believing in
blocks that are no longer there.

---

## What stage 1 adds

| | |
|---|---|
| `primitive_campfire` | sticks and logs. Strike flint against stone over it: 15%, 25%, 50%, 75%, then 100% on the fifth strike. Burns for 60 s, then leaves embers holding 1–5 charcoal and 1–3 soot. |
| `raw_clay_bricks` | the kiln's shell, tagged `swindustry:kiln_wall` |
| `clay_kiln_port` | the kiln's mouth and its controller |
| primitive engineer's goggles | an early wooden sighting frame; worn near an unfinished machine it draws its silhouette |
| `soot` | what a burnt-out campfire leaves behind |
| bronze tools and armour | tier between stone and iron, but with iron's mining level — see `ModToolTiers` |

Vanilla boats, rafts and their chest variants are deliberately recipe-disabled. They remain valid
entities for maps and commands, but ordinary planks cannot bypass the pack's shipbuilding
progression.

### Goggles make the machine visible

The island's sugar cane closes the only early-material gap: three cane can be twisted into one
string. Sticks, paper and that cordage make the primitive goggles without glass or metal. They are a
wooden sighting frame, not armour and not impossible pre-kiln optics.

Place a loading port where the kiln will stand, put the goggles on, and the unfinished machine
draws itself around you: translucent ghost blocks mark positions to fill, red boxes mark cavities
that must stay clear, and cyan outlines trace every missing wall. Sneak-use the port for an exact
count. The server sends what it matched (`swindustry:structure_verdict`), so what you see is
exactly what the server sees — never a guess from an older jar.

The kiln is `KilnPatterns.clayKiln()`: 56 clay bricks and the loading port, taken block for block
from
`furnace.nbt`. A 3×3×2 firebox with the corners knocked off, drawing in to a throat and a
three-block flue. It runs ordinary `minecraft:smelting` recipes — the pack removes the vanilla
furnace, so it has to be able to cook a fish — plus its own `swindustry:kiln` type for the things
only a kiln may do. Bronze is the first of those.

`KilnRecipe#minTier` is the hook for upgrades: a recipe can demand a better kiln than the one it is
sitting in, and be visibly refused until the shell is rebuilt.

---

## Numbers worth tuning

Most balance lives in `world/serverconfig/swindustry-server.toml` — a **server** config, so the
values sync to clients automatically and both sides of a strike agree. Two
datapack-driven values are the most likely to want changing after a playtest:

- **`data/swindustry/recipe/raw_clay_bricks.json`** — four clay balls make two brick blocks, so a
  full kiln costs about 102 clay balls. Beach clay is scarce: `SurfacePalette` in SW: Worldgen Core
  only places it where `detail < 0.02`, roughly 2% of beach surface. If gathering it turns into a
  chore, raise that threshold rather than making bricks cheaper — the kiln *should* be an
  expedition, it just should not be a grind.
- **`data/swindustry/loot_table/blocks/primitive_campfire.json`** — what a burnt-out fire yields.

## Reaching across to SW: Worldgen Core

There is no compile-time dependency in either direction. This mod finds bronze through
`#c:ingots/bronze` and `#c:raw_materials/bronze`, and the island's loose stones through
`#swindustry:fire_strikers/stone`. The dependency runs one way only — Industry knows about Worldgen
Core, never the reverse — which is why the `rock` item lives over there, next to the block that
drops it.
