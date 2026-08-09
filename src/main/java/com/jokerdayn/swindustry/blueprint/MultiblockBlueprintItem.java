package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import java.util.List;
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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack blueprint = player.getItemInHand(usedHand);
        if (!PrimitiveEngineerGogglesItem.isHoldingOrWearingGoggles(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.swindustry.blueprint.need_goggles"), true);
            }
            return InteractionResultHolder.fail(blueprint);
        }

        if (!level.isClientSide) {
            Component blueprintName = MultiblockBlueprints.byId(blueprintId)
                .map(MultiblockBlueprints.Definition::name)
                .orElse(Component.literal(blueprintId.toString()));
            player.displayClientMessage(Component.translatable(
                "message.swindustry.blueprint.calibrated", blueprintName), true);
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
