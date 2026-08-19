package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A reusable sheet for a machine blueprint. */
public final class MultiblockBlueprintItem extends Item {

    private final ResourceLocation blueprintId;

    public MultiblockBlueprintItem(ResourceLocation blueprintId, Properties properties) {
        super(properties);
        this.blueprintId = blueprintId;
    }

    /** Returns the unique identifier of the multiblock blueprint represented by this item. */
    public ResourceLocation blueprintId() {
        return blueprintId;
    }

    /**
     * Clips the plan into a pair of goggles: worn first, else whichever hand holds a pair. The
     * sheet itself is never consumed, and the installed plan travels on the goggles' data
     * component, so tooltips, inspection and the crafting-grid combine all read one truth.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack blueprint = player.getItemInHand(usedHand);

        ItemStack target = PrimitiveEngineerGogglesItem.wornOrHeldGoggles(player);
        if (target.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.swindustry.blueprint.need_goggles"), true);
            }
            return InteractionResultHolder.fail(blueprint);
        }

        if (!level.isClientSide) {
            Optional<MultiblockBlueprints.Definition> definition = MultiblockBlueprints.byId(blueprintId);
            if (definition.isEmpty()) {
                player.displayClientMessage(
                    Component.translatable("message.swindustry.goggles.unknown_blueprint"), true);
                return InteractionResultHolder.fail(blueprint);
            }
            target.set(com.jokerdayn.swindustry.registry.ModDataComponents.SELECTED_BLUEPRINT.get(),
                definition.get().id());
            player.displayClientMessage(Component.translatable(
                "item.swindustry.primitive_engineer_goggles.loaded", definition.get().name()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                SoundSource.PLAYERS, 0.75F, 1.15F);
        }
        return InteractionResultHolder.sidedSuccess(blueprint, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.swindustry.multiblock_blueprint.reusable")
            .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.swindustry.multiblock_blueprint.calibration_hint")
            .withStyle(ChatFormatting.AQUA));
    }
}
