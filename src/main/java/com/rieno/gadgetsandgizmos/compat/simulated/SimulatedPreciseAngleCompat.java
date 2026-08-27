package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticGraphHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.torsion_spring.TorsionSpringBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// Read Simulated's native precise kinetic angles without changing its block entities
public final class SimulatedPreciseAngleCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the precise angle compatibility
    private SimulatedPreciseAngleCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve a downstream angle driven by a Simulated torsion spring output
    public static double getTorsionSpringOutputAngleDegrees(KineticBlockEntity target) {
        Level level = target.getLevel();
        if (level == null) {
            return Double.NaN;
        }

        Set<KineticBlockEntity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        KineticBlockEntity current = target;
        double outputModifier = 1.0D;

        while (visited.add(current)) {
            KineticBlockEntity source = getKineticSource(level, current);
            if (source == null) {
                return Double.NaN;
            }

            Float edgeModifier = KineticGraphHelper.getRotationSpeedModifier(source, current);
            if (edgeModifier == null || !Float.isFinite(edgeModifier) || Math.abs(edgeModifier) < 1.0E-4F) {
                return Double.NaN;
            }
            outputModifier *= edgeModifier;
            if (!Double.isFinite(outputModifier)) {
                return Double.NaN;
            }

            if (source instanceof ExtraKinetics.ExtraKineticsBlockEntity extraOutput
                    && extraOutput.getParentBlockEntity() instanceof TorsionSpringBlockEntity torsionSpring) {
                return KineticAngleHelper.normalizeDegrees(torsionSpring.getAngle() * outputModifier);
            }
            current = source;
        }
        return Double.NaN;
    }

    // Get the exact kinetic source object, including Simulated virtual outputs
    private static @Nullable KineticBlockEntity getKineticSource(Level level, KineticBlockEntity target) {
        if (!target.hasSource() || target.source == null || !level.isLoaded(target.source)) {
            return null;
        }

        BlockEntity sourceBlockEntity = level.getBlockEntity(target.source);
        if (sourceBlockEntity instanceof ExtraKinetics extraKinetics
                && target.source instanceof ExtraBlockPos) {
            KineticBlockEntity extraSource = extraKinetics.getExtraKinetics();
            if (extraSource != null) {
                return extraSource;
            }
        }
        return sourceBlockEntity instanceof KineticBlockEntity kineticSource ? kineticSource : null;
    }
}
