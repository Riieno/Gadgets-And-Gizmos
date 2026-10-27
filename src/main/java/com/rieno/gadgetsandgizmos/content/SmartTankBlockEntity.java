package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerDataSource;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.lib.inventory.LocalResourceStorage;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.create.MultiFluidTankBlockEntity;
import com.rieno.gadgetsandgizmos.lib.inventory.FilteredFluidHandler;
import com.rieno.gadgetsandgizmos.lib.inventory.FluidExtractionPolicy;
import com.rieno.gadgetsandgizmos.lib.inventory.FuelResourceStorage;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider;
import com.rieno.gadgetsandgizmos.lib.probe.FluidStorageData;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

// Double Create's configured capacity for every block in a Smart Tank multiblock
public class SmartTankBlockEntity extends MultiFluidTankBlockEntity implements MountedTabletState.Host, LocalResourceStorage, FluidExtractionPolicy, FuelResourceStorage, BlockEntityDataProvider, ContainerDataSource{
    @Override public java.util.List<net.minecraft.world.level.block.entity.BlockEntity> dataContainers(){
        return level != null && level.isClientSide ? java.util.List.of(this) : ContainerStorageNetworks.connected(this);
    }

    private final ContainerNetworkAccess networkResources = new ContainerNetworkAccess(this);

    // Expose only the resource capability registered for this block's automation port
    public ContainerNetworkAccess networkResources(){ return networkResources; }

    @Override public ResourceRouting.Pool localResources(){
        return new ResourceRouting.Pool(java.util.List.of(), java.util.List.of(getFluidHandler()), java.util.List.of());
    }

    private String fuelFluid = "";
    private String pumpFluid = "";
    private final IFluidHandler fuelHandler = new FilteredFluidHandler(this::getFluidHandler,
            stack -> matches(fuelFluid(), stack));
    private final IFluidHandler bucketHandler = new FilteredFluidHandler(this::getFluidHandler, this::allowsFluidExtraction, false);

    public String fuelFluid(){ return root().fuelFluid; }
    public String pumpFluid(){ return root().pumpFluid; }
    private SmartTankBlockEntity root(){ return getControllerBE() instanceof SmartTankBlockEntity tank ? tank : this; }
    private static boolean matches(String selected, FluidStack stack){
        return selected.isEmpty() || !stack.isEmpty() && selected.equals(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
    }
    @Override public boolean allowsFluidExtraction(FluidStack stack){ return matches(pumpFluid(), stack); }
    @Override public ResourceRouting.Pool fuelResources(){
        return new ResourceRouting.Pool(java.util.List.of(), java.util.List.of(root().fuelHandler), java.util.List.of());
    }
    public IFluidHandler bucketHandler(){ return root().bucketHandler; }

    private final FluidStorageData graphData = new FluidStorageData(this::getFluidHandler,
            () -> getTankInventory().getCapacity(), () -> ContainerNetworkAccess.pool(this).fluids(),
            this::fuelFluid, this::pumpFluid, this::selectFluid, () -> level == null ? 0L : level.getGameTime());

    @Override public java.util.Map<String, String> graphReadableData(){ return graphData.graphReadableData(); }
    @Override public java.util.Map<String, String> graphWritableData(){ return graphData.graphWritableData(); }
    @Override public java.util.Map<String, java.util.List<String>> graphWritableOptions(){ return graphData.graphWritableOptions(); }
    @Override public long graphDataSchemaRevision(){ return graphData.graphDataSchemaRevision(); }
    @Override public GraphValue readGraphValue(String field){ return graphData.readGraphValue(field); }
    @Override public boolean writeGraphValue(String field, GraphValue value){
        if(level == null || level.isClientSide) return false;
        return graphData.writeGraphValue(field, value);
    }

    public void selectFluid(boolean fuel, String id){
        id = id == null ? "" : id;
        var selected = id.isBlank() ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        if(!id.isBlank() && (selected == null || !net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(selected)
                || net.minecraft.core.registries.BuiltInRegistries.FLUID.get(selected) == net.minecraft.world.level.material.Fluids.EMPTY)){
            throw new IllegalArgumentException("Choose a registered fluid");
        }
        for(var member : members()) if(member instanceof SmartTankBlockEntity tank){
            if(fuel) tank.fuelFluid = selected == null ? "" : selected.toString();
            else tank.pumpFluid = selected == null ? "" : selected.toString();
            tank.setChanged();
            tank.sendData();
        }
    }

    private final MountedTabletState tabletState = new MountedTabletState();

    @Override public MountedTabletState mountedTabletState(){ return tabletState; }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.read(tag, provider, clientPacket);
        tabletState.read(tag);
        fuelFluid = tag.getString("FuelFluid");
        pumpFluid = tag.getString("PumpFluid");
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.write(tag, provider, clientPacket);
        tabletState.write(tag);
        tag.putString("FuelFluid", fuelFluid);
        tag.putString("PumpFluid", pumpFluid);
    }

    // Initialize the smart tank block entity
    public SmartTankBlockEntity(BlockPos pos, BlockState state){
        super(CTBlockEntities.SMART_TANK.get(), pos, state);
    }

    @Override public void notifyMultiUpdated(){
        super.notifyMultiUpdated();
        var root = root();
        if(root != this){
            fuelFluid = root.fuelFluid;
            pumpFluid = root.pumpFluid;
            setChanged();
        }
    }

    @Override protected int capacityPerBlock(){ return getCapacityMultiplier() * 2; }

    // Keep the existing total capacity while allowing all fluid types to share it
    @Override public int getTankSize(int tank){ return capacityPerBlock(); }

    public IFluidHandler getFluidHandler(){ return getTankInventory(); }

    // Include decorative panels spanning the edge of their owning member
    @Override
    public AABB getRenderBoundingBox(){
        return super.getRenderBoundingBox().inflate(1.0D);
    }
}
