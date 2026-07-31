package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/** Short-lived, player-local particle blueprints emitted one machine layer at a time. */
@EventBusSubscriber(modid = SWIndustry.MODID)
public final class MultiblockProjectionManager {

    private static final int TICKS_PER_LAYER = 5;
    private static final int PAUSE_TICKS = 8;
    private static final int PASSES = 3;
    private static final double MAX_DISTANCE_SQUARED = 32.0 * 32.0;

    private static final DustParticleOptions CORRECT =
        new DustParticleOptions(new Vector3f(0.24F, 0.74F, 0.42F), 0.48F);
    private static final DustParticleOptions MISSING =
        new DustParticleOptions(new Vector3f(0.25F, 0.72F, 0.92F), 0.70F);
    private static final DustParticleOptions BLOCKED =
        new DustParticleOptions(new Vector3f(0.95F, 0.27F, 0.14F), 0.74F);

    private static final double[][] CORNERS = {
        {0.10, 0.10, 0.10}, {0.90, 0.10, 0.10},
        {0.10, 0.90, 0.10}, {0.90, 0.90, 0.10},
        {0.10, 0.10, 0.90}, {0.90, 0.10, 0.90},
        {0.10, 0.90, 0.90}, {0.90, 0.90, 0.90}
    };
    private static final double[][] FACE_CENTERS = {
        {0.50, 0.50, 0.08}, {0.50, 0.50, 0.92},
        {0.08, 0.50, 0.50}, {0.92, 0.50, 0.50},
        {0.50, 0.08, 0.50}, {0.50, 0.92, 0.50}
    };

    /** One active projection per player prevents repeated clicks from multiplying particle work. */
    private static final Map<UUID, Projection> ACTIVE = new HashMap<>();

    private MultiblockProjectionManager() {}

    public static void show(ServerPlayer player, BlockPos controllerPos,
                            List<MultiblockPattern.InspectionCell> inspection) {
        UUID playerId = player.getUUID();
        List<Cell> cells = cellsFor(inspection);
        if (cells.isEmpty()) {
            ACTIVE.remove(playerId);
            return;
        }
        ACTIVE.put(playerId, new Projection(playerId, player.level().dimension(),
            controllerPos.immutable(), cells));
    }

    private static List<Cell> cellsFor(List<MultiblockPattern.InspectionCell> inspection) {
        return inspection.stream()
            .filter(cell -> cell.expected().role() == BlockMatcher.Role.WALL || !cell.matches())
            .map(cell -> new Cell(cell.pos().immutable(), classify(cell)))
            .toList();
    }

    private static State classify(MultiblockPattern.InspectionCell cell) {
        if (cell.expected().role() == BlockMatcher.Role.CAVITY) {
            return State.BLOCKED_CAVITY;
        }
        return cell.matches() ? State.CORRECT_WALL : State.MISSING_WALL;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ACTIVE.values().removeIf(projection -> projection.tick(server));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private enum State {
        CORRECT_WALL,
        MISSING_WALL,
        BLOCKED_CAVITY
    }

    private record Cell(BlockPos pos, State state) {}

    private static final class Projection {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final BlockPos controllerPos;
        private List<Cell> cells;
        private int minY;
        private int layerCount;
        private double centerX;
        private double centerZ;
        private double spreadX;
        private double spreadZ;
        private int age;

        private Projection(UUID playerId, ResourceKey<Level> dimension, BlockPos controllerPos,
                           List<Cell> cells) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.controllerPos = controllerPos;
            updateCells(cells);
        }

        /** @return true when this projection should be discarded. */
        private boolean tick(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.getLevel(dimension);
            if (player == null || level == null || player.serverLevel() != level
                || player.distanceToSqr(controllerPos.getX() + 0.5,
                    controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) > MAX_DISTANCE_SQUARED
                || !isLoaded(level, controllerPos)
                || !(level.getBlockEntity(controllerPos) instanceof MultiblockControllerEntity controller)) {
                return true;
            }

            int sweepTicks = layerCount * TICKS_PER_LAYER + PAUSE_TICKS;
            int cycleTick = age % sweepTicks;
            if (age > 0 && cycleTick == 0 && !refresh(controller)) {
                return true;
            }
            // Refreshing cannot change the pattern's footprint in normal play, but recomputing keeps
            // this correct for datapack-driven/future controllers too.
            sweepTicks = layerCount * TICKS_PER_LAYER + PAUSE_TICKS;
            cycleTick = age % sweepTicks;
            if (cycleTick < layerCount * TICKS_PER_LAYER
                && cycleTick % TICKS_PER_LAYER == 0) {
                emitLayer(level, player, minY + cycleTick / TICKS_PER_LAYER);
            }
            age++;
            return age >= sweepTicks * PASSES;
        }

        private boolean refresh(MultiblockControllerEntity controller) {
            List<Cell> refreshed = cellsFor(controller.inspectStructure());
            if (refreshed.isEmpty()) {
                return false;
            }
            updateCells(refreshed);
            return true;
        }

        private void updateCells(List<Cell> updated) {
            cells = List.copyOf(updated);
            minY = cells.stream().mapToInt(cell -> cell.pos().getY()).min()
                .orElse(controllerPos.getY());
            int maxY = cells.stream().mapToInt(cell -> cell.pos().getY()).max()
                .orElse(controllerPos.getY());
            int minX = cells.stream().mapToInt(cell -> cell.pos().getX()).min()
                .orElse(controllerPos.getX());
            int maxX = cells.stream().mapToInt(cell -> cell.pos().getX()).max()
                .orElse(controllerPos.getX());
            int minZ = cells.stream().mapToInt(cell -> cell.pos().getZ()).min()
                .orElse(controllerPos.getZ());
            int maxZ = cells.stream().mapToInt(cell -> cell.pos().getZ()).max()
                .orElse(controllerPos.getZ());
            layerCount = maxY - minY + 1;
            centerX = (minX + maxX + 1.0) * 0.5;
            centerZ = (minZ + maxZ + 1.0) * 0.5;
            spreadX = Math.max(0.25, (maxX - minX + 1) * 0.38);
            spreadZ = Math.max(0.25, (maxZ - minZ + 1) * 0.38);
        }

        private void emitLayer(ServerLevel level, ServerPlayer player, int layerY) {
            for (Cell cell : cells) {
                if (cell.pos().getY() != layerY || !isLoaded(level, cell.pos())) {
                    continue;
                }
                switch (cell.state()) {
                    case CORRECT_WALL -> emitPoints(level, player, cell.pos(), CORRECT, FACE_CENTERS);
                    case MISSING_WALL -> {
                        emitPoints(level, player, cell.pos(), MISSING, CORNERS);
                        emitParticle(level, player, MISSING,
                            cell.pos().getX() + 0.5, cell.pos().getY() + 0.5, cell.pos().getZ() + 0.5);
                    }
                    case BLOCKED_CAVITY -> {
                        emitPoints(level, player, cell.pos(), BLOCKED, CORNERS);
                        level.sendParticles(player, ParticleTypes.SMOKE, false,
                            cell.pos().getX() + 0.5, cell.pos().getY() + 0.55, cell.pos().getZ() + 0.5,
                            2, 0.16, 0.16, 0.16, 0.005);
                    }
                }
            }
            // A faint moving wash makes the layer order legible without turning the projection neon.
            level.sendParticles(player, ParticleTypes.ENCHANT, false,
                centerX, layerY + 0.12, centerZ, 5, spreadX, 0.02, spreadZ, 0.0);
        }

        private static void emitPoints(ServerLevel level, ServerPlayer player, BlockPos pos,
                                       ParticleOptions particle, double[][] points) {
            for (double[] point : points) {
                emitParticle(level, player, particle,
                    pos.getX() + point[0], pos.getY() + point[1], pos.getZ() + point[2]);
            }
        }

        private static void emitParticle(ServerLevel level, ServerPlayer player,
                                         ParticleOptions particle, double x, double y, double z) {
            level.sendParticles(player, particle, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        @SuppressWarnings("deprecation")
        private static boolean isLoaded(ServerLevel level, BlockPos pos) {
            // Checking rather than reading keeps a projection from loading chunks just to draw itself.
            return level.hasChunkAt(pos);
        }
    }
}
