package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.joml.Vector3f;

import java.util.List;

// Convert kinetic speed into Forge Energy and push it away from the shaft face
public class AlternatorBlockEntity extends KineticBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float REFERENCE_RPM = 256.0f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Energy storage
    private final OutputOnlyEnergyStorage energyStorage = new OutputOnlyEnergyStorage(
            CTConfigs.COMMON.alternatorCapacity.get(),
            CTConfigs.COMMON.alternatorMaxOutput.get());
    // Last generated FE per tick
    private int lastGeneratedFePerTick;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the alternator
    public AlternatorBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.ALTERNATOR.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the alternator
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            spawnClientArcParticles();
            return;
        }

        int storedBefore = energyStorage.getEnergyStored();
        int generated = getCurrentFePerTick();
        if (generated > 0) {
            energyStorage.internalProduceEnergy(generated);
        }
        pushEnergyToAdjacentConsumers();

        if (generated != lastGeneratedFePerTick || energyStorage.getEnergyStored() != storedBefore || level.getGameTime() % 20L == 0L) {
            lastGeneratedFePerTick = generated;
            sendData();
            setChanged();
        }
    }

    // Get the energy storage
    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    // Get the shaft face
    public Direction getShaftFace() {
        return getBlockState().getValue(AlternatorBlock.FACING);
    }

    // Check if this can extract energy from the source
    public boolean canExtractEnergyFrom(Direction side) {
        return side == null || side != getShaftFace();
    }

    // Get the current FE per tick
    public int getCurrentFePerTick() {
        float rpm = Math.abs(getSpeed());
        if (rpm <= 0.0f) {
            return 0;
        }

        double conversionRate = CTConfigs.COMMON.feAtMaxRpm.get();
        double efficiency = CTConfigs.COMMON.alternatorEfficiency.get();
        return (int) Math.floor(conversionRate * (rpm / REFERENCE_RPM) * efficiency);
    }

    // Calculate the stress applied
    @Override
    public float calculateStressApplied() {
        float applied = getStressImpactPerRpm();
        lastStressApplied = applied;
        return applied;
    }

    // Write the alternator
    @Override
    protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    // Read the alternator
    @Override
    protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        energyStorage.setStored(tag.getInt("Energy"));
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.alternator")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.alternator.rpm"),
                CTTooltipHelper.value(String.format("%.1f", Math.abs(getSpeed())), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.alternator.generation"),
                CTTooltipHelper.value(getCurrentFePerTick() + " FE/t", ChatFormatting.GOLD)));
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.alternator.energy"),
                    CTTooltipHelper.value(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored(),
                            ChatFormatting.BLUE)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.alternator.stress"),
                    CTTooltipHelper.value(String.format("%.1f SU", getCurrentStressImpact()), ChatFormatting.RED)));
        }
        return true;
    }

    // Get the stress impact per RPM
    private float getStressImpactPerRpm() {
        return CTConfigs.COMMON.maxStress.get() / REFERENCE_RPM;
    }

    // Get the current stress impact
    private float getCurrentStressImpact() {
        return Math.abs(getTheoreticalSpeed()) * getStressImpactPerRpm();
    }

    // Spawn the client arc particles
    private void spawnClientArcParticles() {
        if (level == null || !level.isClientSide) {
            return;
        }
        float rpm = Math.abs(getSpeed());
        if (rpm < 24.0f) {
            return;
        }

        float activity = Mth.clamp(rpm / REFERENCE_RPM, 0.0f, 1.0f);
        if (level.random.nextFloat() > 0.04f + activity * 0.16f) {
            return;
        }

        Direction.Axis axis = getShaftFace().getAxis();
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double axisOffset = (level.random.nextDouble() - 0.5D) * 0.38D;
        double firstOffset = (level.random.nextDouble() - 0.5D) * 0.52D;
        double secondOffset = (level.random.nextDouble() - 0.5D) * 0.52D;
        double x = center.x;
        double y = center.y;
        double z = center.z;
        switch (axis) {
            case X -> {
                x += axisOffset;
                y += firstOffset;
                z += secondOffset;
            }
            case Y -> {
                x += firstOffset;
                y += axisOffset;
                z += secondOffset;
            }
            case Z -> {
                x += firstOffset;
                y += secondOffset;
                z += axisOffset;
            }
        }

        double motion = 0.008D + activity * 0.018D;
        level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.36f + activity * 0.28f, 0.08f), 0.7f + activity * 0.35f),
                x, y, z,
                (level.random.nextDouble() - 0.5D) * motion,
                (level.random.nextDouble() - 0.5D) * motion,
                (level.random.nextDouble() - 0.5D) * motion);
    }

    // Push the energy to adjacent consumers
    private void pushEnergyToAdjacentConsumers() {
        if (level == null || energyStorage.getEnergyStored() <= 0) {
            return;
        }

        Direction shaftFace = getShaftFace();
        for (Direction side : Direction.values()) {
            if (side == shaftFace || energyStorage.getEnergyStored() <= 0) {
                continue;
            }

            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }

            int offered = target.receiveEnergy(energyStorage.getEnergyStored(), true);
            if (offered <= 0) {
                continue;
            }

            int extracted = energyStorage.extractEnergy(offered, false);
            if (extracted > 0) {
                target.receiveEnergy(extracted, false);
            }
        }
    }

    // Handle the output only energy storage
    private static final class OutputOnlyEnergyStorage extends EnergyStorage {
        // Initialize the output only energy storage
        private OutputOnlyEnergyStorage(int capacity, int maxExtract) {
            super(capacity, 0, maxExtract);
        }

        // Check if this can receive
        @Override
        public boolean canReceive() {
            return false;
        }

        // Check if this can extract
        @Override
        public boolean canExtract() {
            return true;
        }

        // Set the stored
        private void setStored(int amount) {
            this.energy = Mth.clamp(amount, 0, capacity);
        }

        // Get the internal produce energy
        private int internalProduceEnergy(int amount) {
            if (amount <= 0) {
                return 0;
            }
            int accepted = Math.min(capacity - energy, amount);
            energy += accepted;
            return accepted;
        }
    }
}
