package com.rieno.gadgetsandgizmos.compat.flightcontrol;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.ModList;

import java.util.Map;

// Keep optional native classes behind the loaded-mod boundary
public final class FlightControlCompatibility{
    private static boolean loaded;

    private FlightControlCompatibility(){}

    public static boolean nodeAvailable(AdvancedContraptionControllerBlockEntity host, String type){
        if(!loaded || host == null) return false;
        var runtime = FlightControlIntegration.runtime(host);
        return runtime != null && runtime.nodeAvailable(type);
    }

    public static void register(){
        loaded = ModList.get().isLoaded("create_flight_control");
        if(loaded) FlightControlIntegration.register();
    }

    public static void tick(AdvancedContraptionControllerBlockEntity controller){
        if(loaded) FlightControlIntegration.tick(controller);
    }

    public static Map<String, GraphValue> outputs(AdvancedContraptionControllerBlockEntity controller, String nodeId){
        return loaded ? FlightControlIntegration.outputs(controller, nodeId) : Map.of("available", GraphValue.bool(false));
    }

    public static boolean hasHostedEngineOwner(net.minecraft.world.level.block.entity.BlockEntity component){
        return loaded && FlightControlIntegration.engineHost(component) != null;
    }

    public static boolean hasHostedEngineOwner(net.minecraft.world.level.block.entity.BlockEntity component,
            AdvancedContraptionControllerBlockEntity host){
        return loaded && FlightControlIntegration.engineHost(component) == host;
    }

    public static void remove(AdvancedContraptionControllerBlockEntity controller){
        if(loaded) FlightControlIntegration.remove(controller);
    }

    public static void suspend(AdvancedContraptionControllerBlockEntity controller){
        if(loaded) FlightControlIntegration.suspend(controller);
    }

    public static void author(AdvancedContraptionControllerBlockEntity controller, net.minecraft.server.level.ServerPlayer player){
        if(loaded) FlightControlIntegration.author(controller, player);
    }

    public static CompoundTag save(AdvancedContraptionControllerBlockEntity controller){
        return loaded ? FlightControlIntegration.save(controller) : new CompoundTag();
    }

    public static void load(AdvancedContraptionControllerBlockEntity controller, CompoundTag tag){
        if(loaded) FlightControlIntegration.load(controller, tag);
    }

    public static void physicsTick(AdvancedContraptionControllerBlockEntity controller,
            dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel,
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double dt){
        if(!loaded) return;
        FlightControlGraphRuntime runtime = FlightControlIntegration.runtime(controller);
        if(runtime != null) runtime.physicsTick(subLevel, handle, dt);
    }
}
