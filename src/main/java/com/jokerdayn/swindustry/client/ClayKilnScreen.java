package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnBlockEntity;
import com.jokerdayn.swindustry.kiln.ClayKilnMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Standard 176x166 kiln screen featuring a vanilla furnace layout enhanced with a 4-stage color heat gauge.
 */
public class ClayKilnScreen extends AbstractContainerScreen<ClayKilnMenu> {

    private static final ResourceLocation TEXTURE = SWIndustry.id("textures/gui/clay_kiln.png");

    private static final int FIRE_X = 43;
    private static final int FIRE_Y = 37;
    private static final int FIRE_WIDTH = 14;
    private static final int FIRE_HEIGHT = 14;
    private static final int FIRE_U = 176;
    private static final int FIRE_V = 0;

    private static final int ARROW_X = 66;
    private static final int ARROW_Y = 35;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_U = 176;
    private static final int ARROW_V = 14;

    private static final int GAUGE_X = 151;
    private static final int GAUGE_Y = 17;
    private static final int GAUGE_WIDTH = 13;
    private static final int GAUGE_HEIGHT = 52;
    private static final int GAUGE_U = 201;
    private static final int GAUGE_V = 0;

    private static final int INPUT_X = 42;
    private static final int INPUT_Y = 17;
    private static final int FUEL_X = 42;
    private static final int FUEL_Y = 53;
    private static final int OUTPUT_X = 102;
    private static final int OUTPUT_Y = 35;
    private static final int SLOT_FRAME_SIZE = 18;

    private static final int LABEL_COLOR = 0x404040;

    public ClayKilnScreen(ClayKilnMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        // Vanilla furnace centers its title horizontally across the top
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);

        // 1. Fire burning from bottom up
        if (menu.isLit()) {
            int lit = Math.max(1, Math.round(menu.fuelRemaining() * FIRE_HEIGHT));
            graphics.blit(TEXTURE,
                left + FIRE_X, top + FIRE_Y + FIRE_HEIGHT - lit,
                FIRE_U, FIRE_V + FIRE_HEIGHT - lit,
                FIRE_WIDTH, lit);
        }

        // 2. Cooking progress arrow filling from left to right
        int progress = Math.round(menu.cookProgress() * ARROW_WIDTH);
        if (progress > 0) {
            graphics.blit(TEXTURE,
                left + ARROW_X, top + ARROW_Y,
                ARROW_U, ARROW_V,
                progress, ARROW_HEIGHT);
        }

        // 3. Multi-tier heat gauge filling from bottom up
        int heatHeight = Math.round(menu.heatProgress() * GAUGE_HEIGHT);
        if (heatHeight > 0) {
            graphics.blit(TEXTURE,
                left + GAUGE_X, top + GAUGE_Y + GAUGE_HEIGHT - heatHeight,
                GAUGE_U, GAUGE_V + GAUGE_HEIGHT - heatHeight,
                GAUGE_WIDTH, heatHeight);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderInstrumentTooltips(graphics, mouseX, mouseY);
    }

    private void renderInstrumentTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(GAUGE_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            List<Component> tooltips = new ArrayList<>();
            tooltips.add(Component.translatable("container.swindustry.clay_kiln.heat_title", menu.heatState().label()));
            tooltips.add(Component.translatable("container.swindustry.clay_kiln.heat", menu.heatPercent()));
            graphics.renderComponentTooltip(font, tooltips, mouseX, mouseY);
        } else if (emptySlotHovered(ClayKilnBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("container.swindustry.clay_kiln.slot.input"), mouseX, mouseY);
        } else if (emptySlotHovered(ClayKilnBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("container.swindustry.clay_kiln.slot.fuel"), mouseX, mouseY);
        } else if (emptySlotHovered(ClayKilnBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("container.swindustry.clay_kiln.slot.output"), mouseX, mouseY);
        } else if (isHovering(FIRE_X, FIRE_Y, FIRE_WIDTH, FIRE_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                "container.swindustry.clay_kiln.burn_time", formatTicks(menu.fuelTicksRemaining())),
                mouseX, mouseY);
        } else if (isHovering(ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, mouseX, mouseY)) {
            List<Component> tooltips = new ArrayList<>();
            tooltips.add(Component.translatable("container.swindustry.clay_kiln.progress", menu.cookPercent()));
            tooltips.add(menu.status().label());
            graphics.renderComponentTooltip(font, tooltips, mouseX, mouseY);
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
}

