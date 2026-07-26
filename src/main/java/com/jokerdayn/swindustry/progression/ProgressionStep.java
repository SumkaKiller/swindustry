package com.jokerdayn.swindustry.progression;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.registry.ModItems;
import com.jokerdayn.swindustry.registry.ModTags;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The opening of the game, as an ordered list of one-line objectives.
 *
 * <p>This is the thing in the corner of the screen telling a shipwrecked engineer what to do next.
 * It replaces a quest book on purpose: one line, always the current one, never a menu to open.</p>
 *
 * <p>Steps only ever advance, and only from the one directly before them, so the sequence a player
 * sees is the sequence written here. Picking up a bronze ingot from a chest does not skip anyone
 * past the fire — they still have to make fire, they just will not have to look for the ingot
 * afterwards.</p>
 *
 * <p>Each step decides for itself when it is done. Most read the player's inventory; the two that
 * cannot — lighting a fire, assembling a kiln — read a {@link ProgressionMilestone} recorded by the
 * code that made it happen.</p>
 */
public enum ProgressionStep {

    /** Nothing has happened yet. Everything downstream needs wood. */
    CHOP_WOOD("chop_wood", (player, state) -> carries(player, stack -> stack.is(ItemTags.LOGS))),

    /** Wood plus sticks makes a heap worth striking a spark over. */
    BUILD_CAMPFIRE("build_campfire", (player, state) ->
        carries(player, stack -> stack.is(ModItems.PRIMITIVE_CAMPFIRE.get()))),

    /** No flint and steel exists yet. Two stones and patience are the whole toolkit. */
    GATHER_STRIKERS("gather_strikers", (player, state) ->
        carries(player, stack -> stack.is(ModTags.Items.FIRE_STRIKER_FLINT))
            && carries(player, stack -> stack.is(ModTags.Items.FIRE_STRIKER_STONE))),

    /** The first fire. Most strikes fail; that is the point. */
    LIGHT_FIRE("light_fire", (player, state) -> state.has(ProgressionMilestone.LIT_A_FIRE)),

    /** Wait it out, then break the embers open. */
    RAKE_CHARCOAL("rake_charcoal", (player, state) -> carries(player, stack -> stack.is(Items.CHARCOAL))),

    /** The first machine, and the first proof that a factory is not one block. */
    BUILD_KILN("build_kiln", (player, state) -> state.has(ProgressionMilestone.FORMED_A_KILN)),

    /** Bronze hides in the island boulders and only the kiln will free it. */
    SMELT_BRONZE("smelt_bronze", (player, state) ->
        carries(player, stack -> stack.is(ModTags.Items.BRONZE_INGOTS))),

    /** Tools worth the name. End of the opening. */
    FORGE_BRONZE("forge_bronze", (player, state) -> carries(player, ProgressionStep::isBronzeGear)),

    /** Nothing left to prompt. The HUD goes quiet. */
    COMPLETE("complete", (player, state) -> false);

    private final String key;
    private final Goal goal;

    ProgressionStep(String key, Goal goal) {
        this.key = key;
        this.goal = goal;
    }

    /** The line shown in the corner of the screen. */
    public Component title() {
        return Component.translatable("progression." + SWIndustry.MODID + "." + key);
    }

    /** The smaller line under it, explaining how. */
    public Component hint() {
        return Component.translatable("progression." + SWIndustry.MODID + "." + key + ".hint");
    }

    public String key() {
        return key;
    }

    public boolean isSatisfied(Player player, ProgressionState state) {
        return goal.satisfied(player, state);
    }

    /** The step after this one, or {@link #COMPLETE} at the end. */
    public ProgressionStep next() {
        ProgressionStep[] all = values();
        return this == COMPLETE ? COMPLETE : all[ordinal() + 1];
    }

    public boolean isComplete() {
        return this == COMPLETE;
    }

    /** Reads a step back off disk, falling back to the start if the name is unknown. */
    public static ProgressionStep byKey(String key) {
        for (ProgressionStep step : values()) {
            if (step.key.equals(key)) {
                return step;
            }
        }
        return CHOP_WOOD;
    }

    public static ProgressionStep byOrdinal(int ordinal) {
        ProgressionStep[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CHOP_WOOD;
    }

    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Goal {
        boolean satisfied(Player player, ProgressionState state);
    }

    private static boolean carries(Player player, Predicate<ItemStack> test) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && test.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Any bronze tool or piece of armour.
     *
     * <p>Matched by item id rather than by class so that a bronze item added later — shears, a
     * hammer, a helmet variant — completes the step without this enum needing to know about it.</p>
     */
    private static boolean isBronzeGear(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(SWIndustry.MODID) && id.getPath().startsWith("bronze_");
    }

    /** Kept for callers that want the vanilla tag rather than the item id heuristic. */
    public static boolean carriesTag(Player player, TagKey<Item> tag) {
        return carries(player, stack -> stack.is(tag));
    }
}
