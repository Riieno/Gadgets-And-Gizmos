package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.scm.ScmVectorAllocationRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRemoteSessions;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;

// Own optional native runtime instances for real ACC block entities
public final class FlightControlIntegration{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<AdvancedContraptionControllerBlockEntity, FlightControlGraphRuntime> RUNTIMES =
            new ConcurrentHashMap<>();
    private static final Map<AdvancedContraptionControllerBlockEntity, CompoundTag> PENDING =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Map<AdvancedContraptionControllerBlockEntity, java.util.UUID> AUTHORS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static BiPredicate<AdvancedContraptionControllerBlockEntity, Player> clientInteraction = (host, player) -> false;

    private FlightControlIntegration(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void register(){
        FlightControlNodes.register();
        ScmVectorAllocationRegistry.register(ResourceLocation.fromNamespaceAndPath("createthrusters", "flight_control"),
                new FlightControlScmAllocator());
        com.rieno.gadgetsandgizmos.lib.scm.ScmWrenchSourceRegistry.register(
                ResourceLocation.fromNamespaceAndPath("createthrusters", "flight_control"), FlightControlRequests::sample);
    }

    public static void tick(AdvancedContraptionControllerBlockEntity host){
        if(host.getLevel() == null || host.getLevel().isClientSide) return;
        FlightControlGraphRuntime runtime = RUNTIMES.computeIfAbsent(host, FlightControlGraphRuntime::new);
        CompoundTag pending = PENDING.remove(host);
        if(pending != null) runtime.load(pending);
        runtime.tick();
    }

    public static FlightControlGraphRuntime runtime(AdvancedContraptionControllerBlockEntity host){
        FlightControlGraphRuntime runtime = RUNTIMES.get(host);
        if(runtime == null && host.getLevel() != null && host.getLevel().isClientSide && PENDING.containsKey(host)){
            runtime = new FlightControlGraphRuntime(host);
            runtime.load(PENDING.remove(host));
            RUNTIMES.put(host, runtime);
        }
        return runtime;
    }

    // Include synchronized hosts whose display runtime has not been requested by a block renderer
    public static java.util.List<AdvancedContraptionControllerBlockEntity> hosts(net.minecraft.world.level.Level level){
        var res = new java.util.LinkedHashSet<>(RUNTIMES.keySet());
        synchronized(PENDING){ res.addAll(PENDING.keySet()); }
        return res.stream().filter(host -> host.getLevel() == level && !host.isRemoved()).toList();
    }

    public static Map<String, GraphValue> outputs(AdvancedContraptionControllerBlockEntity host, String nodeId){
        FlightControlGraphRuntime runtime = RUNTIMES.get(host);
        return runtime == null ? Map.of("available", GraphValue.bool(false)) : runtime.outputs(nodeId);
    }

    public static void remove(AdvancedContraptionControllerBlockEntity host){
        FlightControlRequests.remove(host);
        FlightControlGraphRuntime runtime = RUNTIMES.remove(host);
        if(runtime != null) runtime.close();
        PENDING.remove(host);
        AUTHORS.remove(host);
    }

    // Retain native persistence until the unloaded host has been serialized
    public static void suspend(AdvancedContraptionControllerBlockEntity host){
        FlightControlGraphRuntime runtime = RUNTIMES.remove(host);
        if(runtime == null) return;
        PENDING.put(host, runtime.save());
        runtime.close();
    }

    public static void author(AdvancedContraptionControllerBlockEntity host, ServerPlayer player){ AUTHORS.put(host, player.getUUID()); }

    public static ServerPlayer author(AdvancedContraptionControllerBlockEntity host){
        var id = AUTHORS.get(host);
        var server = host.getLevel() == null ? null : host.getLevel().getServer();
        return id == null || server == null ? null : server.getPlayerList().getPlayer(id);
    }

    public static CompoundTag save(AdvancedContraptionControllerBlockEntity host){
        FlightControlGraphRuntime runtime = RUNTIMES.get(host);
        return runtime == null ? PENDING.getOrDefault(host, new CompoundTag()).copy() : runtime.save();
    }

    public static void load(AdvancedContraptionControllerBlockEntity host, CompoundTag tag){
        if(host.getLevel() != null && host.getLevel().isClientSide){
            FlightControlGraphRuntime runtime = RUNTIMES.get(host);
            if(runtime != null){ runtime.load(tag); return; }
        }
        PENDING.put(host, tag.copy());
    }

    public static void clientInteraction(BiPredicate<AdvancedContraptionControllerBlockEntity, Player> predicate){
        clientInteraction = predicate;
    }

    public static com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation orientation(AdvancedContraptionControllerBlockEntity host){
        return host.hasShipControlModule() ? host.getScmOrientation() : host.getScmDefaultOrientation();
    }

    // Resolve the native creative computer's embedded mouse terminal through its parent
    public static net.minecraft.world.level.block.entity.BlockEntity componentHost(net.minecraft.world.level.block.entity.BlockEntity component){
        if(component instanceof ace.flight.block.CreativeFlightTerminal terminal) component = terminal.host();
        var host = com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities.host(component);
        return host == null ? com.rieno.gadgetsandgizmos.lib.physics.BlockEntityBindings.host(component) : host;
    }

    // Follow native swivel ownership before deciding whether SCM may command an engine
    public static AdvancedContraptionControllerBlockEntity engineHost(net.minecraft.world.level.block.entity.BlockEntity component){
        var computer = engineComputer(component);
        return computer != null && componentHost(computer) instanceof AdvancedContraptionControllerBlockEntity host ? host : null;
    }

    public static ace.flight.block.FlightControlComputerBlockEntity engineComputer(net.minecraft.world.level.block.entity.BlockEntity component){
        if(!(component instanceof ace.flight.block.SmartVectorThrusterBlockEntity engine) || engine.getLevel() == null) return null;
        var pos = engine.getLinkedComputerPos();
        if(pos == null && engine.getLinkedSwivelMountPos() != null){
            var mount = engine.getLevel().getBlockEntity(engine.getLinkedSwivelMountPos());
            if(mount instanceof ace.flight.api.IFCCSwivelMount nativeMount) pos = nativeMount.getFCCMountLinkedComputerPos();
        }
        if(pos == null) return null;
        var computer = com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities.resolve(engine.getLevel(), pos);
        if(computer == null) computer = engine.getLevel().getBlockEntity(pos);
        return computer instanceof ace.flight.block.FlightControlComputerBlockEntity nativeComputer ? nativeComputer : null;
    }

    // Retain ACC permissions and the existing physical interaction participant check
    public static boolean isInteractionAuthorized(AdvancedContraptionControllerBlockEntity host, Player player){
        if(player == null || !player.isAlive() || player.isSpectator() || host.isRemoved()
                || host.getLevel() != player.level()) return false;
        if(player instanceof ServerPlayer serverPlayer){
            return host.hasPhysicalInteractionParticipant(player.getUUID()) && (host.canPlayerUse(player)
                    || DiagnosticTabletRemoteSessions.isInteractionAuthorized(serverPlayer, host.getBlockPos(), SableLevelApi.containingId(host)));
        }
        return player.level().isClientSide && clientInteraction.test(host, player);
    }
}
