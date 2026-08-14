package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockItem;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockItem;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockItem;
import com.rieno.gadgetsandgizmos.content.CTTooltipBlockItem;
import com.rieno.gadgetsandgizmos.content.ClawBlockItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.content.ConfigurationClipboardItem;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockItem;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockItem;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockItem;
import com.rieno.gadgetsandgizmos.content.SmallThrusterBlockItem;
import com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinItem;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockItem;
import com.rieno.gadgetsandgizmos.content.PropulsionUpgradeItem;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.rieno.gadgetsandgizmos.content.ThrusterLenseItem;
import com.rieno.gadgetsandgizmos.content.ProcessingUpgradeItem;
import com.rieno.gadgetsandgizmos.content.VerticalAxisVariantBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Register addon items
public final class CTItems {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister.Items REGISTRAR = DeferredRegister.createItems(CreateThrusters.MOD_ID);

    @Nullable
    public static final DeferredItem<BlockItem> THRUSTER = register("thruster",
        () -> new CTTooltipBlockItem(CTBlocks.THRUSTER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> RCS_THRUSTER = register("rcs_thruster",
            () -> new CTTooltipBlockItem(CTBlocks.RCS_THRUSTER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<Item> BLACKSTONE_ALLOY = register("blackstone_alloy",
            () -> new Item(new Item.Properties()));
    @Nullable
    public static final DeferredItem<Item> BLACKSTONE_SHEET = register("blackstone_sheet",
            () -> new Item(new Item.Properties()));
    @Nullable
    public static final DeferredItem<Item> COMPUTATION_MECHANISM = register("computation_mechanism",
            () -> new Item(new Item.Properties()));
    @Nullable
    public static final DeferredItem<Item> INCOMPLETE_COMPUTATION_MECHANISM = register("incomplete_computation_mechanism",
            () -> new Item(new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> BLACKSTONE_ALLOY_BLOCK =
            register("blackstone_alloy_block",
                    () -> new BlockItem(CTBlocks.BLACKSTONE_ALLOY_BLOCK.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> SMALL_THRUSTER = register("small_thruster",
        () -> new SmallThrusterBlockItem(CTBlocks.THRUSTER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> FUEL_OXIDIZER = register("fuel_oxidizer",
        () -> new CTTooltipBlockItem(CTBlocks.FUEL_OXIDIZER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> THRUSTER_BEARING = register("thruster_bearing",
        () -> new CTTooltipBlockItem(CTBlocks.THRUSTER_BEARING.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> AILERON_BEARING = register("aileron_bearing",
        () -> new CTTooltipBlockItem(CTBlocks.AILERON_BEARING.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> VECTOR_BEARING = register("vector_bearing",
        () -> new CTTooltipBlockItem(CTBlocks.VECTOR_BEARING.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> SCISSOR_PISTON = register("scissor_piston",
        () -> new CTTooltipBlockItem(CTBlocks.SCISSOR_PISTON.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> GYROSCOPE_LINK = register("gyroscope_link",
            () -> new GyroscopeLinkBlockItem(CTBlocks.GYROSCOPE_LINK.get(), new Item.Properties().stacksTo(1)));
    @Nullable
    public static final DeferredItem<BlockItem> DIAGNOSTIC_TABLET = register("diagnostic_tablet",
            () -> new DiagnosticTabletItem(CTBlocks.DIAGNOSTIC_TABLET.get(),
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    @Nullable
    public static final DeferredItem<BlockItem> DOUBLE_BUTTON = register("double_button",
            () -> new CTTooltipBlockItem(CTBlocks.DOUBLE_BUTTON.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> COPYCAT_DOUBLE_BUTTON = register("copycat_double_button",
            () -> new CTTooltipBlockItem(CTBlocks.COPYCAT_DOUBLE_BUTTON.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> VIRTUAL_ORIENTATION_SOURCE = register("virtual_orientation_source",
            () -> new CTTooltipBlockItem(CTBlocks.VIRTUAL_ORIENTATION_SOURCE.get(), new Item.Properties().stacksTo(1)));
    @Nullable
    public static final DeferredItem<BlockItem> GYRO_REDSTONE_BRIDGE = register("gyro_redstone_bridge",
            () -> new CTTooltipBlockItem(CTBlocks.GYRO_REDSTONE_BRIDGE.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> BIDIRECTIONAL_GEARBOX = register("bidirectional_gearbox",
        () -> new CTTooltipBlockItem(CTBlocks.BIDIRECTIONAL_GEARBOX.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> BI_DIRECTIONAL_GEARSHIFT = register("bi_directional_gearshift",
        () -> new CTTooltipBlockItem(CTBlocks.BI_DIRECTIONAL_GEARSHIFT.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> VERTICAL_BIDIRECTIONAL_GEARBOX = register("vertical_bidirectional_gearbox",
        () -> new VerticalAxisVariantBlockItem(CTBlocks.BIDIRECTIONAL_GEARBOX.get(), new Item.Properties(), "item.createthrusters.vertical_bidirectional_gearbox"));
    @Nullable
    public static final DeferredItem<BlockItem> ANALOGUE_JOYSTICK = register("analogue_joystick",
            () -> new AnalogueJoystickBlockItem(CTBlocks.ANALOGUE_JOYSTICK.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ANALOGUE_CONTRAPTION_CONTROLLER = register("analogue_contraption_controller",
        () -> new AnalogueContraptionControllerBlockItem(CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ADVANCED_CONTRAPTION_CONTROLLER = register("advanced_contraption_controller",
        () -> new AdvancedContraptionControllerBlockItem(CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> SHIP_CONTROL_MODULE = register("ship_control_module",
        () -> new CTTooltipBlockItem(CTBlocks.SHIP_CONTROL_MODULE.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> SHIP_COUPLER = register("ship_coupler",
        () -> new CTTooltipBlockItem(CTBlocks.SHIP_COUPLER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ACC_DISPLAY = CTBlocks.ACC_DISPLAY == null ? null
            : register("acc_display", () -> new CTTooltipBlockItem(CTBlocks.ACC_DISPLAY.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ACC_DISPLAY_BLOCK = CTBlocks.ACC_DISPLAY_BLOCK == null ? null
            : register("acc_display_block", () -> new CTTooltipBlockItem(CTBlocks.ACC_DISPLAY_BLOCK.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ACC_DISPLAY_PANEL = CTBlocks.ACC_DISPLAY_PANEL == null ? null
            : register("acc_display_panel", () -> new CTTooltipBlockItem(CTBlocks.ACC_DISPLAY_PANEL.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ACC_DISPLAY_HALF_PANEL = CTBlocks.ACC_DISPLAY_HALF_PANEL == null ? null
            : register("acc_display_half_panel", () -> new CTTooltipBlockItem(CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ACC_DISPLAY_SLAB = CTBlocks.ACC_DISPLAY_SLAB == null ? null
            : register("acc_display_slab", () -> new CTTooltipBlockItem(CTBlocks.ACC_DISPLAY_SLAB.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> UNIVERSAL_DISPLAY_ADAPTER = CTBlocks.UNIVERSAL_DISPLAY_ADAPTER == null ? null
            : register("universal_display_adapter", () -> new CTTooltipBlockItem(
                    CTBlocks.UNIVERSAL_DISPLAY_ADAPTER.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> SHIP_DOCK = register("ship_dock",
        () -> new ShipDockBlockItem(CTBlocks.SHIP_DOCK.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<BlockItem> ADVANCED_NAVIGATION_TABLE = register("advanced_navigation_table",
        () -> new CTTooltipBlockItem(CTBlocks.ADVANCED_NAVIGATION_TABLE.get(), new Item.Properties()));
    @Nullable
    public static final DeferredItem<PortableContraptionControllerItem> PORTABLE_CONTRAPTION_CONTROLLER = register("portable_contraption_controller",
        () -> new PortableContraptionControllerItem(new Item.Properties(), false));
    @Nullable
    public static final DeferredItem<PortableContraptionControllerItem> ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER = register("advanced_portable_contraption_controller",
        () -> new PortableContraptionControllerItem(new Item.Properties(), true));
    @Nullable
        public static final DeferredItem<BlockItem> ALTERNATOR = register("alternator",
        () -> new CTTooltipBlockItem(CTBlocks.ALTERNATOR.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> CLAW = register("claw",
            () -> new ClawBlockItem(CTBlocks.CLAW.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> POWERED_ZIPLINE = register("powered_zipline",
            () -> new PoweredZiplineBlockItem(CTBlocks.POWERED_ZIPLINE.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> ROPE_KNOT = register("rope_knot",
            () -> new CTTooltipBlockItem(CTBlocks.ROPE_KNOT.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> ANDESITE_CABLE = register("andesite_cable",
            () -> new CTTooltipBlockItem(CTBlocks.ANDESITE_CABLE.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> INDUSTRIAL_MOTOR = register("industrial_motor",
            () -> new CTTooltipBlockItem(CTBlocks.INDUSTRIAL_MOTOR.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> VARIABLE_TRANSMISSION = register("variable_transmission",
            () -> new CTTooltipBlockItem(CTBlocks.VARIABLE_TRANSMISSION.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> VERTICAL_VARIABLE_TRANSMISSION = register("vertical_variable_transmission",
            () -> new VerticalAxisVariantBlockItem(CTBlocks.VARIABLE_TRANSMISSION.get(), new Item.Properties(), "item.createthrusters.vertical_variable_transmission", net.minecraft.core.Direction.Axis.Y));
    @Nullable
        public static final DeferredItem<BlockItem> PHYSICS_GANTRY_CARRIAGE = register("physics_gantry_carriage",
            () -> new CTTooltipBlockItem(CTBlocks.PHYSICS_GANTRY_CARRIAGE.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> PHYSICS_GANTRY_SHAFT = register("physics_gantry_shaft",
            () -> new CTTooltipBlockItem(CTBlocks.PHYSICS_GANTRY_SHAFT.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<BlockItem> PHYSICS_GANTRY_BELT_WHEEL = register("physics_gantry_belt_wheel",
            () -> new CTTooltipBlockItem(CTBlocks.PHYSICS_GANTRY_BELT_WHEEL.get(), new Item.Properties()));
    @Nullable
        public static final DeferredItem<Item> THRUSTER_LENSE = register("thruster_lense",
            () -> new ThrusterLenseItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    @Nullable
        public static final DeferredItem<Item> SCISSOR_ARMS = register("scissor_arms",
            () -> new Item(new Item.Properties()));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMOKING_T1 = register("processing_upgrade_smoking_t1",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMOKING, 1));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMELTING_T1 = register("processing_upgrade_smelting_t1",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMELTING, 1));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_HAUNTING_T1 = register("processing_upgrade_haunting_t1",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.HAUNTING, 1));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMOKING_T2 = register("processing_upgrade_smoking_t2",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMOKING, 2));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMELTING_T2 = register("processing_upgrade_smelting_t2",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMELTING, 2));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_HAUNTING_T2 = register("processing_upgrade_haunting_t2",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.HAUNTING, 2));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMOKING_T3 = register("processing_upgrade_smoking_t3",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMOKING, 3));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMELTING_T3 = register("processing_upgrade_smelting_t3",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMELTING, 3));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_HAUNTING_T3 = register("processing_upgrade_haunting_t3",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.HAUNTING, 3));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMOKING_T4 = register("processing_upgrade_smoking_t4",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMOKING, 4));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_SMELTING_T4 = register("processing_upgrade_smelting_t4",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.SMELTING, 4));
    @Nullable
        public static final DeferredItem<Item> PROCESSING_UPGRADE_HAUNTING_T4 = register("processing_upgrade_haunting_t4",
            () -> new ProcessingUpgradeItem(new Item.Properties(), com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType.HAUNTING, 4));
    @Nullable
        public static final DeferredItem<Item> PROPULSION_UPGRADE_T1 = register("propulsion_upgrade_t1",
            () -> new PropulsionUpgradeItem(new Item.Properties().rarity(Rarity.EPIC), 1));
    @Nullable
        public static final DeferredItem<Item> PROPULSION_UPGRADE_T2 = register("propulsion_upgrade_t2",
            () -> new PropulsionUpgradeItem(new Item.Properties().rarity(Rarity.EPIC), 2));
    @Nullable
        public static final DeferredItem<Item> PROPULSION_UPGRADE_T3 = register("propulsion_upgrade_t3",
            () -> new PropulsionUpgradeItem(new Item.Properties().rarity(Rarity.EPIC), 3));
    @Nullable
        public static final DeferredItem<Item> PROPULSION_UPGRADE_T4 = register("propulsion_upgrade_t4",
            () -> new PropulsionUpgradeItem(new Item.Properties().rarity(Rarity.EPIC), 4));
    @Nullable
    public static final DeferredItem<PhysicsStaffItem> PHYSICS_STAFF = register("physics_staff",
            CTItems::createPhysicsStaffItem);
    @Nullable
    public static final DeferredItem<ContraptionNetworkLinkerItem> CONTRAPTION_NETWORK_LINKER = register("contraption_network_linker",
            CTItems::createContraptionNetworkLinkerItem);
    @Nullable
    public static final DeferredItem<Item> CONFIGURATION_CLIPBOARD = register("configuration_clipboard",
            () -> new ConfigurationClipboardItem(new Item.Properties().stacksTo(1)));
    @Nullable
    public static final DeferredItem<BlockItem> SHIPPING_MANIFEST = register("shipping_manifest",
            () -> new ShippingManifestBlockItem(CTBlocks.SHIPPING_MANIFEST.get(), new Item.Properties().stacksTo(16)));
    @Nullable
    public static final DeferredItem<ShippingScheduleItem> SHIPPING_SCHEDULE = register("shipping_schedule",
            () -> new ShippingScheduleItem(new Item.Properties().stacksTo(1)));
    @Nullable
    public static final DeferredItem<EntityLauncherItem> ENTITY_LAUNCHER = register("entity_launcher",
            CTItems::createEntityLauncherItem);
    @Nullable
    public static final DeferredItem<PlayerMannequinItem> PLAYER_MANNEQUIN = register("player_mannequin",
            CTItems::createPlayerMannequinItem);
    @Nullable
    public static final DeferredItem<PhysicsGogglesItem> PHYSICS_GOGGLES = register("physics_goggles",
            () -> new PhysicsGogglesItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    @Nullable
    public static final DeferredItem<Item> OXIDIZED_CREATIVE_BLAZE_CAKE = register("oxidized_creative_blaze_cake",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    @Nullable
    public static final DeferredItem<Item> MUSIC_DISC_KINETIC_CURRENCY = register("music_disc_kinetic_currency",
            () -> musicDisc(CTJukeboxSongs.KINETIC_CURRENCY));
    @Nullable
    public static final DeferredItem<Item> MUSIC_DISC_TWISTED_ALIVE = register("music_disc_twisted_alive",
            () -> musicDisc(CTJukeboxSongs.TWISTED_ALIVE));
    @Nullable
    public static final DeferredItem<Item> MUSIC_DISC_UNPLUG_THE_EARTH = register("music_disc_unplug_the_earth",
            () -> musicDisc(CTJukeboxSongs.UNPLUG_THE_EARTH));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the CT items
    @Nullable
    private static <T extends Item> DeferredItem<T> register(String id, Supplier<T> supplier) {
        return CTFeatureToggles.shouldRegisterItem(id) ? REGISTRAR.register(id, supplier) : null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the music disc
    private static Item musicDisc(ResourceKey<JukeboxSong> song) {
        return new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song));
    }

    // Create the physics staff item
    private static PhysicsStaffItem createPhysicsStaffItem() {
        Item.Properties properties = new Item.Properties().stacksTo(1).rarity(Rarity.COMMON);
        ClientAwareItemFactory factory = ClientAwareItemFactory.getInstance();
        if (factory != null) {
            return factory.createPhysicsStaffItem(properties);
        }
        return new PhysicsStaffItem(properties);
    }

    // Create the entity launcher item
    private static EntityLauncherItem createEntityLauncherItem() {
        Item.Properties properties = new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON);
        ClientAwareItemFactory factory = ClientAwareItemFactory.getInstance();
        if (factory != null) {
            return factory.createEntityLauncherItem(properties);
        }
        return new EntityLauncherItem(properties);
    }

    // Create the player mannequin item
    private static PlayerMannequinItem createPlayerMannequinItem() {
        Item.Properties properties = new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON);
        ClientAwareItemFactory factory = ClientAwareItemFactory.getInstance();
        if (factory != null) {
            return factory.createPlayerMannequinItem(properties);
        }
        return new PlayerMannequinItem(properties);
    }

    // Create the contraption network linker item
    private static ContraptionNetworkLinkerItem createContraptionNetworkLinkerItem() {
        Item.Properties properties = new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON);
        ClientAwareItemFactory factory = ClientAwareItemFactory.getInstance();
        if (factory != null) {
            return factory.createContraptionNetworkLinkerItem(properties);
        }
        return new ContraptionNetworkLinkerItem(properties);
    }

    // Initialize the CT items
    private CTItems() {
    }
}
