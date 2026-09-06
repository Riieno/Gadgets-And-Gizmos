package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

// Produce configurable kinetic speed while keeping stress and direction changes safe
public class IndustrialMotorBlockEntity extends GeneratingKineticBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float REFERENCE_RPM = 256.0f;
    private static final int MIN_TARGET_SPEED_RPM = 1;
    private static final int DEFAULT_TARGET_SPEED_RPM = 128;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Energy storage
    private final InputOnlyEnergyStorage energyStorage = new InputOnlyEnergyStorage(
            CTConfigs.COMMON.electricMotorCapacity.get(),
            CTConfigs.COMMON.electricMotorMaxInput.get());
    // Capability
    private final IEnergyStorage capability = energyStorage;
    // Target speed behaviour
    private ScrollValueBehaviour targetSpeedBehaviour;

    // Tracks whether industrial motor is active
    private boolean active;
    // Tracks whether industrial motor is enabled
    private boolean enabled = true;
    // Current generated speed RPM
    private float generatedSpeedRpm;
    // Last consumption FE per tick
    private int lastConsumptionFePerTick;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the industrial motor
    public IndustrialMotorBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.INDUSTRIAL_MOTOR.get(), pos, state);
        setLazyTickRate(20);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        ValueBoxTransform backPanel = new CenteredSideValueBoxTransform((state, dir) ->
                dir == state.getValue(IndustrialMotorBlock.FACING).getOpposite());
        targetSpeedBehaviour = new ScrollValueBehaviour(
                Component.translatable("createthrusters.industrial_motor.target_rpm"),
                this,
                backPanel)
            .between(MIN_TARGET_SPEED_RPM, CTConfigs.COMMON.electricMotorRpmRange.get())
                .withFormatter(val -> val + " RPM")
                .withCallback(val -> {
                    if (!active) {
                        return;
                    }
                    generatedSpeedRpm = val;
                    updateGeneratedRotation();
                });
        targetSpeedBehaviour.setValue(DEFAULT_TARGET_SPEED_RPM);
        behaviours.add(targetSpeedBehaviour);
    }

    // Initialize the industrial motor
    @Override
    public void initialize() {
        super.initialize();
        if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed()) {
            updateGeneratedRotation();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the industrial motor
    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        float requestedSpeed = getConfiguredTargetSpeedRpm();
        int requiredFe = getEnergyConsumptionRate(requestedSpeed);

        energyStorage.ensureDynamicLimits(requiredFe);
        if (getBlockState().getBlock() instanceof IndustrialMotorBlock
                && getBlockState().getValue(IndustrialMotorBlock.POWERED) != enabled) {
            setMotorPowered(enabled);
        }

        boolean nextActive = enabled && requiredFe > 0 && energyStorage.getEnergyStored() >= requiredFe;
        if (nextActive) {
            int consumed = energyStorage.internalConsumeEnergy(requiredFe);
            nextActive = consumed >= requiredFe;
            if (nextActive) {
                lastConsumptionFePerTick = consumed;
            }
        } else {
            lastConsumptionFePerTick = 0;
        }

        float nextGeneratedSpeed = nextActive ? requestedSpeed : 0.0f;
        if (active != nextActive || Math.abs(generatedSpeedRpm - nextGeneratedSpeed) > 0.01f) {
            active = nextActive;
            generatedSpeedRpm = nextGeneratedSpeed;
            setMotorPowered(active);
            updateGeneratedRotation();
        }

        if (level.getGameTime() % 20L == 0L) {
            sendData();
            setChanged();
        }
    }

    // Get the generated speed
    @Override
    public float getGeneratedSpeed() {
        if (!(getBlockState().getBlock() instanceof IndustrialMotorBlock)) {
            return 0.0f;
        }
        Direction facing = getBlockState().getValue(IndustrialMotorBlock.FACING);
        return convertToDirection(generatedSpeedRpm, facing);
    }

    // Calculate the added stress capacity
    @Override
    public float calculateAddedStressCapacity() {
        float capacity = computeStressCapacityBase(Math.abs(getGeneratedSpeed()));
        lastCapacityProvided = capacity;
        return capacity;
    }

    // Get the energy storage
    public IEnergyStorage getEnergyStorage() {
        return capability;
    }

    // Toggle the industrial motor
    public void toggleEnabled() {
        setEnabled(!enabled);
    }

    // Set the enabled
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        setMotorPowered(enabled);
        if (!enabled) {
            lastConsumptionFePerTick = 0;
            active = false;
            generatedSpeedRpm = 0.0f;
            updateGeneratedRotation();
        }
        sendData();
        setChanged();
    }

    // Check if this is enabled
    public boolean isEnabled() {
        return enabled;
    }

    // Get the stress ratio
    public float getStressRatio() {
        if (!IRotate.StressImpact.isEnabled() || capacity <= 0.0f) {
            return 0.0f;
        }
        return overStressed ? 1.125f : Mth.clamp(stress / capacity, 0.0f, 1.125f);
    }

    // Get the energy consumption rate
    public static int getEnergyConsumptionRate(float rpm) {
        float speed = Math.abs(rpm);
        if (speed <= 0.0f) {
            return 0;
        }

        float consumption = CTConfigs.COMMON.feAtMaxRpm.get() * (speed / REFERENCE_RPM);
        return speed > 0.0f
                ? Math.max(Mth.ceil(consumption), CTConfigs.COMMON.electricMotorMinimumConsumption.get())
                : 0;
    }

    // Calculate the stress capacity base
    private static float computeStressCapacityBase(float rpm) {
        if (rpm <= 0.0f) {
            return 0.0f;
        }

        return CTConfigs.COMMON.maxStress.get() / REFERENCE_RPM;
    }

    // Get the configured target speed RPM
    private float getConfiguredTargetSpeedRpm() {
        if (targetSpeedBehaviour == null) {
            return DEFAULT_TARGET_SPEED_RPM;
        }
        return Mth.clamp(targetSpeedBehaviour.getValue(), MIN_TARGET_SPEED_RPM, CTConfigs.COMMON.electricMotorRpmRange.get());
    }

    // Expose speed to the ACC
    public float getTargetSpeedRPM(){
        return getConfiguredTargetSpeedRpm();
    }

    // ACC compatable speed SETTER

    public boolean setTargetSpeedRPM(double rpm){
        if(!Double.isFinite(rpm) || targetSpeedBehaviour == null) return false;
        int next = Mth.clamp((int) Math.round(rpm), MIN_TARGET_SPEED_RPM, CTConfigs.COMMON.electricMotorRpmRange.get());
        if(targetSpeedBehaviour.getValue() == next) return false;
        targetSpeedBehaviour.setValue(next);
        sendData();
        setChanged();
        return true;
    }

    // Set the motor powered
    private void setMotorPowered(boolean powered) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof IndustrialMotorBlock)) {
            return;
        }
        if (state.getValue(IndustrialMotorBlock.POWERED) == powered) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(IndustrialMotorBlock.POWERED, powered), 3);
    }

    // ACC compatable power SETTER
    private static boolean setPower(IndustrialMotorBlockEntity target, boolean enabled){
        if(target.isEnabled() == enabled) return false;
        target.setEnabled(enabled);
        return true;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.industrial_motor")));
        tooltip.add(CTTooltipHelper.line("Enabled", CTTooltipHelper.onOff(enabled)));
        tooltip.add(CTTooltipHelper.line("State", CTTooltipHelper.onOff(active)));
        tooltip.add(CTTooltipHelper.line("Speed",
                CTTooltipHelper.value(String.format("%.1f RPM", Math.abs(generatedSpeedRpm)), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line("Target",
            CTTooltipHelper.value(String.format("%.0f RPM", getConfiguredTargetSpeedRpm()), ChatFormatting.GREEN)));
        tooltip.add(CTTooltipHelper.line("Consumption",
                CTTooltipHelper.value(lastConsumptionFePerTick + " FE/t", ChatFormatting.GOLD)));
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line("Energy",
                    CTTooltipHelper.value(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored(),
                            ChatFormatting.BLUE)));
        }
        return true;
    }


    // Write the industrial motor safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putBoolean("Enabled", enabled);
    }

    // Write the industrial motor
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Active", active);
        tag.putFloat("GeneratedSpeed", generatedSpeedRpm);
        tag.putInt("Consumption", lastConsumptionFePerTick);
    }

    // Read the industrial motor
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        energyStorage.setStored(tag.getInt("Energy"));
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        active = tag.getBoolean("Active");
        generatedSpeedRpm = tag.contains("GeneratedSpeed") ? tag.getFloat("GeneratedSpeed") : 0.0f;
        lastConsumptionFePerTick = tag.contains("Consumption") ? tag.getInt("Consumption") : 0;
    }

    // Handle the input only energy storage
    private static final class InputOnlyEnergyStorage extends EnergyStorage {
        // Base capacity
        private final int baseCapacity;
        // Base max receive
        private final int baseMaxReceive;

        // Initialize the input only energy storage
        private InputOnlyEnergyStorage(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
            this.baseCapacity = capacity;
            this.baseMaxReceive = maxReceive;
        }

        // Check if this can extract
        @Override
        public boolean canExtract() {
            return false;
        }

        // Check if this can receive
        @Override
        public boolean canReceive() {
            return true;
        }

        // Get the internal consume energy
        private int internalConsumeEnergy(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int consumed = Math.min(energy, amount);
            energy -= consumed;
            return consumed;
        }

        // Ensure the dynamic limits
        private void ensureDynamicLimits(int requiredPerTick) {
            if (requiredPerTick <= 0) {
                return;
            }

            long targetReceive = Math.max((long) baseMaxReceive, (long) requiredPerTick);
            if (targetReceive > Integer.MAX_VALUE) {
                targetReceive = Integer.MAX_VALUE;
            }

            long targetCapacity = Math.max((long) baseCapacity, (long) requiredPerTick * 2L);
            if (targetCapacity > Integer.MAX_VALUE) {
                targetCapacity = Integer.MAX_VALUE;
            }

            this.maxReceive = (int) targetReceive;
            this.capacity = (int) targetCapacity;
            if (this.energy > this.capacity) {
                this.energy = this.capacity;
            }
        }

        // Set the stored
        private void setStored(int amount) {
            energy = Mth.clamp(amount, 0, capacity);
        }
    }
}
