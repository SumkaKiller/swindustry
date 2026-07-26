package com.jokerdayn.swindustry.progression;

import java.util.EnumSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * What one player has got through so far.
 *
 * <p>Attached to the player and carried across death and dimension changes, because losing your
 * place in the opening because a creeper found you would be a bad joke rather than a difficulty.</p>
 */
public class ProgressionState implements INBTSerializable<CompoundTag> {

    private static final String KEY_STEP = "Step";
    private static final String KEY_MILESTONES = "Milestones";

    private ProgressionStep step = ProgressionStep.CHOP_WOOD;
    private final EnumSet<ProgressionMilestone> milestones = EnumSet.noneOf(ProgressionMilestone.class);

    public ProgressionStep step() {
        return step;
    }

    /** Moves to a step directly. Returns whether anything actually changed. */
    public boolean setStep(ProgressionStep step) {
        if (this.step == step) {
            return false;
        }
        this.step = step;
        return true;
    }

    public boolean has(ProgressionMilestone milestone) {
        return milestones.contains(milestone);
    }

    /** Records a milestone. Returns whether it was news. */
    public boolean record(ProgressionMilestone milestone) {
        return milestones.add(milestone);
    }

    /** Packs the milestone set into the bitmask sent to the client. */
    public int milestoneBits() {
        int bits = 0;
        for (ProgressionMilestone milestone : milestones) {
            bits |= milestone.bit();
        }
        return bits;
    }

    public void setMilestoneBits(int bits) {
        milestones.clear();
        for (ProgressionMilestone milestone : ProgressionMilestone.values()) {
            if ((bits & milestone.bit()) != 0) {
                milestones.add(milestone);
            }
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        // Written by name rather than by ordinal, so inserting a step into the middle of the
        // sequence later does not silently teleport every existing save to the wrong objective.
        tag.putString(KEY_STEP, step.key());
        tag.putInt(KEY_MILESTONES, milestoneBits());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        step = ProgressionStep.byKey(tag.getString(KEY_STEP));
        setMilestoneBits(tag.getInt(KEY_MILESTONES));
    }
}
