package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.lib.control.ThrusterControl;
import com.rieno.gadgetsandgizmos.lib.control.RcsThrusterControl;
import com.rieno.gadgetsandgizmos.lib.discovery.MountedBlockEntities;
import com.rieno.gadgetsandgizmos.lib.inventory.AssemblyResources;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityPosition;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// Supply native bearing targets and thruster adapters to the library
public final class BearingAssembly{
    private final BlockEntity be;
    private final Supplier<List<UUID>> bodyIds;
    private final Supplier<List<BlockPos>> fallbackPositions;
    private final AssemblyThrusterControl controls;
    private final AssemblyResources resources;

    public BearingAssembly(BlockEntity be, String type, Supplier<List<UUID>> bodyIds,
            Supplier<List<BlockPos>> fallbackPositions, Map<String, String> aliases){
        this.be = be;
        this.bodyIds = bodyIds;
        this.fallbackPositions = fallbackPositions;
        controls = new AssemblyThrusterControl(type,
                () -> thrusters().stream().map(val -> (ThrusterControl)new ThrusterAdapter(val)).toList(),
                () -> rcsThrusters().stream().map(val -> (RcsThrusterControl)new RcsAdapter(val)).toList(),
                aliases, be::setChanged);
        resources = new AssemblyResources(this::blocks, val -> val instanceof ThrusterBlockEntity,
                this::fluid, this::energy, OxidizedFuelStorageAccess::allow);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public AssemblyThrusterControl controls(){ return controls; }
    public AssemblyResources resources(){ return resources; }

    public List<BlockEntity> blocks(){
        if(be.isRemoved()) return List.of();
        return MountedBlockEntities.collect(be.getLevel(), bodyIds.get(), fallbackPositions.get());
    }

    public List<ThrusterBlockEntity> thrusters(){
        return blocks().stream().filter(ThrusterBlockEntity.class::isInstance).map(ThrusterBlockEntity.class::cast).toList();
    }

    public List<RcsThrusterBlockEntity> rcsThrusters(){
        return blocks().stream().filter(RcsThrusterBlockEntity.class::isInstance).map(RcsThrusterBlockEntity.class::cast).toList();
    }

    private IFluidHandler fluid(BlockEntity target){
        if(target instanceof ThrusterBlockEntity thruster) return thruster.getFuelTank();
        var handlers = ResourceRouting.access(target, null).fluids();
        return handlers.isEmpty() ? null : handlers.getFirst();
    }

    private IEnergyStorage energy(BlockEntity target){
        if(target instanceof ThrusterBlockEntity thruster) return thruster.isFocusedMode() ? thruster.getFocusInput() : null;
        var handlers = ResourceRouting.access(target, null).energy();
        return handlers.isEmpty() ? null : handlers.getFirst();
    }

    private record ThrusterAdapter(ThrusterBlockEntity target) implements ThrusterControl{
        @Override public BlockPos getBlockPos(){ return target.getBlockPos(); }
        @Override public Map<String, Object> position(){ return BlockEntityPosition.blockPosition(target); }
        @Override public String getCcId(){ return target.getCcId(); }
        @Override public void setCcId(String id){ target.setCcId(id); }
        @Override public String getAssemblyComputerCraftAlias(){ return target.getAssemblyComputerCraftAlias(); }
        @Override public void setAssemblyComputerCraftAlias(String alias){ target.setAssemblyComputerCraftAlias(alias); }
        @Override public float getThrottle(){ return target.getThrottle(); }
        @Override public float getComputerThrottle(){ return target.getComputerThrottle(); }
        @Override public void setThrottle(float throttle){ target.setThrottle(throttle); }
        @Override public void clearCcThrottleOverride(){ target.clearCcThrottleOverride(); }
        @Override public boolean isEnabled(){ return target.isEnabled(); }
        @Override public void setEnabled(boolean enabled){ target.setEnabled(enabled); }
        @Override public String getControlMode(){ return target.getControlMode().name().toLowerCase(Locale.ROOT); }
        @Override public void setControlMode(String mode){ target.setControlMode(ThrusterBlockEntity.ControlMode.valueOf(mode.toUpperCase(Locale.ROOT))); }
        @Override public int getFuelAmount(){ return target.getFuelAmount(); }
        @Override public int getFuelCapacity(){ return target.getFuelCapacity(); }
        @Override public String getFuelTypeId(){ return target.getFuelTypeId(); }
        @Override public double getEstimatedBurnSeconds(){ return target.getEstimatedBurnSeconds(); }
        @Override public double getThrust(){ return target.getThrust(); }
        @Override public double getRealThrust(){ return target.getRealThrust(); }
        @Override public double getLiftCapacity(){ return target.getLiftCapacity(); }
        @Override public double getAirflow(){ return target.getAirflow(); }
        @Override public boolean isActive(){ return target.isActive(); }
        @Override public boolean isSoulThruster(){ return target.isSoulThruster(); }
        @Override public void enableSoulThruster(){ target.enableSoulThruster(); }
        @Override public void disableSoulThruster(){ target.disableSoulThruster(); }
        @Override public int getSignalStrength(){ return target.getSignalStrength(); }
    }

    private record RcsAdapter(RcsThrusterBlockEntity target) implements RcsThrusterControl{
        @Override public BlockPos getBlockPos(){ return target.getBlockPos(); }
        @Override public Map<String, Object> position(){ return BlockEntityPosition.blockPosition(target); }
        @Override public String getCcId(){ return target.getCcId(); }
        @Override public void setCcId(String id){ target.setCcId(id); }
        @Override public String getAssemblyComputerCraftAlias(){ return target.getAssemblyComputerCraftAlias(); }
        @Override public void setAssemblyComputerCraftAlias(String alias){ target.setAssemblyComputerCraftAlias(alias); }
        @Override public float getSpeed(){ return target.getSpeed(); }
        @Override public double getMaxNozzleThrust(){ return target.getMaxNozzleThrust(); }
        @Override public float getThrottle(Direction nozzle){ return target.getThrottle(nozzle); }
        @Override public double getNozzleThrust(Direction nozzle){ return target.getNozzleThrust(nozzle); }
        @Override public boolean isNozzleActive(Direction nozzle){ return target.isNozzleActive(nozzle); }
        @Override public void setComputerThrottle(Direction nozzle, float throttle){ target.setComputerThrottle(nozzle, throttle); }
        @Override public void clearComputerThrottle(Direction nozzle){ target.clearComputerThrottle(nozzle); }
    }

}
