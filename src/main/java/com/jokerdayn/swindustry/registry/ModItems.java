package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.blueprint.MultiblockBlueprintItem;
import com.jokerdayn.swindustry.blueprint.MultiblockBlueprints;
import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items: one per placeable block, the leavings of a fire, and the bronze age.
 *
 * <p>Bronze gear is built on {@link ModToolTiers#BRONZE} and {@link ModArmorMaterials#BRONZE}, with
 * the same attack numbers vanilla gives each tool shape. Only the material underneath differs, so a
 * bronze axe swings like an axe and a bronze hoe tills like a hoe, with bronze's own reach.</p>
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(SWIndustry.MODID);

    // ---- Blocks -------------------------------------------------------

    public static final DeferredHolder<Item, BlockItem> PRIMITIVE_CAMPFIRE = ITEMS.register(
        "primitive_campfire",
        () -> new BlockItem(ModBlocks.PRIMITIVE_CAMPFIRE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> RAW_CLAY_BRICKS = ITEMS.register(
        "raw_clay_bricks",
        () -> new BlockItem(ModBlocks.RAW_CLAY_BRICKS.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CLAY_KILN_PORT = ITEMS.register(
        "clay_kiln_port",
        () -> new BlockItem(ModBlocks.CLAY_KILN_PORT.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> DRAFTING_TABLE = ITEMS.register(
        "drafting_table",
        () -> new BlockItem(ModBlocks.DRAFTING_TABLE.get(), new Item.Properties()));

    // ---- Leavings -----------------------------------------------------

    /**
     * What a fire leaves behind besides charcoal.
     *
     * <p>Good for nothing at the moment, and that is the point: a fire that produced only useful
     * things would be a machine. This one produces a mess, and the mess is worth keeping because
     * something later will want it.</p>
     */
    public static final DeferredHolder<Item, Item> SOOT =
        ITEMS.register("soot", () -> new Item(new Item.Properties()));

    // ---- Engineering --------------------------------------------------

    public static final DeferredHolder<Item, PrimitiveEngineerGogglesItem> PRIMITIVE_ENGINEER_GOGGLES =
        ITEMS.register("primitive_engineer_goggles", () -> new PrimitiveEngineerGogglesItem(
            new Item.Properties().durability(
                ArmorItem.Type.HELMET.getDurability(ModArmorMaterials.GOGGLES_DURABILITY))));

    public static final DeferredHolder<Item, MultiblockBlueprintItem> CLAY_KILN_BLUEPRINT = ITEMS.register(
        "clay_kiln_blueprint",
        () -> new MultiblockBlueprintItem(MultiblockBlueprints.CLAY_KILN.id(),
            new Item.Properties().stacksTo(1)));

    // ---- Bronze tools -------------------------------------------------

    public static final DeferredHolder<Item, SwordItem> BRONZE_SWORD = ITEMS.register(
        "bronze_sword",
        () -> new SwordItem(ModToolTiers.BRONZE, new Item.Properties()
            .attributes(SwordItem.createAttributes(ModToolTiers.BRONZE, 3, -2.4F))));

    public static final DeferredHolder<Item, PickaxeItem> BRONZE_PICKAXE = ITEMS.register(
        "bronze_pickaxe",
        () -> new PickaxeItem(ModToolTiers.BRONZE, new Item.Properties()
            .attributes(PickaxeItem.createAttributes(ModToolTiers.BRONZE, 1.0F, -2.8F))));

    public static final DeferredHolder<Item, AxeItem> BRONZE_AXE = ITEMS.register(
        "bronze_axe",
        () -> new AxeItem(ModToolTiers.BRONZE, new Item.Properties()
            .attributes(AxeItem.createAttributes(ModToolTiers.BRONZE, 6.0F, -3.1F))));

    public static final DeferredHolder<Item, ShovelItem> BRONZE_SHOVEL = ITEMS.register(
        "bronze_shovel",
        () -> new ShovelItem(ModToolTiers.BRONZE, new Item.Properties()
            .attributes(ShovelItem.createAttributes(ModToolTiers.BRONZE, 1.5F, -3.0F))));

    public static final DeferredHolder<Item, HoeItem> BRONZE_HOE = ITEMS.register(
        "bronze_hoe",
        () -> new HoeItem(ModToolTiers.BRONZE, new Item.Properties()
            .attributes(HoeItem.createAttributes(ModToolTiers.BRONZE, -2.0F, -1.0F))));

    // ---- Bronze armour ------------------------------------------------

    public static final DeferredHolder<Item, ArmorItem> BRONZE_HELMET = ITEMS.register(
        "bronze_helmet",
        () -> new ArmorItem(ModArmorMaterials.bronze(), ArmorItem.Type.HELMET, new Item.Properties()
            .durability(ArmorItem.Type.HELMET.getDurability(ModArmorMaterials.BRONZE_DURABILITY))));

    public static final DeferredHolder<Item, ArmorItem> BRONZE_CHESTPLATE = ITEMS.register(
        "bronze_chestplate",
        () -> new ArmorItem(ModArmorMaterials.bronze(), ArmorItem.Type.CHESTPLATE, new Item.Properties()
            .durability(ArmorItem.Type.CHESTPLATE.getDurability(ModArmorMaterials.BRONZE_DURABILITY))));

    public static final DeferredHolder<Item, ArmorItem> BRONZE_LEGGINGS = ITEMS.register(
        "bronze_leggings",
        () -> new ArmorItem(ModArmorMaterials.bronze(), ArmorItem.Type.LEGGINGS, new Item.Properties()
            .durability(ArmorItem.Type.LEGGINGS.getDurability(ModArmorMaterials.BRONZE_DURABILITY))));

    public static final DeferredHolder<Item, ArmorItem> BRONZE_BOOTS = ITEMS.register(
        "bronze_boots",
        () -> new ArmorItem(ModArmorMaterials.bronze(), ArmorItem.Type.BOOTS, new Item.Properties()
            .durability(ArmorItem.Type.BOOTS.getDurability(ModArmorMaterials.BRONZE_DURABILITY))));

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
