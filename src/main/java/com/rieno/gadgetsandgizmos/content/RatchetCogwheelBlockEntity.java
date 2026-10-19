package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.kinetics.KineticConnectionFilter;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticGraphHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticSoundTiming;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticStressRelay;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticBlockEntity;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticPos;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticProvider;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTSoundEvents;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

// Drive either ratchet side independently and conditionally generate the opposite side
public class RatchetCogwheelBlockEntity extends GeneratingKineticBlockEntity implements VirtualKineticProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float MIN_CLICK_RPM = 1.0f;
    private static final float MAX_CLICK_RPM = 256.0f;
    private static final float SAMPLE_CLICK_RPM = 5.0f;
    private static final ClickTiming SEVEN_TOOTH_CLICK_TIMING = new ClickTiming(8.0f, 2.5f);
    private static final ClickTiming SIXTEEN_TOOTH_CLICK_TIMING = new ClickTiming(4.0f, 1.2f);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shaft kinetic node
    private final ShaftKineticBlockEntity shaft;
    // Virtual kinetic nodes
    private final List<KineticBlockEntity> virtualKinetics;
    // Stress relay
    private final KineticStressRelay stressRelay = new KineticStressRelay();

    // Current externally driven side
    private InputSide inputSide = InputSide.NONE;
    // Generated cog speed
    private float generatedCogSpeed;
    // Generated shaft speed
    private float generatedShaftSpeed;
    // Remaining ticks before the next blocked ratchet click
    private int clickDelay;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ratchet cogwheel block entity
    public RatchetCogwheelBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.RATCHET_COGWHEEL.get(), pos, state);
        shaft = new ShaftKineticBlockEntity(CTBlockEntities.RATCHET_COGWHEEL.get(),
                new VirtualKineticPos((Vec3i) pos, 0), state, this);
        virtualKinetics = List.of(shaft);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update both independent kinetic nodes
    @Override
    public void tick() {
        super.tick();
        shaft.tick();
        if (level == null || level.isClientSide) return;
        updateRelay();
    }

    // Get the generated cog speed
    @Override
    public float getGeneratedSpeed() {
        return generatedCogSpeed;
    }

    // Calculate the relayed cog capacity
    @Override
    public float calculateAddedStressCapacity() {
        return lastCapacityProvided = stressRelay.capacityProvidedBy(this);
    }

    // Calculate the relayed shaft-network stress
    @Override
    public float calculateStressApplied() {
        return lastStressApplied = stressRelay.stressAppliedBy(this);
    }

    // Get the virtual kinetic nodes
    @Override
    public List<KineticBlockEntity> ct$getVirtualKinetics() {
        return virtualKinetics;
    }

    // Get the shaft kinetic node
    public KineticBlockEntity getShaftKineticBlockEntity() {
        return shaft;
    }

    // Get the cog speed
    public float getCogSpeed() {
        return getSpeed();
    }

    // Get the shaft speed
    public float getShaftSpeed() {
        return shaft.getSpeed();
    }

    // Add standard diagonal neighbours for large cogwheel meshing
    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (!ICogWheel.isLargeCog(state)) return super.addPropagationLocations(block, state, neighbours);
        return KineticGraphHelper.addLargeCogwheelPropagationLocations(getBlockPos(), neighbours);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Helpers
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update which side is the independent input and generated output
    private void updateRelay() {
        InputSide nextInput = resolveInputSide();
        float inputSpeed = switch (nextInput) {
            case COG -> getTheoreticalSpeed();
            case SHAFT -> shaft.getTheoreticalSpeed();
            case NONE -> 0.0f;
        };
        float actualInputSpeed = switch (nextInput) {
            case COG -> getSpeed();
            case SHAFT -> shaft.getSpeed();
            case NONE -> 0.0f;
        };
        boolean engaged = !Mth.equal(inputSpeed, 0.0f) && allowsRotation(inputSpeed);
        float nextCogSpeed = engaged && nextInput == InputSide.SHAFT ? inputSpeed : 0.0f;
        float nextShaftSpeed = engaged && nextInput == InputSide.COG ? inputSpeed : 0.0f;
        boolean changed = inputSide != nextInput
                || !Mth.equal(generatedCogSpeed, nextCogSpeed)
                || !Mth.equal(generatedShaftSpeed, nextShaftSpeed);

        if (changed) {
            stressRelay.clear();
            inputSide = nextInput;
            generatedCogSpeed = nextCogSpeed;
            generatedShaftSpeed = nextShaftSpeed;
            updateGeneratedRotation();
            shaft.updateGeneratedRotation();
            configureStressRelay();
            setChanged();
            sendData();
        }
        stressRelay.refresh();
        tickRatchetSound(actualInputSpeed);
    }

    // Resolve the only externally powered side
    private InputSide resolveInputSide() {
        boolean cogPowered = hasSource();
        boolean shaftPowered = shaft.hasSource();
        if (cogPowered == shaftPowered) return InputSide.NONE;
        return cogPowered ? InputSide.COG : InputSide.SHAFT;
    }

    // Configure stress forwarding for the active generated output
    private void configureStressRelay() {
        if (!Mth.equal(generatedShaftSpeed, 0.0f)) {
            stressRelay.configure(this, shaft);
        } else if (!Mth.equal(generatedCogSpeed, 0.0f)) {
            stressRelay.configure(shaft, this);
        }
    }

    // Check if this rotation direction may pass through
    private boolean allowsRotation(float speed) {
        boolean reversed = getBlockState().getValue(RatchetCogwheelBlock.REVERSED);
        return reversed ? speed < 0.0f : speed > 0.0f;
    }

    // Play speed-timed clicks while the input turns against the gate
    private void tickRatchetSound(float inputSpeed) {
        if (Mth.equal(inputSpeed, 0.0f) || allowsRotation(inputSpeed)) {
            clickDelay = 0;
            return;
        }

        float rpm = Mth.clamp(Math.abs(inputSpeed), MIN_CLICK_RPM, MAX_CLICK_RPM);
        ClickTiming timing = ICogWheel.isLargeCog(getBlockState())
                ? SIXTEEN_TOOTH_CLICK_TIMING
                : SEVEN_TOOTH_CLICK_TIMING;
        int nextDelay = timing.delayTicks(rpm);
        clickDelay = Math.min(clickDelay, nextDelay);
        if (clickDelay > 0) clickDelay--;
        if (clickDelay > 0) return;

        level.playSound(null, worldPosition, CTSoundEvents.RATCHET_GEAR_CLICK.get(),
                SoundSource.BLOCKS, 0.75f, 1.0f);
        clickDelay = nextDelay;
    }

    // Hold the measured timing samples for one cogwheel variant
    private record ClickTiming(float oneRpmDelaySeconds, float fiveRpmDelaySeconds) {
        // Calculate the fitted delay at this speed
        private int delayTicks(float rpm) {
            return KineticSoundTiming.fittedReciprocalDelayTicks(rpm,
                    MIN_CLICK_RPM, oneRpmDelaySeconds,
                    SAMPLE_CLICK_RPM, fiveRpmDelaySeconds);
        }
    }

    // Identify the externally driven side
    private enum InputSide {
        NONE,
        COG,
        SHAFT
    }

    // Handle the shaft-side kinetic node
    private static final class ShaftKineticBlockEntity extends GeneratingKineticBlockEntity
            implements VirtualKineticBlockEntity, KineticConnectionFilter {
        // Parent ratchet cogwheel
        private final RatchetCogwheelBlockEntity parent;
        // Shaft rotation configuration
        private final IRotate rotationConfiguration;

        // Initialize the shaft kinetic node
        private ShaftKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                        RatchetCogwheelBlockEntity parent) {
            super(type, pos, state);
            this.parent = parent;
            rotationConfiguration = new IRotate() {
                // Check if the shaft is exposed on this face
                @Override
                public boolean hasShaftTowards(LevelReader level, BlockPos blockPos, BlockState blockState,
                                               Direction face) {
                    return face.getAxis() == blockState.getValue(BlockStateProperties.AXIS);
                }

                // Get the shaft rotation axis
                @Override
                public Direction.Axis getRotationAxis(BlockState blockState) {
                    return blockState.getValue(BlockStateProperties.AXIS);
                }
            };
        }

        // Get the generated shaft speed
        @Override
        public float getGeneratedSpeed() {
            return parent.generatedShaftSpeed;
        }

        // Calculate the relayed shaft capacity
        @Override
        public float calculateAddedStressCapacity() {
            return lastCapacityProvided = parent.stressRelay.capacityProvidedBy(this);
        }

        // Calculate the relayed cog-network stress
        @Override
        public float calculateStressApplied() {
            return lastStressApplied = parent.stressRelay.stressAppliedBy(this);
        }

        // Create a network id separate from the real cog node
        @Override
        public Long createNetworkId() {
            return parent.getBlockPos().asLong() ^ (1L << 56);
        }

        // Check if this is a physical shaft connection
        @Override
        public boolean allowsKineticConnection(KineticBlockEntity other, boolean outgoing) {
            BlockPos diff = other.getBlockPos().subtract(parent.getBlockPos());
            Direction.Axis axis = getBlockState().getValue(BlockStateProperties.AXIS);
            return diff.distManhattan(BlockPos.ZERO) == 1
                    && axis.choose(diff.getX(), diff.getY(), diff.getZ()) != 0;
        }

        // Get the virtual kinetic parent
        @Override
        public KineticBlockEntity ct$getVirtualKineticParent() {
            return parent;
        }

        // Get the virtual kinetic slot
        @Override
        public int ct$getVirtualKineticSlot() {
            return 0;
        }

        // Get the virtual shaft rotation configuration
        @Override
        public IRotate ct$getVirtualKineticRotationConfiguration() {
            return rotationConfiguration;
        }
    }
}
