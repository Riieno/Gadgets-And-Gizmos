package com.rieno.gadgetsandgizmos.compat.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSubLevelRelationRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Bridge optional articulated mods to the generic SCM relation and control APIs
public final class OptionalScmCompatibility {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation SYNAXIS_RELATIONS =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "synaxis_sublevel_relations");
    private static final ResourceLocation COASTER_RELATIONS =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "coaster_sublevel_relations");
    private static final ResourceLocation SYNAXIS_CONTROL =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "synaxis_joint_control");
    private static final String SYNAXIS_MOTOR =
            "com.verr1.synaxis.content.blocks.motor.AbstractDynamicMotorBlockEntity";
    private static final String SYNAXIS_LINEAR =
            "com.verr1.synaxis.content.blocks.slider.HydraulicLinearActuatorBlockEntity";
    private static final String SYNAXIS_COMPANION =
            "com.verr1.synaxis.foundation.blockentity.CompanionPhysicsBlockEntity";
    private static final String COASTER_RIVET =
            "dev.silvergold.simulatedcoasters.rivet.RivetBlockEntity";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the optional SCM compatibility bridge
    private OptionalScmCompatibility() {
    }

    // Register compatibility only for loaded optional mods
    public static void register() {
        if (ModList.get().isLoaded("synaxis")) {
            ScmSubLevelRelationRegistry.register(SYNAXIS_RELATIONS, 100,
                    OptionalScmCompatibility::synaxisRelations);
            ScmControlProbeRegistry.register(SYNAXIS_CONTROL, 100,
                    OptionalScmCompatibility::synaxisProbes);
        }
        if (ModList.get().isLoaded("simulatedcoasters")) {
            ScmSubLevelRelationRegistry.register(COASTER_RELATIONS, 100,
                    OptionalScmCompatibility::coasterRelations);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve Synaxis joints from their live paired physics bodies
    private static Collection<ScmSubLevelRelationRegistry.Relation> synaxisRelations(
            ScmSubLevelRelationRegistry.Context ctx
    ) {
        List<ScmSubLevelRelationRegistry.Relation> relations = new ArrayList<>();
        for (ScmSubLevelRelationRegistry.ScopedBlockEntity scoped : ctx.blockEntities()) {
            BlockEntity blockEntity = scoped.blockEntity();
            if (!isInstanceOf(blockEntity, SYNAXIS_COMPANION)) continue;
            Object resolvedBodies = optionalValue(invoke(blockEntity, "resolveBodies"));
            UUID self = bodyId(invoke(resolvedBodies, "selfBody"));
            UUID companion = bodyId(invoke(resolvedBodies, "companionBody"));
            if (self != null && companion != null) {
                relations.add(new ScmSubLevelRelationRegistry.Relation(
                        self, companion, "synaxis:joint", scoped.subLevelId(),
                        blockEntity.getBlockPos()));
                continue;
            }
            UUID expected = optionalUuid(invoke(blockEntity, "expectedCompanionUuid"));
            if (expected != null) {
                relations.add(new ScmSubLevelRelationRegistry.Relation(
                        scoped.subLevelId(), expected, "synaxis:joint", scoped.subLevelId(),
                        blockEntity.getBlockPos()));
            }
        }
        return relations;
    }

    // Resolve Coasters rivet sub-levels from their host keys
    private static Collection<ScmSubLevelRelationRegistry.Relation> coasterRelations(
            ScmSubLevelRelationRegistry.Context ctx
    ) {
        List<ScmSubLevelRelationRegistry.Relation> relations = new ArrayList<>();
        for (ScmSubLevelRelationRegistry.ScopedBlockEntity scoped : ctx.blockEntities()) {
            BlockEntity blockEntity = scoped.blockEntity();
            if (!isInstanceOf(blockEntity, COASTER_RIVET)) continue;
            Object host = invoke(blockEntity, "hostKey");
            UUID parent = uuid(invoke(host, "hostSubLevelId"));
            if (parent != null) {
                relations.add(new ScmSubLevelRelationRegistry.Relation(
                        parent, scoped.subLevelId(), "simulatedcoasters:rivet", scoped.subLevelId(),
                        blockEntity.getBlockPos()));
            }
        }
        return relations;
    }

    // Create the direct position probes used by Synaxis PID/PID-controlled joints
    private static List<ScmControlProbe> synaxisProbes(
            BlockEntity blockEntity, ScmControlProbeRegistry.Context ctx
    ) {
        if (isInstanceOf(blockEntity, SYNAXIS_MOTOR)) {
            return List.of(new SynaxisJointProbe(blockEntity, ctx.target(),
                    ctx.suggestedDirection(), false));
        }
        if (isInstanceOf(blockEntity, SYNAXIS_LINEAR)) {
            return List.of(new SynaxisJointProbe(blockEntity, ctx.target(),
                    ctx.suggestedDirection(), true));
        }
        return List.of();
    }

    // Check whether a runtime instance implements a class without loading that optional class here
    private static boolean isInstanceOf(Object value, String className) {
        if (value == null) return false;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (className.equals(type.getName())) return true;
            for (Class<?> iface : type.getInterfaces()) {
                if (className.equals(iface.getName())) return true;
            }
        }
        return false;
    }

    // Invoke a public no-argument optional method
    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // Invoke a public optional method with one primitive argument
    private static boolean invoke(Object target, String name, Class<?> parameter, Object value) {
        if (target == null) return false;
        try {
            target.getClass().getMethod(name, parameter).invoke(target, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Read one finite optional number
    private static double number(Object value, double fallback) {
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : fallback;
    }

    // Read one optional UUID
    private static UUID optionalUuid(Object value) {
        if (value instanceof Optional<?> optional) return uuid(optional.orElse(null));
        return uuid(value);
    }

    // Unwrap one optional compatibility return value
    private static Object optionalValue(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    // Read Synaxis's body identity without linking its optional BodyId type
    private static UUID bodyId(Object value) {
        return uuid(invoke(value, "id"));
    }

    // Read one UUID
    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : null;
    }

    // Handle one Synaxis PID/PID joint as a position controller
    private static final class SynaxisJointProbe implements ScmControlProbe {
        private final BlockEntity joint;
        private final ScmTarget target;
        private final Vec3 direction;
        private final boolean linear;
        private final boolean originalPositionMode;
        private final double originalTarget;
        private final double min;
        private final double max;

        // Initialize the Synaxis joint probe
        private SynaxisJointProbe(
                BlockEntity joint, ScmTarget target, Vec3 suggestedDirection, boolean linear
        ) {
            this.joint = joint;
            this.target = target;
            this.linear = linear;
            originalPositionMode = booleanValue(invoke(joint,
                    linear ? "positionMode" : "angleMode"), true);
            originalTarget = number(invoke(joint, "target"), 0.0D);
            double[] limits = limits(joint, linear, originalTarget);
            min = limits[0];
            max = limits[1];
            direction = effectDirection(joint, suggestedDirection);
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return linear ? "synaxis_linear_joint" : "synaxis_revolute_joint";
        }

        // Get the display name
        @Override
        public String displayName() {
            return linear ? "Synaxis Linear Joint" : "Synaxis Revolute Joint";
        }

        // Keep both face bindings for one Synaxis joint mutually exclusive
        @Override
        public String controlGroupId() {
            return "synaxis:" + target.blockStableId();
        }

        // Synaxis owns the joint PID/PID control loop, so generic redstone must not also drive it
        @Override
        public boolean suppressesFallbackProbes() {
            return true;
        }

        // Get the minimum position target
        @Override
        public double minControl() {
            return min;
        }

        // Get the maximum position target
        @Override
        public double maxControl() {
            return max;
        }

        // Return to the original joint target while the SCM is idle
        @Override
        public double neutralControl() {
            return Mth.clamp(originalTarget, min, max);
        }

        // Apply the target through Synaxis's own PID/PID controller
        @Override
        public void apply(double control) {
            if (!isAvailable()) return;
            invoke(joint, linear ? "setPositionMode" : "setAngleMode", boolean.class, true);
            invoke(joint, "setTarget", double.class, Mth.clamp(control, min, max));
        }

        // Read the current joint state
        @Override
        public Reading read() {
            double position = number(invoke(joint,
                    linear ? "currentDistance" : "currentAngle"), originalTarget);
            double speed = number(invoke(joint, "currentSpeed"), 0.0D);
            return new Reading(speed, position - originalTarget, isAvailable());
        }

        // Get the local direction the joint affects
        @Override
        public Vec3 localEffectDirection() {
            return direction;
        }

        // Get the local joint position
        @Override
        public Vec3 localEffectPosition() {
            return Vec3.atCenterOf(joint.getBlockPos());
        }

        // Check whether the joint is connected and still loaded
        @Override
        public boolean isAvailable() {
            return !joint.isRemoved() && booleanValue(invoke(joint, "connected"), true);
        }

        // Restore the target and active control mode from before SCM control
        @Override
        public void restore() {
            if (joint.isRemoved()) return;
            invoke(joint, "setTarget", double.class, originalTarget);
            invoke(joint, linear ? "setPositionMode" : "setAngleMode",
                    boolean.class, originalPositionMode);
        }

        // Get the Synaxis position limits or a bounded local working window
        private static double[] limits(BlockEntity joint, boolean linear, double center) {
            double low = number(invoke(joint,
                    linear ? "minExtension" : "jointLimitMin"), center - (linear ? 2.0D : Math.PI));
            double high = number(invoke(joint,
                    linear ? "maxExtension" : "jointLimitMax"), center + (linear ? 2.0D : Math.PI));
            boolean limited = linear ? high - low >= 1.0E-4D
                    : booleanValue(invoke(joint, "jointLimitEnabled"), false);
            if (!limited || high - low < 1.0E-4D) {
                low = center - (linear ? 2.0D : Math.PI);
                high = center + (linear ? 2.0D : Math.PI);
            }
            return new double[]{Math.min(low, high), Math.max(low, high)};
        }

        // Resolve a stable local effect direction
        private static Vec3 effectDirection(BlockEntity joint, Vec3 suggested) {
            Object axis = invoke(joint, "renderAxis");
            if (axis instanceof Direction direction) {
                return Vec3.atLowerCornerOf(direction.getNormal());
            }
            BlockState state = joint == null ? null : joint.getBlockState();
            if (state != null && state.hasProperty(BlockStateProperties.FACING)) {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                return Vec3.atLowerCornerOf(facing.getNormal());
            }
            if (suggested != null && suggested.lengthSqr() > 1.0E-9D) {
                return suggested.normalize();
            }
            return new Vec3(0.0D, 0.0D, 1.0D);
        }

        // Read one optional boolean
        private static boolean booleanValue(Object value, boolean fallback) {
            return value instanceof Boolean bool ? bool : fallback;
        }
    }
}
