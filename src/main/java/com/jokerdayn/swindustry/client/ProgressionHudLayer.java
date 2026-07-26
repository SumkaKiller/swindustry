package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.Config;
import com.jokerdayn.swindustry.progression.ClientProgression;
import com.jokerdayn.swindustry.progression.ProgressionStep;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The objective panel in the top-right corner.
 *
 * <p>This is the pack's substitute for a quest book, and it is modelled on Starbound's quest
 * tracker rather than on a Minecraft quest mod: a small framed panel that owns one corner of the
 * screen, states one objective, and then leaves you alone. There is no window to open and no tree to
 * browse.</p>
 *
 * <p>It slides in from the edge and fades up when the objective changes, so a change is noticed
 * without a sound cue doing all the work, and then it sits perfectly still.</p>
 */
public class ProgressionHudLayer implements LayeredDraw.Layer {

    // ---- layout ----
    private static final int MARGIN = 6;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 5;
    private static final int ACCENT_WIDTH = 3;
    /** Deliberately narrow. The panel should own a corner, not a third of the screen. */
    private static final int MAX_TEXT_WIDTH = 132;
    private static final int LABEL_HEIGHT = 5;      // the 0.5-scale label
    private static final int GAP_LABEL = 3;
    private static final int GAP_TITLE = 4;
    private static final int GAP_DIVIDER = 4;
    private static final int GAP_HINT = 2;
    private static final int BULLET_GUTTER = 6;

    /** Ticks the panel takes to arrive. */
    private static final float ARRIVE_TICKS = 10.0F;
    /** How far right of its resting place the panel starts. */
    private static final float SLIDE_DISTANCE = 22.0F;

    // ---- palette: dark navy panel, steel frame, brass fittings ----
    private static final int SHADOW = 0x66000000;
    private static final int FRAME_OUTER = 0xFF0A0D12;
    private static final int FRAME = 0xFF7C8CA0;
    private static final int PANEL = 0xF01A2130;
    private static final int SHEEN_TOP = 0x24FFFFFF;
    private static final int SHEEN_BOTTOM = 0x00FFFFFF;
    private static final int BRASS = 0xFFCE9440;
    private static final int BRASS_DIM = 0xFF8A6428;
    private static final int LABEL = 0xFF7E8CA0;
    private static final int TITLE = 0xFFF6EEDC;
    private static final int HINT = 0xFF9AA6B6;
    private static final int DIVIDER = 0xFF313D50;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!Config.SHOW_PROGRESSION_HUD.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        ProgressionStep step = ClientProgression.step();
        if (step.isComplete()) {
            return;
        }

        float arrival = arrival(deltaTracker);
        if (arrival <= 0.01F) {
            return;
        }

        Font font = minecraft.font;
        Component title = step.title();
        Component label = Component.translatable("hud.swindustry.objective",
            step.ordinal() + 1, ProgressionStep.values().length - 1);
        List<FormattedCharSequence> hintLines =
            font.split(step.hint(), MAX_TEXT_WIDTH - BULLET_GUTTER);

        int textWidth = Math.max(font.width(title), font.width(label) / 2);
        for (FormattedCharSequence line : hintLines) {
            textWidth = Math.max(textWidth, font.width(line) + BULLET_GUTTER);
        }
        textWidth = Math.min(textWidth, MAX_TEXT_WIDTH);

        int innerHeight = LABEL_HEIGHT + GAP_LABEL + font.lineHeight;
        if (!hintLines.isEmpty()) {
            innerHeight += GAP_TITLE + 1 + GAP_DIVIDER
                + hintLines.size() * font.lineHeight
                + (hintLines.size() - 1) * GAP_HINT;
        }

        int panelWidth = ACCENT_WIDTH + PAD_X + textWidth + PAD_X;
        int panelHeight = PAD_Y + innerHeight + PAD_Y;

        // Eased slide in from the edge, so the panel arrives rather than blinking into being.
        int slide = Math.round((1.0F - arrival) * SLIDE_DISTANCE);
        int right = graphics.guiWidth() - MARGIN + slide;
        int left = right - panelWidth;
        int top = MARGIN;
        int bottom = top + panelHeight;
        float alpha = arrival;

        // ---- frame ----
        chamfered(graphics, left + 2, top + 2, right + 2, bottom + 2, withAlpha(SHADOW, alpha));
        chamfered(graphics, left - 1, top - 1, right + 1, bottom + 1, withAlpha(FRAME_OUTER, alpha));
        chamfered(graphics, left, top, right, bottom, withAlpha(FRAME, alpha));
        chamfered(graphics, left + 1, top + 1, right - 1, bottom - 1, withAlpha(PANEL, alpha));

        // A sheen down the top third reads as glass over the panel rather than a flat rectangle.
        graphics.fillGradient(left + 2, top + 2, right - 2, top + 2 + panelHeight / 3,
            withAlpha(SHEEN_TOP, alpha), withAlpha(SHEEN_BOTTOM, alpha));

        // ---- brass fitting down the left edge, with a bright cap at each end ----
        int accentLeft = left + 1;
        graphics.fill(accentLeft, top + 2, accentLeft + ACCENT_WIDTH - 1, bottom - 2,
            withAlpha(BRASS_DIM, alpha));
        graphics.fill(accentLeft, top + 2, accentLeft + 1, bottom - 2, withAlpha(BRASS, alpha));

        int textLeft = left + 1 + ACCENT_WIDTH + PAD_X - 1;
        int y = top + 1 + PAD_Y;

        // ---- "OBJECTIVE 3/8", at half size ----
        graphics.pose().pushPose();
        graphics.pose().translate(textLeft, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(font, label, 0, 0, withAlpha(LABEL, alpha), false);
        graphics.pose().popPose();
        y += LABEL_HEIGHT + GAP_LABEL;

        // ---- the objective itself ----
        graphics.drawString(font, title, textLeft, y, withAlpha(TITLE, alpha), false);
        y += font.lineHeight;

        if (hintLines.isEmpty()) {
            return;
        }

        y += GAP_TITLE;
        graphics.fill(textLeft, y, right - PAD_X, y + 1, withAlpha(DIVIDER, alpha));
        y += 1 + GAP_DIVIDER;

        // ---- how to do it, with a brass tick against the first line ----
        boolean first = true;
        for (FormattedCharSequence line : hintLines) {
            if (first) {
                graphics.fill(textLeft, y + 3, textLeft + 2, y + 5, withAlpha(BRASS, alpha));
                first = false;
            }
            graphics.drawString(font, line, textLeft + BULLET_GUTTER, y, withAlpha(HINT, alpha), false);
            y += font.lineHeight + GAP_HINT;
        }
    }

    /**
     * Draws a rectangle with two-pixel chamfers instead of square corners.
     *
     * <p>Nested chamfered rectangles are what give the panel its framed, machined look — square
     * corners on a dark box read as a debug overlay.</p>
     */
    private static void chamfered(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, colour);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, colour);
        graphics.fill(x0, y0 + 2, x1, y1 - 2, colour);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, colour);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, colour);
    }

    /** 0 while the panel is off screen, 1 once it has settled. Eased, not linear. */
    private static float arrival(DeltaTracker deltaTracker) {
        long since = ClientProgression.ticksSinceChange();
        if (since >= ARRIVE_TICKS) {
            return 1.0F;
        }
        float t = Mth.clamp((since + deltaTracker.getGameTimeDeltaPartialTick(false)) / ARRIVE_TICKS,
            0.0F, 1.0F);
        // ease-out cubic
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private static int withAlpha(int argb, float alpha) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaled = Mth.clamp(Math.round(baseAlpha * alpha), 0, 255);
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }
}
