package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftPeripheralHub;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.impl.Peripherals;
import dan200.computercraft.shared.computer.core.ServerContext;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

// Adapt ACC selections and CC:Tweaked 1.118 method bindings to the library hub
final class ComputerCraftPeripheralBridge{
    private static final List<WeakReference<ComputerCraftPeripheralBridge>> ACTIVE = new CopyOnWriteArrayList<>();
    private static boolean installed;
    private final AdvancedContraptionControllerBlockEntity controller;
    final ComputerCraftPeripheralHub hub;

    ComputerCraftPeripheralBridge(AdvancedContraptionControllerBlockEntity controller){
        this.controller = controller;
        this.hub = new ComputerCraftPeripheralHub("acc_" + controller.getScmPersistenceId(), this::targets);
        ACTIVE.add(new WeakReference<>(this));
    }

    static void install(){
        if(installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener(ComputerCraftPeripheralBridge::tick);
    }

    private static void tick(ServerTickEvent.Post evt){
        if(evt.getServer().getTickCount() % 10 != 0) return;
        for(var ref : ACTIVE){
            var bridge = ref.get();
            if(bridge == null){
                ACTIVE.remove(ref);
            }else if(bridge.controller.getLevel() != null
                    && bridge.controller.getLevel().getServer() == evt.getServer()
                    && bridge.hub.hasComputers()){
                bridge.hub.refresh();
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        TARGETS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Stored ACC targets and the inserted linker share one authoritative selection list
    private Collection<ComputerCraftPeripheralHub.Target> targets(){
        if(controller.isRemoved() || !(controller.getLevel() instanceof ServerLevel)) return List.of();
        Map<String, ComputerCraftPeripheralHub.Target> targets = new LinkedHashMap<>();
        for(var linked : controller.getLinkedTargets()){
            var target = new ControllerDiscoveryNode(ContraptionNetworkLinkerData.nodeIdForTarget(linked),
                    linked.mode().discoveryKind(), "", linked.blockId(), linked.label(),
                    linked.subLevelId(), linked.blockPos());
            if(linked.scope().usesFaces()){
                for(var face : linked.faces()){
                    CompoundTag data = new CompoundTag();
                    CompoundTag faces = new CompoundTag();
                    faces.putString(target.nodeId(), face.face().getSerializedName());
                    data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces);
                    add(targets, selection(data), target);
                }
            }else{
                add(targets, null, target);
            }
        }
        addGraph(targets, controller.getDraftGraph());
        addGraph(targets, controller.getActiveGraph());
        return targets.values();
    }

    private void addGraph(Map<String, ComputerCraftPeripheralHub.Target> targets, AdvancedGraphDocument graph){
        List<AdvancedGraphDocument.Node> nodes = new ArrayList<>(graph.nodes());
        for(var function : graph.functions()) nodes.addAll(function.nodes());
        for(var node : nodes){
            add(targets, node, ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData")));
            add(targets, node, ControllerDiscoveryNode.fromTag(node.data().getCompound("SourceData")));
            for(Tag tag : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)){
                add(targets, node, ControllerDiscoveryNode.fromTag((CompoundTag)tag));
            }
        }
    }

    // Use the same moving-block and linker-plane resolution as ACC Get/Set Data
    private void add(Map<String, ComputerCraftPeripheralHub.Target> targets, AdvancedGraphDocument.Node node,
                     ControllerDiscoveryNode selected){
        var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(
                controller.getLevel(), node, selected);
        if(resolved == null || !(resolved.level() instanceof ServerLevel level)) return;
        var be = level.getBlockEntity(resolved.pos());
        if(be == controller || be == null || be.isRemoved()) return;
        Direction side = resolved.side();
        IPeripheral peripheral = level.getCapability(PeripheralCapability.get(), resolved.pos(), side);
        if(peripheral == null) peripheral = Peripherals.getGenericPeripheral(level, resolved.pos(), side, be);
        if(peripheral == null) return;
        for(var target : targets.values()){
            if(target.peripheral().equals(peripheral)) return;
        }
        String key = level.dimension().location() + "/" + resolved.pos().asLong() + "/" + side;
        if(targets.containsKey(key)) return;
        Map<String, ComputerCraftPeripheralHub.Method> methods = new LinkedHashMap<>();
        ServerContext.get(level.getServer()).peripheralMethods().getSelfMethods(peripheral)
                .forEach((name, method) -> methods.put(name,
                        (target, computer, ctx, args) -> method.apply(target, ctx, computer, args)));
        targets.put(key, new ComputerCraftPeripheralHub.Target(key, peripheral, methods));
    }

    private static AdvancedGraphDocument.Node selection(CompoundTag data){
        return new AdvancedGraphDocument.Node("", "", "", 0, 0, data);
    }
}
