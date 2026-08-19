package com.jokerdayn.swindustry.item;

import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModArmorMaterials;
import com.jokerdayn.swindustry.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** Wearable early-game sighting frame that visualizes incomplete multiblock structures. */
public final class PrimitiveEngineerGogglesItem extends ArmorItem {

    public PrimitiveEngineerGogglesItem(Properties properties) {
        super(ModArmorMaterials.engineerGoggles(), Type.HELMET, properties);
    }

    /** The first pair of goggles on the player: worn on head, then main hand, then off hand. */
    public static ItemStack wornOrHeldGoggles(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())) {
            return head;
        }
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /** Checks if the player has engineer goggles equipped on head. */
    public static boolean isWearingGoggles(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get());
    }

    /** Checks if the player has engineer goggles equipped on head or held in either hand. */
    public static boolean isHoldingOrWearingGoggles(Player player) {
        return isWearingGoggles(player)
            || player.getMainHandItem().is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())
            || player.getOffhandItem().is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
            instanceof MultiblockControllerEntity controller)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS : inspect(player, context.getItemInHand(), controller);
    }

    /** Lets a controller support sneak-use while the goggles remain equipped on the player's head. */
    public static boolean inspectWorn(Player player, MultiblockControllerEntity controller) {
        ItemStack goggles = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!goggles.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get())) {
            return false;
        }
        inspect(player, goggles, controller);
        return true;
    }

    private static InteractionResult inspect(Player player, ItemStack goggles,
                                             MultiblockControllerEntity controller) {
        Direction facing = controller.structureFacing();
        if (facing == null || facing.getAxis().isVertical()
            || !(player instanceof ServerPlayer serverPlayer)) {
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.cannot_inspect"), true);
            return InteractionResult.CONSUME;
        }

        controller.revalidate();

        // A calibrated visor is bound to one machine: refuse politely when used elsewhere.
        // Without a plan the frame stays universal, exactly as before calibration existed.
        net.minecraft.resources.ResourceLocation installed =
            goggles.get(com.jokerdayn.swindustry.registry.ModDataComponents.SELECTED_BLUEPRINT.get());
        if (installed != null) {
            var definition = com.jokerdayn.swindustry.blueprint.MultiblockBlueprints.byId(installed);
            if (definition.isEmpty()) {
                player.displayClientMessage(
                    Component.translatable("message.swindustry.goggles.unknown_blueprint"), true);
                return InteractionResult.CONSUME;
            }
            if (!definition.get().matches(controller)) {
                player.displayClientMessage(Component.translatable(
                    "message.swindustry.goggles.wrong_controller", definition.get().name()), true);
                return InteractionResult.CONSUME;
            }
        }

        List<MultiblockPattern.InspectionCell> inspection = controller.inspectStructure();
        if (inspection.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.cannot_inspect"), true);
            return InteractionResult.CONSUME;
        }

        int wallCells = 0;
        int correctWalls = 0;
        int blockedCavities = 0;
        for (MultiblockPattern.InspectionCell cell : inspection) {
            if (cell.expected().role() == BlockMatcher.Role.WALL) {
                wallCells++;
                if (cell.matches()) {
                    correctWalls++;
                }
            } else if (cell.expected().role() == BlockMatcher.Role.CAVITY && !cell.matches()) {
                blockedCavities++;
            }
        }

        if (correctWalls == wallCells && blockedCavities == 0) {
            serverPlayer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.65F, 1.35F);
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.structure_complete"), true);
        } else {
            serverPlayer.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.50F, 0.90F);
            player.displayClientMessage(Component.translatable(
                "message.swindustry.goggles.structure_incomplete",
                correctWalls, wallCells, blockedCavities), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
            "item.swindustry.primitive_engineer_goggles.early_frame")
            .withStyle(ChatFormatting.GRAY));
        net.minecraft.resources.ResourceLocation installed =
            stack.get(com.jokerdayn.swindustry.registry.ModDataComponents.SELECTED_BLUEPRINT.get());
        if (installed != null) {
            Component planName = com.jokerdayn.swindustry.blueprint.MultiblockBlueprints.byId(installed)
                .map(com.jokerdayn.swindustry.blueprint.MultiblockBlueprints.Definition::name)
                .orElse(Component.translatable("message.swindustry.goggles.unknown_blueprint"));
            tooltipComponents.add(Component.translatable(
                "item.swindustry.primitive_engineer_goggles.loaded", planName)
                .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipComponents.add(Component.translatable(
                "item.swindustry.primitive_engineer_goggles.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipComponents.add(Component.translatable(
            "item.swindustry.primitive_engineer_goggles.use_hint")
            .withStyle(ChatFormatting.AQUA));
    }
}
