package com.jokerdayn.swindustry.item;

import com.jokerdayn.swindustry.blueprint.MultiblockBlueprints;
import com.jokerdayn.swindustry.blueprint.MultiblockProjectionManager;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModArmorMaterials;
import com.jokerdayn.swindustry.registry.ModDataComponents;
import com.jokerdayn.swindustry.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

/** Wearable early-game goggles that project the reusable blueprint clipped into them. */
public final class PrimitiveEngineerGogglesItem extends ArmorItem {

    public PrimitiveEngineerGogglesItem(Properties properties) {
        super(ModArmorMaterials.engineerGoggles(), Type.HELMET, properties);
    }

    public static void selectBlueprint(ItemStack goggles, ResourceLocation blueprintId) {
        goggles.set(ModDataComponents.SELECTED_BLUEPRINT.get(), blueprintId);
    }

    public static Optional<ResourceLocation> selectedBlueprint(ItemStack goggles) {
        return Optional.ofNullable(goggles.get(ModDataComponents.SELECTED_BLUEPRINT.get()));
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
        Optional<ResourceLocation> selected = selectedBlueprint(goggles);
        if (selected.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.no_blueprint"), true);
            return InteractionResult.CONSUME;
        }

        Optional<MultiblockBlueprints.Definition> definition = MultiblockBlueprints.byId(selected.get());
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

        Direction facing = controller.structureFacing();
        if (facing == null || facing.getAxis().isVertical()
            || !(player instanceof ServerPlayer serverPlayer)) {
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.cannot_inspect"), true);
            return InteractionResult.CONSUME;
        }

        controller.revalidate();
        List<MultiblockPattern.InspectionCell> inspection = controller.inspectStructure();
        if (inspection.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.cannot_inspect"), true);
            return InteractionResult.CONSUME;
        }
        MultiblockProjectionManager.show(serverPlayer, controller.getBlockPos(), inspection);

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

        serverPlayer.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.55F, 1.35F);
        if (correctWalls == wallCells && blockedCavities == 0) {
            player.displayClientMessage(
                Component.translatable("message.swindustry.goggles.structure_complete"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                "message.swindustry.goggles.structure_incomplete",
                correctWalls, wallCells, blockedCavities), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Optional<Component> blueprintName = selectedBlueprint(stack)
            .flatMap(MultiblockBlueprints::byId)
            .map(MultiblockBlueprints.Definition::name);
        if (blueprintName.isPresent()) {
            tooltipComponents.add(Component.translatable(
                "item.swindustry.primitive_engineer_goggles.loaded", blueprintName.get())
                .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipComponents.add(Component.translatable(
                "item.swindustry.primitive_engineer_goggles.empty")
                .withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.translatable(
            "item.swindustry.primitive_engineer_goggles.early_frame")
            .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
            "item.swindustry.primitive_engineer_goggles.use_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable(
            "item.swindustry.primitive_engineer_goggles.legend")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
