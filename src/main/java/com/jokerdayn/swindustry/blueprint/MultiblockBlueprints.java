package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.KilnPatterns;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** The small bridge between a blueprint item, a controller block and a multiblock pattern. */
public final class MultiblockBlueprints {

    private static final Map<ResourceLocation, Definition> DEFINITIONS = new LinkedHashMap<>();

    public static final Definition CLAY_KILN = register(new Definition(
        SWIndustry.id("clay_kiln"),
        () -> KilnPatterns.clayKiln(),
        ModBlocks.CLAY_KILN_PORT,
        "blueprint.swindustry.clay_kiln"));

    private MultiblockBlueprints() {}

    private static Definition register(Definition definition) {
        Definition previous = DEFINITIONS.put(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate multiblock blueprint id " + definition.id());
        }
        return definition;
    }

    /** Finds a blueprint definition by its unique identifier. */
    public static Optional<Definition> byId(ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    /**
     * Blueprint registration metadata linking a multiblock pattern with its corresponding controller block.
     *
     * @param id             unique identifier of the blueprint definition
     * @param pattern        supplier resolving to the multiblock pattern definition
     * @param controller     supplier resolving to the machine controller block
     * @param translationKey localization key for displaying the blueprint name
     */
    public record Definition(
        ResourceLocation id,
        Supplier<MultiblockPattern> pattern,
        Supplier<? extends Block> controller,
        String translationKey
    ) {
        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(controller, "controller");
            Objects.requireNonNull(translationKey, "translationKey");
        }

        /** Returns the localized name component for this blueprint. */
        public Component name() {
            return Component.translatable(translationKey);
        }

        /**
         * Checks whether the given controller block entity matches this blueprint definition.
         *
         * <p>Both checks matter when a future controller can switch between more than one pattern.</p>
         */
        public boolean matches(MultiblockControllerEntity candidate) {
            return candidate.getBlockState().is(controller.get())
                && candidate.structurePattern() == pattern.get();
        }
    }
}
