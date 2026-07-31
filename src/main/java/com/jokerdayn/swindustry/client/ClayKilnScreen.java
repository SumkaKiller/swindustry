package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnBlockEntity;
import com.jokerdayn.swindustry.kiln.ClayKilnMenu;
import com.jokerdayn.swindustry.kiln.KilnStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * The kiln's screen.
 *
 * <p>The three furnace slots remain familiar, but the clay-and-brass instrument panel tells the
 * player why the machine is waiting instead of reducing every failure to an unlit arrow.</p>
 */
public class ClayKilnScreen extends AbstractContainerScreen<ClayKilnMenu> {

    private static final ResourceLocation TEXTURE = SWIndustry.id("textures/gui/clay_kiln.png");

    /** Dynamic overlays live to the right of the 202-wide window in the same 256px sheet. */
    private static final int OVERLAY_U = 202;
    private static final int FIRE_V = 0;
    private static final int FIRE_FRAMES = 3;
    private static final int FIRE_FRAME_TICKS = 4;
    private static final int ARROW_V = 16;
    private static final int HEAT_U = 244;
    private static final int HEAT_V = 0;

    private static final int FIRE_X = 66;
    private static final int FIRE_Y = 61;
    private static final int FIRE_WIDTH = 14;
    private static final int FIRE_HEIGHT = 14;

    private static final int ARROW_X = 109;
    private static final int ARROW_Y = 51;
    private static final int ARROW_WIDTH = 38;
    private static final int ARROW_HEIGHT = 15;

    private static final int HEAT_X = 91;
    private static final int HEAT_Y = 39;
    private static final int HEAT_WIDTH = 6;
    private static final int HEAT_HEIGHT = 35;

    private static final int STATUS_X = 13;
    private static final int STATUS_Y = 87;
    private static final int STATUS_SIZE = 6;

    private static final int TIER_X = 160;
    private static final int TIER_Y = 9;
    private static final int TIER_WIDTH = 31;
    private static final int TIER_HEIGHT = 11;

    private static final int INPUT_X = 28;
    private static final int INPUT_Y = 34;
    private static final int FUEL_X = 28;
    private static final int FUEL_Y = 65;
    private static final int OUTPUT_X = 158;
    private static final int OUTPUT_Y = 50;
    private static final int SLOT_FRAME_SIZE = 18;

    public ClayKilnScreen(ClayKilnMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 202;
        this.imageHeight = 190;
        this.titleLabelX = 12;
        this.titleLabelY = 9;
        this.inventoryLabelX = 20;
        this.inventoryLabelY = 100;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        // Fire burns down from the top, the way a furnace's does.
        float fuel = menu.fuelRemaining();
        if (fuel > 0.0F) {
            int lit = Math.max(1, Math.round(fuel * FIRE_HEIGHT));
            long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            int frame = (int) ((gameTime / FIRE_FRAME_TICKS) % FIRE_FRAMES);
            graphics.blit(TEXTURE,
                left + FIRE_X, top + FIRE_Y + FIRE_HEIGHT - lit,
                OVERLAY_U + frame * FIRE_WIDTH, FIRE_V + FIRE_HEIGHT - lit,
                FIRE_WIDTH, lit);

            int heat = Math.max(1, Math.round(fuel * HEAT_HEIGHT));
            graphics.blit(TEXTURE,
                left + HEAT_X, top + HEAT_Y + HEAT_HEIGHT - heat,
                HEAT_U, HEAT_V + HEAT_HEIGHT - heat,
                HEAT_WIDTH, heat);
        }

        int progress = Math.round(menu.cookProgress() * ARROW_WIDTH);
        if (progress > 0) {
            graphics.blit(TEXTURE,
                left + ARROW_X, top + ARROW_Y,
                OVERLAY_U, ARROW_V,
                progress, ARROW_HEIGHT);
        }

        int statusColor = statusColor(menu.status());
        graphics.fill(left + STATUS_X - 1, top + STATUS_Y - 1,
            left + STATUS_X + STATUS_SIZE + 1, top + STATUS_Y + STATUS_SIZE + 1, 0xFF241713);
        graphics.fill(left + STATUS_X, top + STATUS_Y,
            left + STATUS_X + STATUS_SIZE, top + STATUS_Y + STATUS_SIZE, statusColor);
    }

    /** Warm light on dark brick. The inherited dark grey would be invisible on this panel. */
    private static final int TITLE_COLOR = 0xFFF1DFC0;
    private static final int LABEL_COLOR = 0xFF4F3B2D;
    private static final int SECONDARY_COLOR = 0xFFC7AD84;

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);

        Component status = menu.status().label();
        graphics.drawString(font, status, 23, 85, statusColor(menu.status()), false);
        graphics.drawCenteredString(font, Component.translatable(
            "container.swindustry.clay_kiln.tier", menu.tier()),
            TIER_X + TIER_WIDTH / 2, TIER_Y + 1, SECONDARY_COLOR);

        if (menu.cookProgress() > 0.0F) {
            String percentage = menu.cookPercent() + "%";
            graphics.drawCenteredString(font, percentage, ARROW_X + ARROW_WIDTH / 2,
                ARROW_Y + ARROW_HEIGHT + 3, SECONDARY_COLOR);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderInstrumentTooltips(graphics, mouseX, mouseY);
    }

    private void renderInstrumentTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (emptySlotHovered(ClayKilnBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("container.swindustry.clay_kiln.slot.input"), mouseX, mouseY);
        } else if (emptySlotHovered(ClayKilnBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("container.swindustry.clay_kiln.slot.fuel"), mouseX, mouseY);
        } else if (emptySlotHovered(ClayKilnBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("container.swindustry.clay_kiln.slot.output"), mouseX, mouseY);
        } else if (isHovering(62, 34, 39, 44, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                "container.swindustry.clay_kiln.burn_time", formatTicks(menu.fuelTicksRemaining())),
                mouseX, mouseY);
        } else if (isHovering(106, 48, 44, 24, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                "container.swindustry.clay_kiln.progress", menu.cookPercent()), mouseX, mouseY);
        } else if (isHovering(10, 83, 182, 14, mouseX, mouseY)) {
            graphics.renderTooltip(font, statusHint(menu.status()), mouseX, mouseY);
        } else if (isHovering(TIER_X, TIER_Y, TIER_WIDTH, TIER_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                "container.swindustry.clay_kiln.tier_tooltip", menu.tier()), mouseX, mouseY);
        }
    }

    private boolean emptySlotHovered(int slotIndex, int x, int y, int mouseX, int mouseY) {
        return !menu.getSlot(slotIndex).hasItem()
            && isHovering(x - 1, y - 1, SLOT_FRAME_SIZE, SLOT_FRAME_SIZE, mouseX, mouseY);
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, (ticks + 19) / 20);
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static Component statusHint(KilnStatus status) {
        String suffix = switch (status) {
            case INCOMPLETE -> "incomplete";
            case IDLE -> "idle";
            case INVALID_RECIPE -> "invalid_recipe";
            case NEEDS_FUEL -> "needs_fuel";
            case OUTPUT_BLOCKED -> "output_blocked";
            case TIER_TOO_LOW -> "tier_too_low";
            case WORKING -> "working";
        };
        return Component.translatable("container.swindustry.clay_kiln.hint." + suffix);
    }

    private static int statusColor(KilnStatus status) {
        return switch (status) {
            case WORKING -> 0xFF78BE72;
            case IDLE -> 0xFFC6B08A;
            case NEEDS_FUEL -> 0xFFE0A149;
            case INVALID_RECIPE -> 0xFFD98745;
            case OUTPUT_BLOCKED, INCOMPLETE -> 0xFFD85D4A;
            case TIER_TOO_LOW -> 0xFFE0783F;
        };
    }
}
