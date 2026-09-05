package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.multiblock.network.KilnStructurePayload;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int HORIZONTAL_RADIUS = 32;
    private static final int VERTICAL_RADIUS = 16;
    private static final int SCAN_INTERVAL_TICKS = 10;

    private static long lastScanTick = -1;
    private static final List<BlockPos> CACHED_CONTROLLER_POSITIONS = new ArrayList<>();
    private static final Map<BlockPos, List<MultiblockPattern.InspectionCell>> LOCAL_INSPECTIONS = new HashMap<>();
    private static final Map<BlockPos, CachedServerInspection> SERVER_INSPECTIONS = new HashMap<>();
    private static ClientLevel cachedLevel;

    private record CachedServerInspection(List<KilnStructurePayload.Cell> cells,
                                          List<MultiblockPattern.InspectionCell> inspection) {}

    private MultiblockGhostRenderer() {}

    @SubscribeEvent
    public static void onClientLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        CACHED_CONTROLLER_POSITIONS.clear();
        LOCAL_INSPECTIONS.clear();
        SERVER_INSPECTIONS.clear();
        cachedLevel = null;
        lastScanTick = -1;
        StructureVerdicts.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
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
        java.util.Set<BlockPos> addedMissing = new java.util.HashSet<>();
        java.util.Set<BlockPos> addedBlocked = new java.util.HashSet<>();

        for (BlockPos cPos : controllers) {
            BlockEntity be = level.getBlockEntity(cPos);
            if (be instanceof MultiblockControllerEntity controller && !controller.isFormed()) {
                // Prefer the server's verdict; fall back to a local pattern walk only when no
                // verdict has arrived yet (freshly placed controller, packet loss, singleplayer
                // before first tick).
                List<MultiblockPattern.InspectionCell> inspection = getInspection(level, cPos, controller);
                for (MultiblockPattern.InspectionCell cell : inspection) {
                    if (!cell.matches()) {
                        BlockPos pos = cell.pos();
                        if (cell.expected().role() == BlockMatcher.Role.WALL) {
                            BlockState currentState = level.getBlockState(pos);
                            if (currentState.isAir() || (currentState.canBeReplaced() && currentState.getFluidState().isEmpty())) {
                                if (addedMissing.add(pos)) {
                                    missingWalls.add(cell);
                                }
                            } else {
                                // A wrong block or fluid is placed where a wall should be -> highlight with red warning box
                                if (addedBlocked.add(pos)) {
                                    blockedCavities.add(cell);
                                }
                            }
                        } else if (cell.expected().role() == BlockMatcher.Role.CAVITY) {
                            if (addedBlocked.add(pos)) {
                                blockedCavities.add(cell);
                            }
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

        // 2. Render red warning boxes for blocked cavities using native chained strip (no diagonal glitch triangles)
        if (!blockedCavities.isEmpty()) {
            VertexConsumer filledConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
            for (MultiblockPattern.InspectionCell cell : blockedCavities) {
                BlockPos pos = cell.pos();
                LevelRenderer.addChainedFilledBoxVertices(poseStack, filledConsumer,
                    pos.getX() - 0.005, pos.getY() - 0.005, pos.getZ() - 0.005,
                    pos.getX() + 1.005, pos.getY() + 1.005, pos.getZ() + 1.005,
                    0.95F, 0.15F, 0.15F, 0.40F);
            }
        }

        // 3. Render crisp outlines with subtle 0.002 bias to prevent z-fighting with adjacent solid terrain
        VertexConsumer linesConsumer = bufferSource.getBuffer(RenderType.lines());
        for (MultiblockPattern.InspectionCell cell : missingWalls) {
            BlockPos pos = cell.pos();
            LevelRenderer.renderLineBox(poseStack, linesConsumer,
                pos.getX() - 0.002, pos.getY() - 0.002, pos.getZ() - 0.002,
                pos.getX() + 1.002, pos.getY() + 1.002, pos.getZ() + 1.002,
                0.25F, 0.72F, 0.92F, 0.45F);
        }

        for (MultiblockPattern.InspectionCell cell : blockedCavities) {
            BlockPos pos = cell.pos();
            LevelRenderer.renderLineBox(poseStack, linesConsumer,
                pos.getX() - 0.005, pos.getY() - 0.005, pos.getZ() - 0.005,
                pos.getX() + 1.005, pos.getY() + 1.005, pos.getZ() + 1.005,
                0.95F, 0.15F, 0.15F, 0.85F);
        }

        poseStack.popPose();

        // Flush render batches so ghost projection never leaks into hand/GUI render passes
        bufferSource.endBatch(RenderType.translucent());
        bufferSource.endBatch(RenderType.debugFilledBox());
        bufferSource.endBatch(RenderType.lines());
    }

    /**
     * Chunk-level block entity search: scans only loaded block entities instead of brute-forcing thousands of air blocks.
     */
    private static List<MultiblockPattern.InspectionCell> getInspection(
        ClientLevel level, BlockPos controllerPos, MultiblockControllerEntity controller) {
        List<KilnStructurePayload.Cell> serverCells = StructureVerdicts.lookup(controllerPos);
        if (serverCells != null) {
            CachedServerInspection cached = SERVER_INSPECTIONS.get(controllerPos);
            if (cached == null || cached.cells() != serverCells) {
                List<MultiblockPattern.InspectionCell> inspection = new ArrayList<>(serverCells.size());
                for (KilnStructurePayload.Cell cell : serverCells) {
                    inspection.add(new MultiblockPattern.InspectionCell(
                        BlockPos.of(cell.packed()), ' ',
                        BlockMatcher.of(BlockMatcher.Role.values()[cell.roleOrdinal()], s -> false),
                        false));
                }
                inspection = List.copyOf(inspection);
                SERVER_INSPECTIONS.put(controllerPos,
                    new CachedServerInspection(serverCells, inspection));
                return inspection;
            }
            return cached.inspection();
        }

        SERVER_INSPECTIONS.remove(controllerPos);
        return LOCAL_INSPECTIONS.computeIfAbsent(controllerPos,
            ignored -> controller.inspectStructure());
    }

    private static List<BlockPos> getNearbyControllers(ClientLevel level, BlockPos playerPos) {
        if (cachedLevel != level) {
            cachedLevel = level;
            lastScanTick = -1;
            CACHED_CONTROLLER_POSITIONS.clear();
            LOCAL_INSPECTIONS.clear();
            SERVER_INSPECTIONS.clear();
        }

        long tick = level.getGameTime();
        if (tick - lastScanTick >= SCAN_INTERVAL_TICKS || tick < lastScanTick) {
            lastScanTick = tick;
            CACHED_CONTROLLER_POSITIONS.clear();
            LOCAL_INSPECTIONS.clear();

            int minChunkX = (playerPos.getX() - HORIZONTAL_RADIUS) >> 4;
            int maxChunkX = (playerPos.getX() + HORIZONTAL_RADIUS) >> 4;
            int minChunkZ = (playerPos.getZ() - HORIZONTAL_RADIUS) >> 4;
            int maxChunkZ = (playerPos.getZ() + HORIZONTAL_RADIUS) >> 4;

            int minX = playerPos.getX() - HORIZONTAL_RADIUS;
            int maxX = playerPos.getX() + HORIZONTAL_RADIUS;
            int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - VERTICAL_RADIUS);
            int maxY = Math.min(level.getMaxBuildHeight(), playerPos.getY() + VERTICAL_RADIUS);
            int minZ = playerPos.getZ() - HORIZONTAL_RADIUS;
            int maxZ = playerPos.getZ() + HORIZONTAL_RADIUS;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (level.hasChunk(cx, cz)) {
                        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (be instanceof MultiblockControllerEntity controller && !controller.isFormed()) {
                                BlockPos pos = be.getBlockPos();
                                if (pos.getX() >= minX && pos.getX() <= maxX
                                    && pos.getY() >= minY && pos.getY() <= maxY
                                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                                    CACHED_CONTROLLER_POSITIONS.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
            SERVER_INSPECTIONS.keySet().removeIf(
                pos -> !CACHED_CONTROLLER_POSITIONS.contains(pos));
        }
        return CACHED_CONTROLLER_POSITIONS;
    }
}
