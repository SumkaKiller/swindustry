package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import com.jokerdayn.swindustry.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A reusable sheet produced at the drafting table for one particular machine. */
public final class MultiblockBlueprintItem extends Item {

    private final ResourceLocation blueprintId;

    public MultiblockBlueprintItem(ResourceLocation blueprintId, Properties properties) {
        super(properties);
        this.blueprintId = blueprintId;
    }

    public ResourceLocation blueprintId() {
        return blueprintId;
    }

    /** Clips the held sheet into worn goggles (or a pair held in the other hand) without consuming it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack blueprint = player.getItemInHand(usedHand);
        ItemStack goggles = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!goggles.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())) {
            InteractionHand otherHand = usedHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
            goggles = player.getItemInHand(otherHand);
        }

        if (!goggles.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.swindustry.blueprint.need_goggles"), true);
            }
            return InteractionResultHolder.fail(blueprint);
        }
        if (MultiblockBlueprints.byId(blueprintId).isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.swindustry.goggles.unknown_blueprint"), true);
            }
            return InteractionResultHolder.fail(blueprint);
        }

        if (!level.isClientSide) {
            PrimitiveEngineerGogglesItem.selectBlueprint(goggles, blueprintId);
            Component blueprintName = MultiblockBlueprints.byId(blueprintId)
                .orElseThrow()
                .name();
            player.displayClientMessage(Component.translatable(
                "message.swindustry.blueprint.calibrated", blueprintName), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                SoundSource.PLAYERS, 0.75F, 1.15F);
            player.awardStat(Stats.ITEM_USED.get(this));
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
        tooltipComponents.add(Component.translatable("item.swindustry.multiblock_blueprint.crafting_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
