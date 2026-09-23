package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.IonThrusterStacks;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariant;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.content.SupporterHeads;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Register the creative tabs
public final class CTCreativeTabs {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current row
    public static int CURRENT_ROW = 0;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Integer> SECTION_ROWS = new ConcurrentHashMap<>();

    public static final DeferredRegister<CreativeModeTab> REGISTRAR =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateThrusters.MOD_ID);

    public static final CTCreativeTabSection BLOCKS_SECTION = new CTCreativeTabSection(
            "blocks",
            Component.literal("Blocks"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "banner_blocks"),
            0
    );

    public static final CTCreativeTabSection ITEMS_SECTION = new CTCreativeTabSection(
            "items",
            Component.literal("Items"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "banner_items"),
            1
    );

    public static final CTCreativeTabSection SPECIAL_THANKS_SECTION = new CTCreativeTabSection(
            "special_thanks",
            Component.literal("Special Thanks"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "banner_special"),
            2
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTRAR.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createthrusters.main"))
                    .icon(() -> firstAvailable(CTItems.THRUSTER, CTItems.PHYSICS_GOGGLES, CTItems.PHYSICS_STAFF))
                    .displayItems((params, output) -> buildContents(stack -> {
                        if (!stack.isEmpty()) {
                            output.accept(stack);
                        }
                    }, stack -> {
                    }))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOMENS = REGISTRAR.register("lomens",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createthrusters.lomens"))
                    .icon(() -> firstAvailable(CTItems.MUSIC_DISC_KINETIC_CURRENCY))
                    .displayItems((params, output) -> buildLomensContents(output::accept, stack -> {
                    }))
                    .build());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build the contents
    public static void buildContents(Consumer<ItemStack> displayItems, Consumer<ItemStack> searchItems) {
        SECTION_ROWS.clear();
        int row = 0;

        // -----------------------------------------------------BLOCKS-----------------------------------------------------
        row = addSectionBanner(displayItems, BLOCKS_SECTION, row);
        int blockCount = 0;
        blockCount += accept(displayItems, searchItems, CTItems.THRUSTER);
        blockCount += acceptStack(displayItems, searchItems, IonThrusterStacks.create());
        blockCount += accept(displayItems, searchItems, CTItems.SMALL_THRUSTER);
        blockCount += accept(displayItems, searchItems, CTItems.RCS_THRUSTER);
        blockCount += accept(displayItems, searchItems, CTItems.BLACKSTONE_ALLOY_BLOCK);
        blockCount += accept(displayItems, searchItems, CTItems.BLACKSTONE_CASING);
        blockCount += accept(displayItems, searchItems, CTItems.FUEL_OXIDIZER);
        blockCount += accept(displayItems, searchItems, CTItems.ALTERNATOR);
        blockCount += accept(displayItems, searchItems, CTItems.ANDESITE_CABLE);
        blockCount += accept(displayItems, searchItems, CTItems.INDUSTRIAL_MOTOR);
        blockCount += accept(displayItems, searchItems, CTItems.VARIABLE_TRANSMISSION);
        blockCount += accept(displayItems, searchItems, CTItems.VERTICAL_VARIABLE_TRANSMISSION);
        blockCount += accept(displayItems, searchItems, CTItems.PHYSICS_GANTRY_SHAFT);
        blockCount += accept(displayItems, searchItems, CTItems.PHYSICS_GANTRY_BELT_WHEEL);
        blockCount += accept(displayItems, searchItems, CTItems.PHYSICS_GANTRY_CARRIAGE);
        blockCount += accept(displayItems, searchItems, CTItems.CLAW);
        blockCount += accept(displayItems, searchItems, CTItems.POWERED_ZIPLINE);
        blockCount += accept(displayItems, searchItems, CTItems.THRUSTER_BEARING);
        blockCount += accept(displayItems, searchItems, CTItems.AILERON_BEARING);
        blockCount += accept(displayItems, searchItems, CTItems.VECTOR_BEARING);
        blockCount += accept(displayItems, searchItems, CTItems.SCISSOR_PISTON);
        blockCount += accept(displayItems, searchItems, CTItems.GYROSCOPE_LINK);
        blockCount += accept(displayItems, searchItems, CTItems.DOUBLE_BUTTON);
        blockCount += accept(displayItems, searchItems, CTItems.COPYCAT_DOUBLE_BUTTON);
        blockCount += accept(displayItems, searchItems, CTItems.VIRTUAL_ORIENTATION_SOURCE);
        blockCount += accept(displayItems, searchItems, CTItems.GYRO_REDSTONE_BRIDGE);
        blockCount += accept(displayItems, searchItems, CTItems.BIDIRECTIONAL_GEARBOX);
        blockCount += accept(displayItems, searchItems, CTItems.BI_DIRECTIONAL_GEARSHIFT);
        blockCount += accept(displayItems, searchItems, CTItems.RATCHET_COGWHEEL);
        blockCount += accept(displayItems, searchItems, CTItems.LARGE_RATCHET_COGWHEEL);
        blockCount += accept(displayItems, searchItems, CTItems.VERTICAL_BIDIRECTIONAL_GEARBOX);
        blockCount += accept(displayItems, searchItems, CTItems.ANALOGUE_JOYSTICK);
        blockCount += accept(displayItems, searchItems, CTItems.ANALOGUE_CONTRAPTION_CONTROLLER);
        blockCount += accept(displayItems, searchItems, CTItems.ADVANCED_CONTRAPTION_CONTROLLER);
        blockCount += accept(displayItems, searchItems, CTItems.SHIP_CONTROL_MODULE);
        blockCount += accept(displayItems, searchItems, CTItems.SHIP_COUPLER);
        blockCount += accept(displayItems, searchItems, CTItems.ACC_DISPLAY);
        blockCount += accept(displayItems, searchItems, CTItems.ACC_DISPLAY_BLOCK);
        blockCount += accept(displayItems, searchItems, CTItems.ACC_DISPLAY_PANEL);
        blockCount += accept(displayItems, searchItems, CTItems.ACC_DISPLAY_HALF_PANEL);
        blockCount += accept(displayItems, searchItems, CTItems.ACC_DISPLAY_SLAB);
        blockCount += accept(displayItems, searchItems, CTItems.UNIVERSAL_DISPLAY_ADAPTER);
        blockCount += accept(displayItems, searchItems, CTItems.SHIP_DOCK);
        blockCount += accept(displayItems, searchItems, CTItems.ADVANCED_NAVIGATION_TABLE);
        blockCount += accept(displayItems, searchItems, CTItems.DIAGNOSTIC_TABLET);
        blockCount += accept(displayItems, searchItems, CTItems.SHIPPING_MANIFEST);
        blockCount += accept(displayItems, searchItems, CTItems.SMART_VAULT);
        blockCount += accept(displayItems, searchItems, CTItems.SMART_BATTERY);
        blockCount += accept(displayItems, searchItems, CTItems.SMART_TANK);
        blockCount += accept(displayItems, searchItems, CTItems.WORKER_POD);
        row = padSection(displayItems, row, blockCount);

        // -----------------------------------------------------ITEMS-----------------------------------------------------
        row = addSectionBanner(displayItems, ITEMS_SECTION, row);
        int itemCount = 0;
        itemCount += accept(displayItems, searchItems, CTItems.BLACKSTONE_ALLOY);
        itemCount += accept(displayItems, searchItems, CTItems.BLACKSTONE_SHEET);
        itemCount += accept(displayItems, searchItems, CTItems.COMPUTATION_MECHANISM);
        itemCount += accept(displayItems, searchItems, CTItems.PORTABLE_CONTRAPTION_CONTROLLER);
        itemCount += accept(displayItems, searchItems, CTItems.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER);
        itemCount += accept(displayItems, searchItems, CTItems.CONTRAPTION_NETWORK_LINKER);
        itemCount += accept(displayItems, searchItems, CTItems.CONFIGURATION_CLIPBOARD);
        itemCount += accept(displayItems, searchItems, CTItems.ENTITY_LAUNCHER);
        itemCount += accept(displayItems, searchItems, CTItems.SHIPPING_SCHEDULE);
        itemCount += accept(displayItems, searchItems, CTItems.THRUSTER_LENSE);
        itemCount += accept(displayItems, searchItems, CTItems.SCISSOR_ARMS);
        itemCount += accept(displayItems, searchItems, CTItems.OXIDIZED_CREATIVE_BLAZE_CAKE);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMOKING_T1);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMELTING_T1);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_HAUNTING_T1);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMOKING_T2);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMELTING_T2);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_HAUNTING_T2);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMOKING_T3);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMELTING_T3);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_HAUNTING_T3);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMOKING_T4);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_SMELTING_T4);
        itemCount += accept(displayItems, searchItems, CTItems.PROCESSING_UPGRADE_HAUNTING_T4);
        itemCount += accept(displayItems, searchItems, CTItems.PROPULSION_UPGRADE_T1);
        itemCount += accept(displayItems, searchItems, CTItems.PROPULSION_UPGRADE_T2);
        itemCount += accept(displayItems, searchItems, CTItems.PROPULSION_UPGRADE_T3);
        itemCount += accept(displayItems, searchItems, CTItems.PROPULSION_UPGRADE_T4);
        itemCount += accept(displayItems, searchItems, CTItems.PHYSICS_GOGGLES);
        itemCount += accept(displayItems, searchItems, CTItems.PHYSICS_STAFF);
        row = padSection(displayItems, row, itemCount);

        // ------------------------------------SPECIAL THANKS------------------------------------
        row = addSectionBanner(displayItems, SPECIAL_THANKS_SECTION, row);
        int specialThanksCount = 0;
        for (PlayerMannequinVariant variant : PlayerMannequinVariants.all()) {
            specialThanksCount += acceptSupporterHead(displayItems, searchItems, variant);
        }
        padSection(displayItems, row, specialThanksCount);
    }

    // Build the lomens contents
    private static void buildLomensContents(Consumer<ItemStack> displayItems, Consumer<ItemStack> searchItems) {
        accept(displayItems, searchItems, CTItems.MUSIC_DISC_KINETIC_CURRENCY);
        accept(displayItems, searchItems, CTItems.MUSIC_DISC_TWISTED_ALIVE);
        accept(displayItems, searchItems, CTItems.MUSIC_DISC_UNPLUG_THE_EARTH);
    }

    // Get the sections
    public static List<CTCreativeTabSection> sections() {
        return List.of(BLOCKS_SECTION, ITEMS_SECTION, SPECIAL_THANKS_SECTION);
    }

    // Get the section row
    public static int getSectionRow(CTCreativeTabSection section) {
        return SECTION_ROWS.getOrDefault(section.id(), -1);
    }

    // Add the section banner
    private static int addSectionBanner(Consumer<ItemStack> displayItems, CTCreativeTabSection section, int row) {
        SECTION_ROWS.put(section.id(), row);
        for (int i = 0; i < 9; i++) {
            displayItems.accept(ItemStack.EMPTY);
        }
        return row + 1;
    }

    // Get the pad section
    private static int padSection(Consumer<ItemStack> displayItems, int row, int itemCount) {
        int padding = (9 - (itemCount % 9)) % 9;
        for (int i = 0; i < padding; i++) {
            displayItems.accept(ItemStack.EMPTY);
        }
        return row + (itemCount + padding) / 9;
    }

    // Accept the CT creative tabs value
    private static int accept(Consumer<ItemStack> displayItems, Consumer<ItemStack> searchItems, Supplier<? extends ItemLike> itemSupplier) {
        if (itemSupplier == null) {
            return 0;
        }

        ItemStack stack = new ItemStack(itemSupplier.get());
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !CreateThrusters.MOD_ID.equals(itemId.getNamespace())) {
            return 0;
        }
        if (!CTFeatureToggles.isItemEnabled(itemId.getPath())) {
            return 0;
        }
        displayItems.accept(stack);
        searchItems.accept(stack.copy());
        return 1;
    }

    // Accept the stack
    private static int acceptStack(Consumer<ItemStack> displayItems, Consumer<ItemStack> searchItems, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !CreateThrusters.MOD_ID.equals(itemId.getNamespace())) {
            return 0;
        }
        if (!CTFeatureToggles.isItemEnabled(itemId.getPath())) {
            return 0;
        }
        displayItems.accept(stack);
        searchItems.accept(stack.copy());
        return 1;
    }

    // Accept a marked vanilla player head for one supporter mannequin variant
    private static int acceptSupporterHead(Consumer<ItemStack> displayItems,
                                           Consumer<ItemStack> searchItems,
                                           PlayerMannequinVariant variant) {
        if (!CTFeatureToggles.isItemEnabled("player_mannequin")) {
            return 0;
        }
        ItemStack stack = SupporterHeads.createStack(variant);
        if (stack.isEmpty()) {
            return 0;
        }
        displayItems.accept(stack);
        searchItems.accept(stack.copy());
        return 1;
    }

    // Find the first available item
    @SafeVarargs
    private static ItemStack firstAvailable(Supplier<? extends ItemLike>... suppliers) {
        for (Supplier<? extends ItemLike> supplier : suppliers) {
            if (supplier != null) {
                ItemStack stack = new ItemStack(supplier.get());
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemId != null
                        && CreateThrusters.MOD_ID.equals(itemId.getNamespace())
                        && CTFeatureToggles.isItemEnabled(itemId.getPath())) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT creative tabs
    private CTCreativeTabs() {
    }
}
