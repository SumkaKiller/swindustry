package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Client-side in-world renderer that displays true translucent ghost block models (Litematica style),
 * crisp warning boxes for blocked cavities, and clean outlines when wearing engineer goggles.
 */
@EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
public final class MultiblockGhostRenderer {

    private static final float GHOST_ALPHA = 0.45F;
    private static final int HORIZONTAL_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 8;
    private static final int SCAN_INTERVAL_TICKS = 8;

    private static long lastScanTick = -1;
    private static final List<BlockPos> CACHED_CONTROLLER_POSITIONS = new ArrayList<>();

    private MultiblockGhostRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        if (!PrimitiveEngineerGogglesItem.isHoldingOrWearingGoggles(player)) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        List<BlockPos> controllers = getNearbyControllers(level, playerPos);
        if (controllers.isEmpty()) {
            return;
        }

        List<MultiblockPattern.InspectionCell> missingWalls = new ArrayList<>();
        List<MultiblockPattern.InspectionCell> blockedCavities = new ArrayList<>();

        for (BlockPos cPos : controllers) {
            BlockEntity be = level.getBlockEntity(cPos);
            if (be instanceof MultiblockControllerEntity controller && !controller.isFormed()) {
                for (MultiblockPattern.InspectionCell cell : controller.inspectStructure()) {
                    if (!cell.matches()) {
                        if (cell.expected().role() == BlockMatcher.Role.WALL) {
                            missingWalls.add(cell);
                        } else if (cell.expected().role() == BlockMatcher.Role.CAVITY) {
                            blockedCavities.add(cell);
                        }
                    }
                }
            }
        }

        if (missingWalls.isEmpty() && blockedCavities.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        BlockState ghostState = ModBlocks.RAW_CLAY_BRICKS.get().defaultBlockState();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // 1. Render true translucent textured block models using AlphaVertexConsumer (Litematica / Create style)
        MultiBufferSource translucentSource = renderType ->
            new AlphaVertexConsumer(bufferSource.getBuffer(RenderType.translucent()), GHOST_ALPHA);

        for (MultiblockPattern.InspectionCell cell : missingWalls) {
            BlockPos pos = cell.pos();
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            blockRenderer.renderSingleBlock(ghostState, poseStack, translucentSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        // 2. Render red warning boxes for blocked cavities (expanded bounds so solid obstacles are enveloped and clearly visible)
        if (!blockedCavities.isEmpty()) {
            VertexConsumer filledConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
            for (MultiblockPattern.InspectionCell cell : blockedCavities) {
                BlockPos pos = cell.pos();
                renderFilledBox(poseStack, filledConsumer,
                    pos.getX() - 0.005, pos.getY() - 0.005, pos.getZ() - 0.005,
                    pos.getX() + 1.005, pos.getY() + 1.005, pos.getZ() + 1.005,
                    0.95F, 0.15F, 0.15F, 0.40F);
            }
        }

        // 3. Render crisp outlines
        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());
        for (MultiblockPattern.InspectionCell cell : missingWalls) {
            BlockPos pos = cell.pos();
            LevelRenderer.renderLineBox(poseStack, linesConsumer,
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                0.25F, 0.72F, 0.92F, 0.70F);
        }

        for (MultiblockPattern.InspectionCell cell : blockedCavities) {
            BlockPos pos = cell.pos();
            LevelRenderer.renderLineBox(poseStack, linesConsumer,
                pos.getX() - 0.005, pos.getY() - 0.005, pos.getZ() - 0.005,
                pos.getX() + 1.005, pos.getY() + 1.005, pos.getZ() + 1.005,
                0.95F, 0.15F, 0.15F, 0.95F);
        }

        poseStack.popPose();
    }

    /**
     * Highly optimized, continuous scan that runs periodically without skipping any coordinates.
     */
    private static List<BlockPos> getNearbyControllers(ClientLevel level, BlockPos playerPos) {
        long tick = level.getGameTime();
        if (tick - lastScanTick >= SCAN_INTERVAL_TICKS || tick < lastScanTick) {
            lastScanTick = tick;
            CACHED_CONTROLLER_POSITIONS.clear();

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int minX = playerPos.getX() - HORIZONTAL_RADIUS;
            int maxX = playerPos.getX() + HORIZONTAL_RADIUS;
            int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - VERTICAL_RADIUS);
            int maxY = Math.min(level.getMaxBuildHeight(), playerPos.getY() + VERTICAL_RADIUS);
            int minZ = playerPos.getZ() - HORIZONTAL_RADIUS;
            int maxZ = playerPos.getZ() + HORIZONTAL_RADIUS;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        cursor.set(x, y, z);
                        if (level.getBlockEntity(cursor) instanceof MultiblockControllerEntity controller
                            && !controller.isFormed()) {
                            CACHED_CONTROLLER_POSITIONS.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        return CACHED_CONTROLLER_POSITIONS;
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer builder,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();

        float x1 = (float) minX;
        float y1 = (float) minY;
        float z1 = (float) minZ;
        float x2 = (float) maxX;
        float y2 = (float) maxY;
        float z2 = (float) maxZ;

        // Down
        builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);

        // Up
        builder.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);

        // North
        builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);

        // South
        builder.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);

        // West
        builder.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);

        // East
        builder.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
    }
}
