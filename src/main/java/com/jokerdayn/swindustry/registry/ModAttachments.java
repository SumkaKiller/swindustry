package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.progression.ProgressionState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/** Extra data hung on players. */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SWIndustry.MODID);

    /**
     * Where a player is in the opening.
     *
     * <p>Copied on death on purpose. Being killed by a drowned on the way back from a boulder is a
     * setback; being sent back to "chop some wood" for it would be a bug wearing a difficulty
     * costume.</p>
     */
    public static final Supplier<AttachmentType<ProgressionState>> PROGRESSION =
        ATTACHMENT_TYPES.register("progression",
            () -> AttachmentType.serializable(ProgressionState::new).copyOnDeath().build());

    private ModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
