package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The kiln's screen.
 *
 * <p>A furnace layout, because a player meeting their first machine should not also have to learn a
 * new interface. The one addition is the warning across the middle when the bricks around the port
 * are not a kiln — which is the single most likely thing to be wrong, and worth saying plainly
 * rather than leaving as a machine that mysteriously will not light.</p>
 */
public class ClayKilnScreen extends AbstractContainerScreen<ClayKilnMenu> {

    private static final ResourceLocation TEXTURE = SWIndustry.id("textures/gui/clay_kiln.png");

    /** The fire and arrow overlays live to the right of the 176-wide window in the same sheet. */
    private static final int OVERLAY_U = 176;
    private static final int FIRE_V = 0;
    private static final int ARROW_V = 14;

    private static final int FIRE_X = 58;
    private static final int FIRE_Y = 28;
    private static final int FIRE_WIDTH = 14;
    private static final int FIRE_HEIGHT = 14;

    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 28;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;

    public ClayKilnScreen(ClayKilnMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
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
            graphics.blit(TEXTURE,
                left + FIRE_X, top + FIRE_Y + FIRE_HEIGHT - lit,
                OVERLAY_U, FIRE_V + FIRE_HEIGHT - lit,
                FIRE_WIDTH, lit);
        }

        int progress = Math.round(menu.cookProgress() * ARROW_WIDTH);
        if (progress > 0) {
            graphics.blit(TEXTURE,
                left + ARROW_X, top + ARROW_Y,
                OVERLAY_U, ARROW_V,
                progress, ARROW_HEIGHT);
        }
    }

    /** Warm light on dark brick. The inherited dark grey would be invisible on this panel. */
    private static final int TITLE_COLOR = 0xFFF0DFBE;
    private static final int LABEL_COLOR = 0xFFBFA98C;
    private static final int WARNING_COLOR = 0xFFE8783A;

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);

        if (!menu.isStructureFormed()) {
            // Right-aligned on the same recessed band as the inventory label, which is the only
            // strip of the panel not covered by a slot.
            Component warning = Component.translatable("container.swindustry.clay_kiln.incomplete");
            int x = imageWidth - 8 - font.width(warning);
            graphics.drawString(font, warning, x, inventoryLabelY, WARNING_COLOR, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
