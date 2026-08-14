package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.WheelMountControlBridge;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

// Route Wheel Mount Direct Control through the addon's direct-control API
@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity")
public abstract class WheelMountDirectControlMixin extends SmartBlockEntity
        implements IDirectControlReceiver, WheelMountControlBridge {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Last server steering signal
    @Shadow
    private int lastServerSteeringSignal;

    // Last server steering signal left
    @Shadow
    private int lastServerSteeringSignalLeft;

    // Last server steering signal right
    @Shadow
    private int lastServerSteeringSignalRight;

    // Current queued force pos
    @Shadow
    @Final
    private Vector3d queuedForcePos;

    // Current queued force
    @Shadow
    @Final
    private Vector3d queuedForce;

    // Current left override
    private float ct$leftOverride;
    // Current right override
    private float ct$rightOverride;
    // Current brake override
    private float ct$brakeOverride;
    // Current physical samples
    @Unique
    private long ct$physicalSamples;
    // Current cumulative impulse x
    @Unique
    private double ct$cumulativeImpulseX;
    // Current cumulative impulse y
    @Unique
    private double ct$cumulativeImpulseY;
    // Current cumulative impulse z
    @Unique
    private double ct$cumulativeImpulseZ;
    // Current physical sample position x
    @Unique
    private double ct$physicalSamplePositionX;
    // Current physical sample position y
    @Unique
    private double ct$physicalSamplePositionY;
    // Current physical sample position z
    @Unique
    private double ct$physicalSamplePositionZ;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the wheel mount direct control
    protected WheelMountDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        String channel = channelId == null ? "" : channelId.toLowerCase(Locale.ROOT);
        float clamped = Mth.clamp(val, 0.0f, 1.0f);

        if (channel.contains("yaw_left") || channel.contains("roll_left") || channel.contains("strafe_left")
                || channel.endsWith("_left") || channel.equals("left")) {
            ct$leftOverride = clamped;
        } else if (channel.contains("yaw_right") || channel.contains("roll_right") || channel.contains("strafe_right")
                || channel.endsWith("_right") || channel.equals("right")) {
            ct$rightOverride = clamped;
        } else if (channel.contains("brake") || channel.contains("throttle_down") || channel.contains("lift_down")
                || channel.equals("brake")) {
            ct$brakeOverride = clamped;
        }

        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }
    }

    // Inject the steering signal
    @Inject(method = "getSteeringSignal", at = @At("RETURN"), cancellable = true)
    private void ct$injectSteeringSignal(CallbackInfoReturnable<Integer> cir) {
        if (level == null || level.isClientSide) {
            return;
        }

        int left = Math.max(lastServerSteeringSignalLeft, ct$toRedstone(ct$leftOverride));
        int right = Math.max(lastServerSteeringSignalRight, ct$toRedstone(ct$rightOverride));
        int blended = left - right;

        boolean changed = blended != lastServerSteeringSignal
                || left != lastServerSteeringSignalLeft
                || right != lastServerSteeringSignalRight;

        lastServerSteeringSignal = blended;
        lastServerSteeringSignalLeft = left;
        lastServerSteeringSignalRight = right;

        if (changed) {

            sendData();
        }

        cir.setReturnValue(blended);
    }

        // Get the redirect tick brake signal
        @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
            ),
            require = 0
    )
    private int ct$redirectTickBrakeSignal(Level level, BlockPos pos, Direction direction) {
        int vanilla = level.getSignal(pos, direction);
        return Math.max(vanilla, ct$toRedstone(ct$brakeOverride));
    }

        // Get the redirect physics brake signal
        @Redirect(
            method = "sable$physicsTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
            ),
            require = 0
    )
    private int ct$redirectPhysicsBrakeSignal(Level level, BlockPos pos, Direction direction) {
        int vanilla = level.getSignal(pos, direction);
        return Math.max(vanilla, ct$toRedstone(ct$brakeOverride));
    }

    // Capture the physical wheel impulse
    @Inject(
            method = "sable$physicsTick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/force/ForceTotal;applyImpulseAtPoint(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            require = 0
    )
    private synchronized void ct$capturePhysicalWheelImpulse(
            ServerSubLevel subLevel,
            RigidBodyHandle handle,
            double timeStep,
            CallbackInfo ci
    ) {
        ct$physicalSamplePositionX = queuedForcePos.x;
        ct$physicalSamplePositionY = queuedForcePos.y;
        ct$physicalSamplePositionZ = queuedForcePos.z;
        ct$cumulativeImpulseX += queuedForce.x;
        ct$cumulativeImpulseY += queuedForce.y;
        ct$cumulativeImpulseZ += queuedForce.z;
        ct$physicalSamples++;
    }

    // Convert the wheel mount direct control to redstone
    private static int ct$toRedstone(float val) {
        return Mth.clamp(Mth.ceil(val * 15.0f), 0, 15);
    }

    // Get the left override
    @Override
    public float ct$getLeftOverride() {
        return ct$leftOverride;
    }

    // Get the right override
    @Override
    public float ct$getRightOverride() {
        return ct$rightOverride;
    }

    // Get the brake override
    @Override
    public float ct$getBrakeOverride() {
        return ct$brakeOverride;
    }

    // Get the effective steering signal
    @Override
    public int ct$getEffectiveSteeringSignal() {
        return lastServerSteeringSignal;
    }

    // Set the direct inputs
    @Override
    public void ct$setDirectInputs(float left, float right, float brake) {
        ct$leftOverride = Mth.clamp(left, 0.0f, 1.0f);
        ct$rightOverride = Mth.clamp(right, 0.0f, 1.0f);
        ct$brakeOverride = Mth.clamp(brake, 0.0f, 1.0f);
        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }
    }

    // Get the physical sample
    @Override
    public synchronized PhysicalSample ct$getPhysicalSample() {
        return new PhysicalSample(
                ct$physicalSamples,
                new Vec3(ct$physicalSamplePositionX,
                        ct$physicalSamplePositionY,
                        ct$physicalSamplePositionZ),
                new Vec3(ct$cumulativeImpulseX,
                        ct$cumulativeImpulseY,
                        ct$cumulativeImpulseZ));
    }
}
