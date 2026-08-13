package com.jokerdayn.swindustry.checks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/** Regression check for the coordinate-sensitive clay kiln texture atlas. */
public final class GuiTextureCheck {

    private static final Path TEXTURE = Path.of(
        "src/main/resources/assets/swindustry/textures/gui/clay_kiln.png");
    private static final Path ASSETS = Path.of("src/main/resources/assets/swindustry");

    private static final int SHEET_SIZE = 256;
    private static final int PANEL_WIDTH = 202;
    private static final int PANEL_HEIGHT = 190;

    private GuiTextureCheck() {}

    public static void main(String[] args) throws IOException {
        BufferedImage image = ImageIO.read(TEXTURE.toFile());
        check(image != null, "texture could not be decoded");
        check(image.getWidth() == SHEET_SIZE && image.getHeight() == SHEET_SIZE,
            "expected a 256x256 atlas, got " + image.getWidth() + "x" + image.getHeight());

        // Cut corners are intentionally transparent, but every functional part of the window must
        // have a background.  This catches the old 176x166 sheet immediately.
        check(alphaCount(image, 0, 0, PANEL_WIDTH, PANEL_HEIGHT) >= 38_000,
            "the 202x190 panel contains an unexpected transparent region");
        requireOpaqueRect(image, 27, 33, 18, 18, "input slot frame");
        requireOpaqueRect(image, 27, 64, 18, 18, "fuel slot frame");
        requireOpaqueRect(image, 157, 49, 18, 18, "output slot frame");
        requireOpaqueRect(image, 19, 111, 162, 54, "player inventory grid");
        requireOpaqueRect(image, 19, 169, 162, 18, "hotbar");

        check(alphaCount(image, 202, 0, 14, 14) >= 65, "fire frame 1 is missing");
        check(alphaCount(image, 216, 0, 14, 14) >= 65, "fire frame 2 is missing");
        check(alphaCount(image, 230, 0, 14, 14) >= 65, "fire frame 3 is missing");
        check(alphaCount(image, 202, 16, 38, 15) >= 125, "progress arrow is missing");
        check(alphaCount(image, 244, 0, 6, 35) == 210, "heat column is incomplete");

        check(noPixelsOutsideAtlasRegions(image),
            "opaque pixels escaped the panel or declared overlay sprite regions");

        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    colors.add(argb);
                }
            }
        }
        check(colors.size() <= 40,
            "kiln GUI uses " + colors.size() + " opaque colours; keep the pixel palette restrained");

        verifyReferencedTextures();
        verifyKilnTranslations();

        System.out.println("Clay kiln GUI texture checks passed (" + colors.size() + " colours).");
    }

    private static void verifyReferencedTextures() throws IOException {
        Path models = ASSETS.resolve("models");
        try (var paths = Files.walk(models)) {
            for (Path model : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(model)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : null;
                    if (textures == null) {
                        continue;
                    }
                    for (var entry : textures.entrySet()) {
                        JsonElement value = entry.getValue();
                        if (!value.isJsonPrimitive()) {
                            continue;
                        }
                        String id = value.getAsString();
                        String prefix = "swindustry:";
                        if (!id.startsWith(prefix)) {
                            continue;
                        }
                        Path texture = ASSETS.resolve("textures")
                            .resolve(id.substring(prefix.length()).replace('/', java.io.File.separatorChar) + ".png");
                        check(Files.isRegularFile(texture),
                            model + " references missing texture " + texture);
                    }
                }
            }
        }

        requireFile("textures/models/armor/engineer_goggles_layer_1.png");
        requireFile("textures/models/armor/bronze_layer_1.png");
        requireFile("textures/models/armor/bronze_layer_2.png");
    }

    private static void requireFile(String relativePath) {
        Path file = ASSETS.resolve(relativePath);
        check(Files.isRegularFile(file), "required asset is missing: " + file);
    }

    private static void verifyKilnTranslations() throws IOException {
        Set<String> required = Set.of(
            "container.swindustry.clay_kiln.slot.input",
            "container.swindustry.clay_kiln.slot.fuel",
            "container.swindustry.clay_kiln.slot.output",
            "container.swindustry.clay_kiln.burn_time",
            "container.swindustry.clay_kiln.hint.incomplete",
            "container.swindustry.clay_kiln.hint.idle",
            "container.swindustry.clay_kiln.hint.invalid_recipe",
            "container.swindustry.clay_kiln.hint.needs_fuel",
            "container.swindustry.clay_kiln.hint.output_blocked",
            "container.swindustry.clay_kiln.hint.tier_too_low",
            "container.swindustry.clay_kiln.hint.working"
        );
        for (String locale : Set.of("en_us", "ru_ru")) {
            Path languageFile = ASSETS.resolve("lang/" + locale + ".json");
            try (Reader reader = Files.newBufferedReader(languageFile)) {
                JsonObject language = JsonParser.parseReader(reader).getAsJsonObject();
                for (String key : required) {
                    check(language.has(key) && !language.get(key).getAsString().isBlank(),
                        locale + " is missing kiln GUI translation " + key);
                }
            }
        }
    }

    private static boolean noPixelsOutsideAtlasRegions(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                boolean panel = x < PANEL_WIDTH && y < PANEL_HEIGHT;
                boolean fireFrames = x >= 202 && x < 244 && y < 14;
                boolean arrow = x >= 202 && x < 240 && y >= 16 && y < 31;
                boolean heat = x >= 244 && x < 250 && y < 35;
                if (!panel && !fireFrames && !arrow && !heat) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void requireOpaqueRect(BufferedImage image, int x, int y, int width, int height,
                                          String name) {
        check(alphaCount(image, x, y, width, height) == width * height, name + " is not fully opaque");
    }

    private static int alphaCount(BufferedImage image, int x, int y, int width, int height) {
        int count = 0;
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) {
                if ((image.getRGB(px, py) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
