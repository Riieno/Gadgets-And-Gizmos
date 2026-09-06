package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AlternatorBlock;
import com.rieno.gadgetsandgizmos.content.AdvancedNavigationTableBlock;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlock;
import com.rieno.gadgetsandgizmos.content.AileronBearingLinkBlock;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlock;
import com.rieno.gadgetsandgizmos.content.AndesiteCableBlock;
import com.rieno.gadgetsandgizmos.content.BlackstoneAlloyBlock;
import com.rieno.gadgetsandgizmos.content.ClawBlock;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlock;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftBlock;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerPlaneBlock;
import com.rieno.gadgetsandgizmos.content.CopycatDoubleButtonBlock;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlock;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlock;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlock;
import com.rieno.gadgetsandgizmos.content.FuelOxidizerBlock;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlock;
import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlock;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlock;
import com.rieno.gadgetsandgizmos.content.GyroRedstoneBridgeBlock;
import com.rieno.gadgetsandgizmos.content.LauncherEndpointBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlock;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlock;
import com.rieno.gadgetsandgizmos.content.RopeKnotBlock;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlock;
import com.rieno.gadgetsandgizmos.content.ScissorPistonArmBlock;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlock;
import com.rieno.gadgetsandgizmos.content.ScissorPistonLinkBlock;
import com.rieno.gadgetsandgizmos.content.ShipControlModuleBlock;
import com.rieno.gadgetsandgizmos.content.ShipCouplerBlock;
import com.rieno.gadgetsandgizmos.content.ShipDockBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingLinkBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.content.VariableTransmissionBlock;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlock;
import com.rieno.gadgetsandgizmos.content.VectorBearingLinkBlock;
import com.rieno.gadgetsandgizmos.content.VirtualOrientationSourceBlock;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Register addon blocks
public final class CTBlocks {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister.Blocks REGISTRAR = DeferredRegister.createBlocks(CreateThrusters.MOD_ID);

    @Nullable
    public static final DeferredBlock<ThrusterBlock> THRUSTER = register("thruster",
            () -> new ThrusterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<RcsThrusterBlock> RCS_THRUSTER = register("rcs_thruster",
            () -> new RcsThrusterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<BlackstoneAlloyBlock> BLACKSTONE_ALLOY_BLOCK =
            register("blackstone_alloy_block",
                    () -> new BlackstoneAlloyBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK).strength(5.0f, 8.0f)
                            .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    @Nullable
    public static final DeferredBlock<FuelOxidizerBlock> FUEL_OXIDIZER = register("fuel_oxidizer",
            () -> new FuelOxidizerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ThrusterBearingBlock> THRUSTER_BEARING = register("thruster_bearing",
            () -> new ThrusterBearingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion().forceSolidOn()));

    @Nullable
    public static final DeferredBlock<ThrusterBearingLinkBlock> THRUSTER_BEARING_LINK = register("thruster_bearing_link",
            () -> new ThrusterBearingLinkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<AileronBearingBlock> AILERON_BEARING = register("aileron_bearing",
            () -> new AileronBearingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion().forceSolidOn()));

    @Nullable
    public static final DeferredBlock<AileronBearingLinkBlock> AILERON_BEARING_LINK = register("aileron_bearing_link",
            () -> new AileronBearingLinkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<VectorBearingBlock> VECTOR_BEARING = register("vector_bearing",
            () -> new VectorBearingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion().forceSolidOn()));

    @Nullable
    public static final DeferredBlock<VectorBearingLinkBlock> VECTOR_BEARING_LINK = register("vector_bearing_link",
            () -> new VectorBearingLinkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ScissorPistonBlock> SCISSOR_PISTON = register("scissor_piston",
            () -> new ScissorPistonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion().forceSolidOn()));

    @Nullable
    public static final DeferredBlock<ScissorPistonLinkBlock> SCISSOR_PISTON_LINK = register("scissor_piston_link",
            () -> new ScissorPistonLinkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ScissorPistonArmBlock> SCISSOR_PISTON_ARM = register("scissor_piston_arm",
            () -> new ScissorPistonArmBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<GyroscopeLinkBlock> GYROSCOPE_LINK = register("gyroscope_link",
            () -> new GyroscopeLinkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).requiresCorrectToolForDrops()));

    @Nullable
    public static final DeferredBlock<DoubleButtonBlock> DOUBLE_BUTTON = register("double_button",
            () -> new DoubleButtonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<CopycatDoubleButtonBlock> COPYCAT_DOUBLE_BUTTON = register("copycat_double_button",
            () -> new CopycatDoubleButtonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(2.0f).noOcclusion()));

    @Nullable
    public static final DeferredBlock<VirtualOrientationSourceBlock> VIRTUAL_ORIENTATION_SOURCE = register("virtual_orientation_source",
            () -> new VirtualOrientationSourceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).requiresCorrectToolForDrops()));

    @Nullable
    public static final DeferredBlock<GyroRedstoneBridgeBlock> GYRO_REDSTONE_BRIDGE = register("gyro_redstone_bridge",
            () -> new GyroRedstoneBridgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f).requiresCorrectToolForDrops()));

    @Nullable
    public static final DeferredBlock<BiDirectionalGearboxBlock> BIDIRECTIONAL_GEARBOX = register("bidirectional_gearbox",
            () -> new BiDirectionalGearboxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<BiDirectionalGearshiftBlock> BI_DIRECTIONAL_GEARSHIFT = register("bi_directional_gearshift",
            () -> new BiDirectionalGearshiftBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<AnalogueJoystickBlock> ANALOGUE_JOYSTICK = register("analogue_joystick",
            () -> new AnalogueJoystickBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<AnalogueContraptionControllerBlock> ANALOGUE_CONTRAPTION_CONTROLLER = register("analogue_contraption_controller",
            () -> new AnalogueContraptionControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ContraptionNetworkLinkerPlaneBlock> CONTRAPTION_NETWORK_LINKER_PLANE = register("contraption_network_linker_plane",
            () -> new ContraptionNetworkLinkerPlaneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.0f)
                    .noCollission().noOcclusion().replaceable().pushReaction(PushReaction.DESTROY)));

    @Nullable
    public static final DeferredBlock<AdvancedContraptionControllerBlock> ADVANCED_CONTRAPTION_CONTROLLER = register("advanced_contraption_controller",
            () -> new AdvancedContraptionControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ShipControlModuleBlock> SHIP_CONTROL_MODULE = register("ship_control_module",
            () -> new ShipControlModuleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ShipCouplerBlock> SHIP_COUPLER = register("ship_coupler",
            () -> new ShipCouplerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 8.0f).sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<AccDisplayBlock> ACC_DISPLAY = register("acc_display",
            () -> accDisplay(AccDisplayBlock.DisplayType.BOARD));
    @Nullable
    public static final DeferredBlock<AccDisplayBlock> ACC_DISPLAY_BLOCK = register("acc_display_block",
            () -> accDisplay(AccDisplayBlock.DisplayType.BLOCK));
    @Nullable
    public static final DeferredBlock<AccDisplayBlock> ACC_DISPLAY_PANEL = register("acc_display_panel",
            () -> accDisplay(AccDisplayBlock.DisplayType.PANEL));
    @Nullable
    public static final DeferredBlock<AccDisplayBlock> ACC_DISPLAY_HALF_PANEL = register("acc_display_half_panel",
            () -> accDisplay(AccDisplayBlock.DisplayType.HALF_PANEL));
    @Nullable
    public static final DeferredBlock<AccDisplayBlock> ACC_DISPLAY_SLAB = register("acc_display_slab",
            () -> accDisplay(AccDisplayBlock.DisplayType.SLAB));
    @Nullable
    public static final DeferredBlock<UniversalDisplayAdapterBlock> UNIVERSAL_DISPLAY_ADAPTER =
            register("universal_display_adapter", () -> new UniversalDisplayAdapterBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)
                            .requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ShipDockBlock> SHIP_DOCK = register("ship_dock",
            () -> new ShipDockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<DiagnosticTabletBlock> DIAGNOSTIC_TABLET = register("diagnostic_tablet",
            () -> new DiagnosticTabletBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    @Nullable
    public static final DeferredBlock<AdvancedNavigationTableBlock> ADVANCED_NAVIGATION_TABLE = register("advanced_navigation_table",
            () -> new AdvancedNavigationTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ShippingManifestBlock> SHIPPING_MANIFEST = register("shipping_manifest",
            () -> new ShippingManifestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5f).noCollission().noOcclusion()));

    @Nullable
    public static final DeferredBlock<AlternatorBlock> ALTERNATOR = register("alternator",
            () -> new AlternatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<ClawBlock> CLAW = register("claw",
            () -> new ClawBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<EntityLauncherAnchorBlock> ENTITY_LAUNCHER_ANCHOR = register("entity_launcher_anchor",
            () -> new EntityLauncherAnchorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<PoweredZiplineBlock> POWERED_ZIPLINE = register("powered_zipline",
            () -> new PoweredZiplineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<RopeKnotBlock> ROPE_KNOT = register("rope_knot",
            () -> new RopeKnotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.2f).noCollission().noOcclusion()));

    @Nullable
    public static final DeferredBlock<LauncherEndpointBlock> LAUNCHER_ENDPOINT = register("launcher_endpoint",
            () -> new LauncherEndpointBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.0f).noCollission().noOcclusion()));

    @Nullable
    public static final DeferredBlock<AndesiteCableBlock> ANDESITE_CABLE = register("andesite_cable",
            () -> new AndesiteCableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5f).noOcclusion()));

    @Nullable
    public static final DeferredBlock<IndustrialMotorBlock> INDUSTRIAL_MOTOR = register("industrial_motor",
            () -> new IndustrialMotorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<VariableTransmissionBlock> VARIABLE_TRANSMISSION = register("variable_transmission",
            () -> new VariableTransmissionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<PhysicsGantryCarriageBlock> PHYSICS_GANTRY_CARRIAGE = register("physics_gantry_carriage",
            () -> new PhysicsGantryCarriageBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    @Nullable
    public static final DeferredBlock<PhysicsGantryShaftBlock> PHYSICS_GANTRY_SHAFT = register("physics_gantry_shaft",
            () -> new PhysicsGantryShaftBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).strength(3.0f).requiresCorrectToolForDrops().forceSolidOn()));

    @Nullable
    public static final DeferredBlock<PhysicsGantryBeltWheelBlock> PHYSICS_GANTRY_BELT_WHEEL = register("physics_gantry_belt_wheel",
            () -> new PhysicsGantryBeltWheelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the CT blocks
    @Nullable
    private static <T extends net.minecraft.world.level.block.Block> DeferredBlock<T> register(String id, Supplier<T> supplier) {
        return CTFeatureToggles.shouldRegisterBlock(id) ? REGISTRAR.register(id, supplier) : null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ACC display
    private static AccDisplayBlock accDisplay(AccDisplayBlock.DisplayType type) {
        return new AccDisplayBlock(type, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(2.5f)
                .requiresCorrectToolForDrops().noOcclusion());
    }

    // Initialize the CT blocks
    private CTBlocks() {
    }
}
