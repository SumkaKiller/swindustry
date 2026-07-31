package com.jokerdayn.swindustry.checks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerdayn.swindustry.blueprint.MultiblockBlueprints;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/** Fast headless invariants for the early-game goggles, drafting table and blueprint assets. */
public final class BlueprintFlowCheck {

    private BlueprintFlowCheck() {}

    public static void main(String[] args) throws Exception {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        checkEarlyRecipes();
        checkBlueprintDefinition();
        checkResourcesAndTextures();
        checkTranslations();
        System.out.println("Blueprint flow checks passed.");
    }

    private static void checkEarlyRecipes() throws IOException {
        JsonObject goggles = json("data/swindustry/recipe/primitive_engineer_goggles.json");
        assertEquals("minecraft:crafting_shaped", goggles.get("type").getAsString(),
            "goggles recipe type");
        assertEquals("equipment", goggles.get("category").getAsString(),
            "goggles recipe-book category");
        Set<String> goggleItems = ingredientItems(goggles);
        assertEquals(Set.of("minecraft:stick", "minecraft:paper", "minecraft:string"),
            goggleItems, "goggles must remain craftable before the first firing");
        assertEquals("swindustry:primitive_engineer_goggles",
            goggles.getAsJsonObject("result").get("id").getAsString(),
            "goggles recipe result");

        JsonObject table = json("data/swindustry/recipe/drafting_table.json");
        assertEquals("building", table.get("category").getAsString(),
            "drafting table recipe-book category");
        Set<String> tableItems = ingredientItems(table);
        assertEquals(Set.of("minecraft:paper", "minecraft:stick"), tableItems,
            "drafting table item ingredients");
        JsonObject tableKey = table.getAsJsonObject("key");
        assertEquals("minecraft:planks", tableKey.getAsJsonObject("W").get("tag").getAsString(),
            "drafting table wood tag");

        JsonObject cordage = json("data/swindustry/recipe/reed_cordage.json");
        assertEquals("minecraft:crafting_shaped", cordage.get("type").getAsString(),
            "reed cordage recipe type");
        assertEquals("misc", cordage.get("category").getAsString(),
            "reed cordage recipe-book category");
        assertEquals(Set.of("minecraft:sugar_cane"), ingredientItems(cordage),
            "reed cordage early ingredient");
        assertEquals("CCC", cordage.getAsJsonArray("pattern").get(0).getAsString(),
            "reed cordage requires three canes");
        assertEquals("minecraft:string",
            cordage.getAsJsonObject("result").get("id").getAsString(),
            "reed cordage result");
        assertEquals(1, cordage.getAsJsonObject("result").get("count").getAsInt(),
            "reed cordage output count");

        JsonObject drafting = json("data/swindustry/recipe/drafting/clay_kiln.json");
        assertEquals("swindustry:drafting", drafting.get("type").getAsString(),
            "drafting recipe type");
        assertEquals("swindustry:clay_kiln_port",
            drafting.getAsJsonObject("reference").get("item").getAsString(),
            "kiln blueprint reusable reference");
        assertEquals("swindustry:clay_kiln_blueprint",
            drafting.getAsJsonObject("result").get("id").getAsString(),
            "kiln blueprint result");

        JsonObject calibration = json("data/swindustry/recipe/goggle_calibration.json");
        assertEquals("swindustry:goggle_calibration", calibration.get("type").getAsString(),
            "fallback calibration recipe type");
    }

    private static void checkBlueprintDefinition() {
        MultiblockBlueprints.Definition kiln = MultiblockBlueprints.CLAY_KILN;
        if (MultiblockBlueprints.byId(kiln.id()).orElseThrow() != kiln) {
            throw new AssertionError("Registered kiln blueprint did not resolve to its definition");
        }
        if (MultiblockBlueprints.byId(kiln.id().withPath("does_not_exist")).isPresent()) {
            throw new AssertionError("Unknown blueprint id unexpectedly resolved");
        }

        int[] roles = new int[BlockMatcher.Role.values().length];
        kiln.pattern().get().forEachCell((offset, symbol, matcher) -> roles[matcher.role().ordinal()]++);
        assertEquals(44, roles[BlockMatcher.Role.WALL.ordinal()], "kiln projected wall count");
        assertEquals(22, roles[BlockMatcher.Role.CAVITY.ordinal()], "kiln projected cavity count");
        assertEquals(44, kiln.pattern().get().blockCount(), "kiln material block count");
    }

    private static void checkResourcesAndTextures() throws IOException {
        require("assets/swindustry/models/item/primitive_engineer_goggles.json");
        require("assets/swindustry/models/item/clay_kiln_blueprint.json");
        require("assets/swindustry/models/item/drafting_table.json");
        require("assets/swindustry/models/block/drafting_table.json");
        require("assets/swindustry/blockstates/drafting_table.json");
        require("data/swindustry/loot_table/blocks/drafting_table.json");

        JsonObject states = json("assets/swindustry/blockstates/drafting_table.json");
        JsonObject variants = states.getAsJsonObject("variants");
        assertEquals(Set.of("facing=north", "facing=east", "facing=south", "facing=west"),
            variants.keySet(), "drafting table facing models");

        checkImage("assets/swindustry/textures/item/primitive_engineer_goggles.png", 16, 16, true);
        checkImage("assets/swindustry/textures/item/clay_kiln_blueprint.png", 16, 16, true);
        checkImage("assets/swindustry/textures/models/armor/engineer_goggles_layer_1.png", 64, 32, true);
    }

    private static void checkTranslations() throws IOException {
        Set<String> required = Set.of(
            "container.swindustry.drafting_table",
            "container.swindustry.drafting_table.paper_hint",
            "container.swindustry.drafting_table.ink_hint",
            "container.swindustry.drafting_table.reference_hint",
            "container.swindustry.drafting_table.result_hint",
            "item.swindustry.primitive_engineer_goggles.empty",
            "item.swindustry.primitive_engineer_goggles.loaded",
            "item.swindustry.multiblock_blueprint.calibration_hint",
            "message.swindustry.blueprint.calibrated",
            "message.swindustry.goggles.structure_incomplete"
        );
        for (String locale : Set.of("en_us", "ru_ru")) {
            JsonObject language = json("assets/swindustry/lang/" + locale + ".json");
            for (String key : required) {
                if (!language.has(key) || language.get(key).getAsString().isBlank()) {
                    throw new AssertionError(locale + " is missing translation " + key);
                }
            }
        }
    }

    private static Set<String> ingredientItems(JsonObject recipe) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject("key").entrySet()) {
            JsonObject ingredient = entry.getValue().getAsJsonObject();
            if (ingredient.has("item")) {
                ids.add(ingredient.get("item").getAsString());
            }
        }
        return Set.copyOf(ids);
    }

    private static void checkImage(String path, int expectedWidth, int expectedHeight,
                                   boolean requireTransparency) throws IOException {
        BufferedImage image;
        try (InputStream stream = resource(path)) {
            image = ImageIO.read(stream);
        }
        if (image == null) {
            throw new AssertionError(path + " is not a readable PNG");
        }
        assertEquals(expectedWidth, image.getWidth(), path + " width");
        assertEquals(expectedHeight, image.getHeight(), path + " height");

        boolean transparent = false;
        boolean visible = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                visible |= alpha != 0;
            }
        }
        if (!visible || requireTransparency && !transparent) {
            throw new AssertionError(path + " must contain both visible and transparent pixels");
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = resource(path);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void require(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            if (stream.read() < 0) {
                throw new IOException("Empty classpath resource " + path);
            }
        }
    }

    private static InputStream resource(String path) throws IOException {
        InputStream stream = BlueprintFlowCheck.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Missing classpath resource " + path);
        }
        return stream;
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
