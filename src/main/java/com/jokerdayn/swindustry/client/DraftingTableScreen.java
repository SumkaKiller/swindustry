package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.blueprint.DraftingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** A compact vanilla-style drawing board, rendered in code so every slot stays pixel-perfect. */
public final class DraftingTableScreen extends AbstractContainerScreen<DraftingTableMenu> {

    private static final int WOOD_DARK = 0xFF2D2119;
    private static final int WOOD = 0xFF5B3B24;
    private static final int WOOD_LIGHT = 0xFF805534;
    private static final int PARCHMENT_DARK = 0xFF9B825A;
    private static final int PARCHMENT = 0xFFD5BD86;
    private static final int PARCHMENT_LIGHT = 0xFFE7D39E;
    private static final int SLOT_SHADOW = 0xFF5B4A37;
    private static final int SLOT_FACE = 0xFFC7B486;
    private static final int SLOT_LIGHT = 0xFFF0DEAA;
    private static final int BLUEPRINT = 0xFF4B91A7;
    private static final int TITLE_COLOR = 0xFFF0DCA7;
    private static final int LABEL_COLOR = 0xFF3A2B20;

    public DraftingTableScreen(DraftingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        // Dark spruce frame with deliberately blocky one-pixel highlights.
        graphics.fill(left, top, left + imageWidth, top + imageHeight, WOOD_DARK);
        graphics.fill(left + 2, top + 2, left + imageWidth - 2, top + imageHeight - 2, WOOD);
        graphics.fill(left + 3, top + 3, left + imageWidth - 3, top + 4, WOOD_LIGHT);
        graphics.fill(left + 4, top + 17, left + imageWidth - 4, top + 77, PARCHMENT_DARK);
        graphics.fill(left + 5, top + 18, left + imageWidth - 5, top + 76, PARCHMENT);
        graphics.fill(left + 6, top + 19, left + imageWidth - 6, top + 20, PARCHMENT_LIGHT);

        // A restrained cyan drafting grid: visible enough to sell the table, quiet behind items.
        for (int x = 10; x < imageWidth - 8; x += 12) {
            graphics.fill(left + x, top + 21, left + x + 1, top + 74, 0x224B91A7);
        }
        for (int y = 25; y < 75; y += 12) {
            graphics.fill(left + 6, top + y, left + imageWidth - 6, top + y + 1, 0x224B91A7);
        }

        // Inventory tray.
        graphics.fill(left + 4, top + 79, left + imageWidth - 4, top + imageHeight - 4, PARCHMENT_DARK);
        graphics.fill(left + 5, top + 80, left + imageWidth - 5, top + imageHeight - 5, PARCHMENT);

        for (Slot slot : menu.slots) {
            boolean output = slot.index == DraftingTableMenu.SLOT_RESULT;
            drawSlot(graphics, left + slot.x - 1, top + slot.y - 1, output);
        }

        drawPlus(graphics, left + 38, top + 37);
        drawPlus(graphics, left + 70, top + 37);
        drawArrow(graphics, left + 111, top + 37);

        drawEmptyMachineGlyphs(graphics, left, top);
    }

    private void drawEmptyMachineGlyphs(GuiGraphics graphics, int left, int top) {
        if (!menu.getSlot(DraftingTableMenu.SLOT_PAPER).hasItem()) {
            // Folded paper.
            graphics.fill(left + 23, top + 34, left + 31, top + 43, 0x99F2E5BD);
            graphics.fill(left + 29, top + 34, left + 31, top + 36, 0x99BDAA7F);
            graphics.fill(left + 24, top + 38, left + 29, top + 39, 0x996E8C8E);
        }
        if (!menu.getSlot(DraftingTableMenu.SLOT_INK).hasItem()) {
            // Soot-ink pot.
            graphics.fill(left + 51, top + 35, left + 57, top + 37, 0xAA332B28);
            graphics.fill(left + 49, top + 37, left + 59, top + 43, 0xAA211D1B);
            graphics.fill(left + 51, top + 38, left + 57, top + 39, 0xAA6E5A45);
        }
        if (!menu.getSlot(DraftingTableMenu.SLOT_REFERENCE).hasItem()) {
            // Reusable controller sample.
            graphics.fill(left + 87, top + 34, left + 95, top + 43, 0x995C4633);
            graphics.fill(left + 89, top + 36, left + 93, top + 41, 0x99A87548);
            graphics.fill(left + 90, top + 37, left + 92, top + 40, 0x996BADC0);
        }
        if (!menu.getSlot(DraftingTableMenu.SLOT_RESULT).hasItem()) {
            // Rolled blueprint, picked out in the only bright accent used by the screen.
            graphics.fill(left + 146, top + 34, left + 155, top + 43, 0x995BA2B5);
            graphics.fill(left + 148, top + 36, left + 153, top + 37, 0x99C9E2D7);
            graphics.fill(left + 148, top + 39, left + 152, top + 40, 0x99C9E2D7);
            graphics.fill(left + 145, top + 34, left + 147, top + 43, 0x99E1C58C);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, boolean output) {
        int shadow = output ? 0xFF315D69 : SLOT_SHADOW;
        int face = output ? 0xFF6DA6AF : SLOT_FACE;
        int light = output ? 0xFFB8D8D2 : SLOT_LIGHT;
        graphics.fill(x, y, x + 18, y + 18, shadow);
        graphics.fill(x + 1, y + 1, x + 18, y + 2, light);
        graphics.fill(x + 1, y + 1, x + 2, y + 18, light);
        graphics.fill(x + 2, y + 2, x + 17, y + 17, face);
        if (output) {
            graphics.fill(x + 17, y + 3, x + 18, y + 16, BLUEPRINT);
        }
    }

    private static void drawPlus(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y - 2, x + 2, y + 4, PARCHMENT_DARK);
        graphics.fill(x - 2, y, x + 4, y + 2, PARCHMENT_DARK);
    }

    private static void drawArrow(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 26, y + 2, BLUEPRINT);
        graphics.fill(x + 21, y - 3, x + 23, y + 5, BLUEPRINT);
        graphics.fill(x + 23, y - 2, x + 26, y + 4, BLUEPRINT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty() || hoveredSlot == null || hoveredSlot.hasItem()) {
            return;
        }
        String key = switch (hoveredSlot.index) {
            case DraftingTableMenu.SLOT_PAPER -> "container.swindustry.drafting_table.paper_hint";
            case DraftingTableMenu.SLOT_INK -> "container.swindustry.drafting_table.ink_hint";
            case DraftingTableMenu.SLOT_REFERENCE -> "container.swindustry.drafting_table.reference_hint";
            case DraftingTableMenu.SLOT_RESULT -> "container.swindustry.drafting_table.result_hint";
            default -> null;
        };
        if (key != null) {
            graphics.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
