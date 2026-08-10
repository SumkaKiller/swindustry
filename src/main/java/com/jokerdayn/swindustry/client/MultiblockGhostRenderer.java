package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Client-side in-world renderer that displays translucent ghost outlines and warnings for incomplete
 * multiblock machines when wearing engineer goggles.
 */
@EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
public final class MultiblockGhostRenderer {

    private static final int SEARCH_RADIUS = 16;

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
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    cursor.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be instanceof MultiblockControllerEntity controller && !controller.isFormed()) {
                        renderControllerGhost(controller, poseStack, bufferSource);
                    }
                }
            }
        }

        bufferSource.endBatch(RenderType.lines());
        bufferSource.endBatch(RenderType.debugFilledBox());
        poseStack.popPose();
    }

    private static void renderControllerGhost(MultiblockControllerEntity controller,
                                              PoseStack poseStack, MultiBufferSource bufferSource) {
        List<MultiblockPattern.InspectionCell> cells = controller.inspectStructure();
        if (cells.isEmpty()) {
            return;
        }

        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());

        for (MultiblockPattern.InspectionCell cell : cells) {
            if (cell.matches()) {
                continue;
            }

            BlockPos pos = cell.pos();
            double minX = pos.getX() + 0.01;
            double minY = pos.getY() + 0.01;
            double minZ = pos.getZ() + 0.01;
            double maxX = pos.getX() + 0.99;
            double maxY = pos.getY() + 0.99;
            double maxZ = pos.getZ() + 0.99;

            if (cell.expected().role() == BlockMatcher.Role.WALL) {
                LevelRenderer.renderLineBox(poseStack, linesConsumer, minX, minY, minZ, maxX, maxY, maxZ,
                    0.25F, 0.72F, 0.92F, 0.85F);
                renderGhostBox(poseStack, bufferSource, minX, minY, minZ, maxX, maxY, maxZ,
                    0.25F, 0.72F, 0.92F, 0.22F);
            } else if (cell.expected().role() == BlockMatcher.Role.CAVITY) {
                LevelRenderer.renderLineBox(poseStack, linesConsumer, minX, minY, minZ, maxX, maxY, maxZ,
                    0.95F, 0.25F, 0.15F, 0.90F);
                renderGhostBox(poseStack, bufferSource, minX, minY, minZ, maxX, maxY, maxZ,
                    0.95F, 0.25F, 0.15F, 0.35F);
            }
        }
    }

    private static void renderGhostBox(PoseStack poseStack, MultiBufferSource bufferSource,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ,
                                       float r, float g, float b, float a) {
        VertexConsumer builder = bufferSource.getBuffer(RenderType.debugFilledBox());
        Matrix4f mat = poseStack.last().pose();

        float x1 = (float) minX;
        float y1 = (float) minY;
        float z1 = (float) minZ;
        float x2 = (float) maxX;
        float y2 = (float) maxY;
        float z2 = (float) maxZ;

        // Down face
        builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);

        // Up face
        builder.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);

        // North face
        builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);

        // South face
        builder.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);

        // West face
        builder.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);

        // East face
        builder.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
    }
}
