package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

// Register the client renderers
public final class CTClientRenderers {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client renderers
    private CTClientRenderers() {
    }

    // Register the visualizers
    public static void registerVisualizers() {
        if (CTBlockEntities.BIDIRECTIONAL_GEARBOX != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.BIDIRECTIONAL_GEARBOX.get())
                .factory(BiDirectionalGearboxVisual::new)
                .apply();
        }
        if (CTBlockEntities.BI_DIRECTIONAL_GEARSHIFT != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.BI_DIRECTIONAL_GEARSHIFT.get())
                    .factory(BiDirectionalGearboxVisual::new)
                    .apply();
        }
        if (CTBlockEntities.THRUSTER_BEARING != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.THRUSTER_BEARING.get())
                    .factory(ThrusterBearingVisual::new)
                    .apply();
        }
        if (CTBlockEntities.AILERON_BEARING != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.AILERON_BEARING.get())
                    .factory(AileronBearingShaftVisual::new)
                    .neverSkipVanillaRender()
                    .apply();
        }
        if (CTBlockEntities.SCISSOR_PISTON != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.SCISSOR_PISTON.get())
                    .factory(ScissorPistonShaftVisual::new)
                    .neverSkipVanillaRender()
                    .apply();
        }
        if (CTBlockEntities.CLAW != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.CLAW.get())
                .factory(ClawVisual::new)
                .neverSkipVanillaRender()
                .apply();
        }
        if (CTBlockEntities.PHYSICS_GANTRY_CARRIAGE != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.PHYSICS_GANTRY_CARRIAGE.get())
                .factory(PhysicsGantryCarriageVisual::new)
                .apply();
        }
        if (CTBlockEntities.PHYSICS_GANTRY_BELT_WHEEL != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.PHYSICS_GANTRY_BELT_WHEEL.get())
                .factory(PhysicsGantryBeltWheelVisual::new)
                .neverSkipVanillaRender()
                .apply();
        }
        if (CTBlockEntities.RCS_THRUSTER != null) {
            SimpleBlockEntityVisualizer.builder(CTBlockEntities.RCS_THRUSTER.get())
                    .factory(RcsThrusterVisual::new)
                    .apply();
        }

    }

    // Register the renderers
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        CTPartialModels.init();
        if (CTEntityTypes.LAUNCHED_CLAW != null) {
            evt.registerEntityRenderer(CTEntityTypes.LAUNCHED_CLAW.get(), EntityLauncherClawRenderer::new);
        }
        if (CTEntityTypes.PLAYER_MANNEQUIN != null) {
            evt.registerEntityRenderer(CTEntityTypes.PLAYER_MANNEQUIN.get(), PlayerMannequinRenderer::new);
        }
        if (CTBlockEntities.ALTERNATOR != null) evt.registerBlockEntityRenderer(CTBlockEntities.ALTERNATOR.get(), AlternatorRenderer::new);
        if (CTBlockEntities.FUEL_OXIDIZER != null) evt.registerBlockEntityRenderer(CTBlockEntities.FUEL_OXIDIZER.get(), FuelOxidizerRenderer::new);
        if (CTBlockEntities.INDUSTRIAL_MOTOR != null) evt.registerBlockEntityRenderer(CTBlockEntities.INDUSTRIAL_MOTOR.get(), IndustrialMotorRenderer::new);
        if (CTBlockEntities.VARIABLE_TRANSMISSION != null) evt.registerBlockEntityRenderer(CTBlockEntities.VARIABLE_TRANSMISSION.get(), VariableTransmissionRenderer::new);
        if (CTBlockEntities.BIDIRECTIONAL_GEARBOX != null) evt.registerBlockEntityRenderer(CTBlockEntities.BIDIRECTIONAL_GEARBOX.get(), BiDirectionalGearboxRenderer::new);
        if (CTBlockEntities.BI_DIRECTIONAL_GEARSHIFT != null) evt.registerBlockEntityRenderer(cast(CTBlockEntities.BI_DIRECTIONAL_GEARSHIFT.get()), BiDirectionalGearboxRenderer::new);
        if (CTBlockEntities.VECTOR_BEARING != null) evt.registerBlockEntityRenderer(CTBlockEntities.VECTOR_BEARING.get(), VectorBearingRenderer::new);
        if (CTBlockEntities.AILERON_BEARING != null) evt.registerBlockEntityRenderer(CTBlockEntities.AILERON_BEARING.get(), AileronBearingRenderer::new);
        if (CTBlockEntities.SCISSOR_PISTON != null) evt.registerBlockEntityRenderer(CTBlockEntities.SCISSOR_PISTON.get(), ScissorPistonRenderer::new);
        if (CTBlockEntities.SCISSOR_PISTON_ARM != null) evt.registerBlockEntityRenderer(CTBlockEntities.SCISSOR_PISTON_ARM.get(), ScissorPistonArmRenderer::new);
        if (CTBlockEntities.THRUSTER != null) evt.registerBlockEntityRenderer(CTBlockEntities.THRUSTER.get(), ThrusterRenderer::new);
        if (CTBlockEntities.THRUSTER_BEARING != null) evt.registerBlockEntityRenderer(CTBlockEntities.THRUSTER_BEARING.get(), ThrusterBearingRenderer::new);
        if (CTBlockEntities.ANALOGUE_JOYSTICK != null) evt.registerBlockEntityRenderer(CTBlockEntities.ANALOGUE_JOYSTICK.get(), AnalogueJoystickRenderer::new);
        if (CTBlockEntities.DOUBLE_BUTTON != null) evt.registerBlockEntityRenderer(CTBlockEntities.DOUBLE_BUTTON.get(), DoubleButtonRenderer::new);
        if (CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER != null) evt.registerBlockEntityRenderer(CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get(), AnalogueContraptionControllerRenderer::new);
        if (CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER != null) evt.registerBlockEntityRenderer(CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get(), AnalogueContraptionControllerRenderer::new);
        if (CTBlockEntities.ACC_DISPLAY != null) evt.registerBlockEntityRenderer(CTBlockEntities.ACC_DISPLAY.get(), AccDisplayRenderer::new);
        if (CTBlockEntities.ADVANCED_NAVIGATION_TABLE != null) evt.registerBlockEntityRenderer(CTBlockEntities.ADVANCED_NAVIGATION_TABLE.get(), AdvancedNavigationTableRenderer::new);
        if (CTBlockEntities.SHIP_DOCK != null) evt.registerBlockEntityRenderer(CTBlockEntities.SHIP_DOCK.get(), ShipDockRenderer::new);
        if (CTBlockEntities.DIAGNOSTIC_TABLET != null) evt.registerBlockEntityRenderer(CTBlockEntities.DIAGNOSTIC_TABLET.get(), DiagnosticTabletRenderer::new);
        if (CTBlockEntities.CLAW != null) evt.registerBlockEntityRenderer(CTBlockEntities.CLAW.get(), ClawRenderer::new);
        if (CTBlockEntities.ENTITY_LAUNCHER_ANCHOR != null) evt.registerBlockEntityRenderer(CTBlockEntities.ENTITY_LAUNCHER_ANCHOR.get(), EntityLauncherAnchorRenderer::new);
        if (CTBlockEntities.ROPE_KNOT != null) evt.registerBlockEntityRenderer(CTBlockEntities.ROPE_KNOT.get(), RopeKnotRenderer::new);
        if (CTBlockEntities.POWERED_ZIPLINE != null) evt.registerBlockEntityRenderer(CTBlockEntities.POWERED_ZIPLINE.get(), PoweredZiplineRenderer::new);
        if (CTBlockEntities.PHYSICS_GANTRY_CARRIAGE != null) evt.registerBlockEntityRenderer(CTBlockEntities.PHYSICS_GANTRY_CARRIAGE.get(), PhysicsGantryCarriageRenderer::new);
        if (CTBlockEntities.PHYSICS_GANTRY_SHAFT != null) evt.registerBlockEntityRenderer(CTBlockEntities.PHYSICS_GANTRY_SHAFT.get(), PhysicsGantryShaftRenderer::new);
        if (CTBlockEntities.PHYSICS_GANTRY_BELT_WHEEL != null) evt.registerBlockEntityRenderer(CTBlockEntities.PHYSICS_GANTRY_BELT_WHEEL.get(), PhysicsGantryBeltWheelRenderer::new);
        if (CTBlockEntities.RCS_THRUSTER != null) evt.registerBlockEntityRenderer(CTBlockEntities.RCS_THRUSTER.get(), RcsThrusterRenderer::new);
        evt.registerBlockEntityRenderer(BlockEntityType.LECTERN, PortableLecternControllerRenderer::new);

        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse("simulated:swivel_bearing_link_block"))
            .ifPresent(type -> evt.registerBlockEntityRenderer(cast(type), ThrusterSwivelBearingPlateRenderer::new));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Modify the baking result
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult evt) {
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath(
                CreateThrusters.MOD_ID, "copycat_double_button");
        evt.getModels().replaceAll((location, model) -> {
            if (!location.id().equals(blockId)
                    || ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())
                    || model instanceof CopycatDoubleButtonModel) {
                return model;
            }
            return new CopycatDoubleButtonModel(model);
        });
        AccDisplayConnectedTextures.register();
        if (CTBlocks.ACC_DISPLAY != null) {
            evt.getModels().replaceAll((location, model) -> {
                net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(location.id());
                if (block == CTBlocks.ACC_DISPLAY.get()
                        || block == CTBlocks.ACC_DISPLAY_BLOCK.get()
                        || block == CTBlocks.ACC_DISPLAY_PANEL.get()
                        || block == CTBlocks.ACC_DISPLAY_HALF_PANEL.get()
                        || block == CTBlocks.ACC_DISPLAY_SLAB.get()) {
                    return AccDisplayConnectedTextures.wrap(block, model);
                }
                return model;
            });
        }
    }

    // Get the cast
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityType<T> cast(BlockEntityType<?> type) {
        return (BlockEntityType<T>) type;
    }
}
