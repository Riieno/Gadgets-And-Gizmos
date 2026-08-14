package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

// Keep the instanced Bidirectional Gearbox model aligned with its live block state
public class BiDirectionalGearboxVisual extends KineticBlockEntityVisual<BiDirectionalGearboxBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked shafts
    private final EnumMap<Direction, RotatingInstance> shafts = new EnumMap<>(Direction.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearbox visual
    public BiDirectionalGearboxVisual(VisualizationContext ctx, BiDirectionalGearboxBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Instancer<RotatingInstance> instancer = this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF));

        Direction.Axis primaryAxis = this.blockEntity.getPrimaryLaneAxis();
        Direction.Axis secondaryAxis = this.blockEntity.getSecondaryLaneAxis();
        for (Direction dir : Direction.values()) {
            Direction.Axis axis = dir.getAxis();
            if (axis != primaryAxis && axis != secondaryAxis) {
                continue;
            }

            RotatingInstance instance = instancer.createInstance();
            instance.setRotationAxis(axis)
                .setRotationalSpeed(beSpeed(dir))
                .setRotationOffset(this.blockEntity.getVisualShaftOffset(dir))
                    .setPosition((Vec3i) this.getVisualPosition())
                    .rotateToFace(Direction.SOUTH, dir);

            CTFlywheelVisuals.forceWhite(instance)
                    .setChanged();
            this.shafts.put(dir, instance);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the be speed
    private float beSpeed(Direction dir) {
        return this.blockEntity.getVisualShaftSpeed(dir) * RotatingInstance.SPEED_MULTIPLIER;
    }

    // Update the bi directional gearbox visual
    @Override
    public void update(float pt) {
        for (Map.Entry<Direction, RotatingInstance> entry : this.shafts.entrySet()) {
            Direction dir = entry.getKey();
            entry.getValue()
                    .setRotationAxis(dir.getAxis())
                    .setRotationalSpeed(beSpeed(dir))
                    .setRotationOffset(this.blockEntity.getVisualShaftOffset(dir));
            CTFlywheelVisuals.forceWhite(entry.getValue())
                    .setChanged();
        }
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        this.relight(this.shafts.values().toArray(FlatLit[]::new));
    }

    // Delete the bi directional gearbox visual
    @Override
    protected void _delete() {
        this.shafts.values().forEach(AbstractInstance::delete);
        this.shafts.clear();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        this.shafts.values().forEach(consumer);
    }
}
