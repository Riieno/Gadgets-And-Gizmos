package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AlternatorBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedNavigationTableBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.AndesiteCableBlockEntity;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerPlaneBlockEntity;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlockEntity;
import com.rieno.gadgetsandgizmos.content.FuelOxidizerBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroRedstoneBridgeBlockEntity;
import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlockEntity;
import com.rieno.gadgetsandgizmos.content.LauncherEndpointBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffAnchorBlockEntity;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.content.RopeKnotBlockEntity;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonArmBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShipCouplerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.VariableTransmissionBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.VirtualOrientationSourceBlockEntity;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Register addon block entities
public final class CTBlockEntities {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<BlockEntityType<?>> REGISTRAR = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateThrusters.MOD_ID);

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThrusterBlockEntity>> THRUSTER = register("thruster",
            () -> BlockEntityType.Builder.of(ThrusterBlockEntity::new, CTBlocks.THRUSTER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RcsThrusterBlockEntity>> RCS_THRUSTER =
            register("rcs_thruster",
                    () -> BlockEntityType.Builder.of(
                            RcsThrusterBlockEntity::new, CTBlocks.RCS_THRUSTER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelOxidizerBlockEntity>> FUEL_OXIDIZER = register("fuel_oxidizer",
            () -> BlockEntityType.Builder.of(FuelOxidizerBlockEntity::new, CTBlocks.FUEL_OXIDIZER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThrusterBearingBlockEntity>> THRUSTER_BEARING = register("thruster_bearing",
            () -> BlockEntityType.Builder.of(ThrusterBearingBlockEntity::new, CTBlocks.THRUSTER_BEARING.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThrusterBearingLinkBlockEntity>> THRUSTER_BEARING_LINK = register("thruster_bearing_link",
            () -> BlockEntityType.Builder.of(ThrusterBearingLinkBlockEntity::new, CTBlocks.THRUSTER_BEARING_LINK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AileronBearingBlockEntity>> AILERON_BEARING = register("aileron_bearing",
            () -> BlockEntityType.Builder.of(AileronBearingBlockEntity::new, CTBlocks.AILERON_BEARING.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AileronBearingLinkBlockEntity>> AILERON_BEARING_LINK = register("aileron_bearing_link",
            () -> BlockEntityType.Builder.of(AileronBearingLinkBlockEntity::new, CTBlocks.AILERON_BEARING_LINK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VectorBearingBlockEntity>> VECTOR_BEARING = register("vector_bearing",
            () -> BlockEntityType.Builder.of(VectorBearingBlockEntity::new, CTBlocks.VECTOR_BEARING.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VectorBearingLinkBlockEntity>> VECTOR_BEARING_LINK = register("vector_bearing_link",
            () -> BlockEntityType.Builder.of(VectorBearingLinkBlockEntity::new, CTBlocks.VECTOR_BEARING_LINK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScissorPistonBlockEntity>> SCISSOR_PISTON = register("scissor_piston",
            () -> BlockEntityType.Builder.of(ScissorPistonBlockEntity::new, CTBlocks.SCISSOR_PISTON.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScissorPistonLinkBlockEntity>> SCISSOR_PISTON_LINK = register("scissor_piston_link",
            () -> BlockEntityType.Builder.of(ScissorPistonLinkBlockEntity::new, CTBlocks.SCISSOR_PISTON_LINK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScissorPistonArmBlockEntity>> SCISSOR_PISTON_ARM = register("scissor_piston_arm",
            () -> BlockEntityType.Builder.of(ScissorPistonArmBlockEntity::new, CTBlocks.SCISSOR_PISTON_ARM.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GyroscopeLinkBlockEntity>> GYROSCOPE_LINK = register("gyroscope_link",
            () -> BlockEntityType.Builder.of(GyroscopeLinkBlockEntity::new, CTBlocks.GYROSCOPE_LINK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiagnosticTabletBlockEntity>> DIAGNOSTIC_TABLET = register("diagnostic_tablet",
            () -> BlockEntityType.Builder.of(DiagnosticTabletBlockEntity::new,
                    CTBlocks.DIAGNOSTIC_TABLET.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DoubleButtonBlockEntity>> DOUBLE_BUTTON = register("double_button",
            () -> BlockEntityType.Builder.of(DoubleButtonBlockEntity::new,
                    CTBlocks.DOUBLE_BUTTON.get(), CTBlocks.COPYCAT_DOUBLE_BUTTON.get()).build(null),
            "copycat_double_button");

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VirtualOrientationSourceBlockEntity>> VIRTUAL_ORIENTATION_SOURCE = register("virtual_orientation_source",
            () -> BlockEntityType.Builder.of(VirtualOrientationSourceBlockEntity::new, CTBlocks.VIRTUAL_ORIENTATION_SOURCE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GyroRedstoneBridgeBlockEntity>> GYRO_REDSTONE_BRIDGE = register("gyro_redstone_bridge",
            () -> BlockEntityType.Builder.of(GyroRedstoneBridgeBlockEntity::new, CTBlocks.GYRO_REDSTONE_BRIDGE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BiDirectionalGearboxBlockEntity>> BIDIRECTIONAL_GEARBOX = register("bidirectional_gearbox",
            () -> BlockEntityType.Builder.of(BiDirectionalGearboxBlockEntity::new, CTBlocks.BIDIRECTIONAL_GEARBOX.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BiDirectionalGearshiftBlockEntity>> BI_DIRECTIONAL_GEARSHIFT = register("bi_directional_gearshift",
            () -> BlockEntityType.Builder.of(BiDirectionalGearshiftBlockEntity::new, CTBlocks.BI_DIRECTIONAL_GEARSHIFT.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnalogueJoystickBlockEntity>> ANALOGUE_JOYSTICK = register("analogue_joystick",
            () -> BlockEntityType.Builder.of(AnalogueJoystickBlockEntity::new, CTBlocks.ANALOGUE_JOYSTICK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnalogueContraptionControllerBlockEntity>> ANALOGUE_CONTRAPTION_CONTROLLER = register("analogue_contraption_controller",
            () -> BlockEntityType.Builder.of(AnalogueContraptionControllerBlockEntity::new, CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContraptionNetworkLinkerPlaneBlockEntity>> CONTRAPTION_NETWORK_LINKER_PLANE = register("contraption_network_linker_plane",
            () -> BlockEntityType.Builder.of(ContraptionNetworkLinkerPlaneBlockEntity::new, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedContraptionControllerBlockEntity>> ADVANCED_CONTRAPTION_CONTROLLER = register("advanced_contraption_controller",
            () -> BlockEntityType.Builder.of(AdvancedContraptionControllerBlockEntity::new, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AccDisplayBlockEntity>> ACC_DISPLAY =
            CTBlocks.ACC_DISPLAY == null ? null : register("acc_display",
                    () -> BlockEntityType.Builder.of(AccDisplayBlockEntity::new,
                            CTBlocks.ACC_DISPLAY.get(), CTBlocks.ACC_DISPLAY_BLOCK.get(),
                            CTBlocks.ACC_DISPLAY_PANEL.get(), CTBlocks.ACC_DISPLAY_HALF_PANEL.get(),
                            CTBlocks.ACC_DISPLAY_SLAB.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UniversalDisplayAdapterBlockEntity>> UNIVERSAL_DISPLAY_ADAPTER =
            CTBlocks.UNIVERSAL_DISPLAY_ADAPTER == null ? null : register("universal_display_adapter",
                    () -> BlockEntityType.Builder.of(UniversalDisplayAdapterBlockEntity::new,
                            CTBlocks.UNIVERSAL_DISPLAY_ADAPTER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedNavigationTableBlockEntity>> ADVANCED_NAVIGATION_TABLE = register("advanced_navigation_table",
            () -> BlockEntityType.Builder.of(AdvancedNavigationTableBlockEntity::new, CTBlocks.ADVANCED_NAVIGATION_TABLE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShippingManifestBlockEntity>> SHIPPING_MANIFEST = register("shipping_manifest",
            () -> BlockEntityType.Builder.of(ShippingManifestBlockEntity::new, CTBlocks.SHIPPING_MANIFEST.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipDockBlockEntity>> SHIP_DOCK = register("ship_dock",
            () -> BlockEntityType.Builder.of(ShipDockBlockEntity::new, CTBlocks.SHIP_DOCK.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipCouplerBlockEntity>> SHIP_COUPLER = register("ship_coupler",
            () -> BlockEntityType.Builder.of(ShipCouplerBlockEntity::new, CTBlocks.SHIP_COUPLER.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlternatorBlockEntity>> ALTERNATOR = register("alternator",
            () -> BlockEntityType.Builder.of(AlternatorBlockEntity::new, CTBlocks.ALTERNATOR.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClawBlockEntity>> CLAW = register("claw",
            () -> BlockEntityType.Builder.of(ClawBlockEntity::new, CTBlocks.CLAW.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EntityLauncherAnchorBlockEntity>> ENTITY_LAUNCHER_ANCHOR = register("entity_launcher_anchor",
            () -> BlockEntityType.Builder.of(EntityLauncherAnchorBlockEntity::new, CTBlocks.ENTITY_LAUNCHER_ANCHOR.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PoweredZiplineBlockEntity>> POWERED_ZIPLINE = register("powered_zipline",
            () -> BlockEntityType.Builder.of(PoweredZiplineBlockEntity::new, CTBlocks.POWERED_ZIPLINE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RopeKnotBlockEntity>> ROPE_KNOT = register("rope_knot",
            () -> BlockEntityType.Builder.of(RopeKnotBlockEntity::new, CTBlocks.ROPE_KNOT.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherEndpointBlockEntity>> LAUNCHER_ENDPOINT = register("launcher_endpoint",
            () -> BlockEntityType.Builder.of(LauncherEndpointBlockEntity::new, CTBlocks.LAUNCHER_ENDPOINT.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AndesiteCableBlockEntity>> ANDESITE_CABLE = register("andesite_cable",
            () -> BlockEntityType.Builder.of(AndesiteCableBlockEntity::new, CTBlocks.ANDESITE_CABLE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialMotorBlockEntity>> INDUSTRIAL_MOTOR = register("industrial_motor",
            () -> BlockEntityType.Builder.of(IndustrialMotorBlockEntity::new, CTBlocks.INDUSTRIAL_MOTOR.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VariableTransmissionBlockEntity>> VARIABLE_TRANSMISSION = register("variable_transmission",
            () -> BlockEntityType.Builder.of(VariableTransmissionBlockEntity::new, CTBlocks.VARIABLE_TRANSMISSION.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicsGantryCarriageBlockEntity>> PHYSICS_GANTRY_CARRIAGE = register("physics_gantry_carriage",
            () -> BlockEntityType.Builder.of(PhysicsGantryCarriageBlockEntity::new, CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicsGantryShaftBlockEntity>> PHYSICS_GANTRY_SHAFT = register("physics_gantry_shaft",
            () -> BlockEntityType.Builder.of(PhysicsGantryShaftBlockEntity::new, CTBlocks.PHYSICS_GANTRY_SHAFT.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicsGantryBeltWheelBlockEntity>> PHYSICS_GANTRY_BELT_WHEEL = register("physics_gantry_belt_wheel",
            () -> BlockEntityType.Builder.of(PhysicsGantryBeltWheelBlockEntity::new, CTBlocks.PHYSICS_GANTRY_BELT_WHEEL.get()).build(null));

    @Nullable
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicsStaffAnchorBlockEntity>> PHYSICS_STAFF_ANCHOR = register("physics_staff_anchor",
            () -> BlockEntityType.Builder.of(PhysicsStaffAnchorBlockEntity::new, CTBlocks.PHYSICS_STAFF_ANCHOR.get()).build(null));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the CT block entities
    @Nullable
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(
            String id, Supplier<BlockEntityType<T>> supplier, String... additionalBlockIds) {
        if (!CTFeatureToggles.shouldRegisterBlock(id)) {
            return null;
        }
        for (String additionalBlockId : additionalBlockIds) {
            if (!CTFeatureToggles.shouldRegisterBlock(additionalBlockId)) {
                return null;
            }
        }
        return REGISTRAR.register(id, supplier);
    }

    // Initialize the CT block entities
    private CTBlockEntities() {
    }
}
