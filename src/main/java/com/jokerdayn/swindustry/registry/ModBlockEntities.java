package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.campfire.PrimitiveCampfireBlockEntity;
import com.jokerdayn.swindustry.kiln.ClayKilnBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entities. Only blocks that genuinely have to remember something get one. */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SWIndustry.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrimitiveCampfireBlockEntity>>
        PRIMITIVE_CAMPFIRE = BLOCK_ENTITIES.register(
            "primitive_campfire",
            () -> BlockEntityType.Builder
                .of(PrimitiveCampfireBlockEntity::new, ModBlocks.PRIMITIVE_CAMPFIRE.get())
                .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClayKilnBlockEntity>>
        CLAY_KILN = BLOCK_ENTITIES.register(
            "clay_kiln",
            () -> BlockEntityType.Builder
                .of(ClayKilnBlockEntity::new, ModBlocks.CLAY_KILN_PORT.get())
                .build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
