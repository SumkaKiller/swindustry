package com.jokerdayn.swindustry.checks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Fast resource invariants for the Stage 1 bronze equipment progression. */
public final class Stage1ProgressionCheck {

    private static final Set<String> DISABLED_VANILLA_WATERCRAFT_RECIPES = Set.of(
        "oak_boat",
        "oak_chest_boat",
        "spruce_boat",
        "spruce_chest_boat",
        "birch_boat",
        "birch_chest_boat",
        "jungle_boat",
        "jungle_chest_boat",
        "acacia_boat",
        "acacia_chest_boat",
        "dark_oak_boat",
        "dark_oak_chest_boat",
        "mangrove_boat",
        "mangrove_chest_boat",
        "cherry_boat",
        "cherry_chest_boat",
        "bamboo_raft",
        "bamboo_chest_raft"
    );

    private static final Map<String, String> BRONZE_ITEM_TAGS = Map.ofEntries(
        Map.entry("swords", "swindustry:bronze_sword"),
        Map.entry("axes", "swindustry:bronze_axe"),
        Map.entry("pickaxes", "swindustry:bronze_pickaxe"),
        Map.entry("shovels", "swindustry:bronze_shovel"),
        Map.entry("hoes", "swindustry:bronze_hoe"),
        Map.entry("head_armor", "swindustry:bronze_helmet"),
        Map.entry("chest_armor", "swindustry:bronze_chestplate"),
        Map.entry("leg_armor", "swindustry:bronze_leggings"),
        Map.entry("foot_armor", "swindustry:bronze_boots")
    );

    private Stage1ProgressionCheck() {}

    public static void main(String[] args) throws Exception {
        for (Map.Entry<String, String> binding : BRONZE_ITEM_TAGS.entrySet()) {
            checkTagBinding(binding.getKey(), binding.getValue());
        }
        for (String recipe : DISABLED_VANILLA_WATERCRAFT_RECIPES) {
            checkDisabledVanillaRecipe(recipe);
        }
        System.out.println("Stage 1 progression checks passed.");
    }

    private static void checkTagBinding(String tag, String expectedItem) throws IOException {
        String path = "data/minecraft/tags/item/" + tag + ".json";
        JsonObject root = json(path);
        if (!root.has("replace") || root.get("replace").getAsBoolean()) {
            throw new AssertionError(path + " must merge with the vanilla tag");
        }
        Set<String> values = root.getAsJsonArray("values").asList().stream()
            .map(JsonElement::getAsString)
            .collect(Collectors.toUnmodifiableSet());
        if (!values.contains(expectedItem)) {
            throw new AssertionError(path + " is missing " + expectedItem);
        }
    }

    private static void checkDisabledVanillaRecipe(String recipe) throws IOException {
        String path = "data/minecraft/recipe/" + recipe + ".json";
        JsonObject root = json(path);
        JsonElement conditions = root.get("neoforge:conditions");
        boolean disabled = conditions != null
            && conditions.isJsonArray()
            && conditions.getAsJsonArray().asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .anyMatch(condition -> condition.has("type")
                    && "neoforge:false".equals(condition.get("type").getAsString()));
        if (!disabled) {
            throw new AssertionError(path + " must be disabled by neoforge:false");
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = Stage1ProgressionCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing classpath resource " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
