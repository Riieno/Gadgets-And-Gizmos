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
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralSides;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection;
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
import java.util.LinkedHashSet;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Adapt ACC selections and CC:Tweaked 1.118 method bindings to the library hub
final class ComputerCraftPeripheralBridge{
    private static final List<WeakReference<ComputerCraftPeripheralBridge>> ACTIVE = new CopyOnWriteArrayList<>();
    private static boolean installed;
    private static final Map<AdvancedContraptionControllerBlockEntity, ComputerCraftPeripheralBridge> SHARED = new WeakHashMap<>();
    private final WeakReference<AdvancedContraptionControllerBlockEntity> controller;
    final ComputerCraftPeripheralHub hub;
    final Map<String, ControllerDiscoveryNode> selections = new LinkedHashMap<>();

    // Keep catalog names and all attached computers on the same controller-owned hub
    static synchronized ComputerCraftPeripheralBridge of(AdvancedContraptionControllerBlockEntity controller){
        var bridge = SHARED.get(controller);
        if(bridge == null){
            bridge = new ComputerCraftPeripheralBridge(controller); SHARED.put(controller, bridge); bridge.hub.refresh();
        }
        return bridge;
    }

    ComputerCraftPeripheralBridge(AdvancedContraptionControllerBlockEntity controller){
        this.controller = new WeakReference<>(controller);
        this.hub = new ComputerCraftPeripheralHub("acc_" + controller.getScmPersistenceId(), this::targets);
        ACTIVE.add(new WeakReference<>(this));
    }

    net.minecraft.world.level.block.entity.BlockEntity terminalTarget(String name){
        var controller = this.controller.get(); if(controller == null || controller.getLevel() == null) return null;
        var device = hub.catalog().stream().filter(val -> val.accepts(name)).findFirst().orElse(null);
        if(device == null) return null;
        for(String reference : device.references()){
            var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(controller.getLevel(), null, selections.get(reference));
            if(resolved == null) continue;
            var target = resolved.level().getBlockEntity(resolved.pos());
            if(target instanceof com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity display){ var root = display.networkRoot(); if(root != null) target = root; }
            if(target != null) return target;
        }
        return null;
    }

    static void install(){
        if(installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener(ComputerCraftPeripheralBridge::tick);
        NeoForge.EVENT_BUS.addListener(ComputerCraftPeripheralBridge::stopped);
    }

    private static synchronized void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent evt){
        SHARED.keySet().removeIf(controller -> controller.getLevel() != null && controller.getLevel().getServer() == evt.getServer());
    }

    private static void tick(ServerTickEvent.Post evt){
        if(evt.getServer().getTickCount() % 10 != 0) return;
        synchronized(ComputerCraftPeripheralBridge.class){ SHARED.keySet().removeIf(AdvancedContraptionControllerBlockEntity::isRemoved); }
        for(var ref : ACTIVE){
            var bridge = ref.get();
            if(bridge == null){
                ACTIVE.remove(ref);
            }else{
                var controller = bridge.controller.get();
                if(controller != null && controller.getLevel() != null
                        && controller.getLevel().getServer() == evt.getServer() && bridge.hub.hasComputers()) bridge.hub.refresh();
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
        var controller = this.controller.get();
        if(controller == null || controller.isRemoved() || !(controller.getLevel() instanceof ServerLevel)) return List.of();
        Map<String, ComputerCraftPeripheralHub.Target> targets = new LinkedHashMap<>();
        selections.clear();
        Map<String, ContraptionNetworkLinkerData.LinkedTarget> linkedTargets = new LinkedHashMap<>();
        controller.getLinkedTargets().forEach(target -> linkedTargets.put(ContraptionNetworkLinkerData.nodeIdForTarget(target), target));
        // Prefer current linker labels over older controller copies without expanding the allowed target set
        for(var target : ContraptionNetworkLinkerData.readTargets(controller.getStoredLinker())){
            String id = ContraptionNetworkLinkerData.nodeIdForTarget(target);
            if(linkedTargets.containsKey(id)) linkedTargets.put(id, target);
        }
        for(var linked : linkedTargets.values()){
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
        for(var neighbour : ScriptPeripheralSides.adjacent(controller.getLevel(), controller.getBlockPos(),
                controller.getBlockState().getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING))){
            var be = neighbour.target();
            var selected = new ControllerDiscoveryNode("adjacent:" + neighbour.alias(), com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.MACHINE, "",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString(), "",
                    com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.getContainingSubLevelId(be), be.getBlockPos());
            var data = new CompoundTag(); var faces = new CompoundTag(); var aliases = new CompoundTag();
            faces.putString(selected.nodeId(), neighbour.face().getSerializedName());
            aliases.putString(selected.nodeId(), neighbour.alias());
            data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces); data.put(GraphTargetSelection.ALIASES_TAG, aliases);
            add(targets, selection(data), selected);
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
        var controller = this.controller.get();
        if(controller == null) return;
        var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(
                controller.getLevel(), node, selected);
        if(resolved == null || !(resolved.level() instanceof ServerLevel level)) return;
        var be = level.getBlockEntity(resolved.pos());
        if(be == controller || be == null || be.isRemoved()) return;
        Direction side = resolved.side();
        IPeripheral peripheral = level.getCapability(PeripheralCapability.get(), resolved.pos(), side);
        if(peripheral == null) peripheral = Peripherals.getGenericPeripheral(level, resolved.pos(), side, be);
        if(peripheral == null) return;
        selections.putIfAbsent(selected.nodeId(), selected);
        var aliases = new LinkedHashSet<String>(); var references = new LinkedHashSet<String>();
        if(!selected.label().isBlank()) aliases.add(selected.label());
        references.add(selected.nodeId());
        if(node != null){
            String alias = node.data().getCompound(GraphTargetSelection.ALIASES_TAG).getString(selected.nodeId());
            if(!alias.isBlank()) aliases.add(alias);
        }
        for(var target : targets.values()){
            if(target.peripheral().equals(peripheral)){
                if(target.references().contains(selected.nodeId())){
                    aliases.remove(selected.label());
                    if(node != null){
                        String alias = node.data().getCompound(GraphTargetSelection.ALIASES_TAG).getString(selected.nodeId());
                        if(!alias.isBlank()) aliases.add(alias);
                    }
                }
                aliases.addAll(target.aliases()); references.addAll(target.references());
                targets.put(target.key(), new ComputerCraftPeripheralHub.Target(target.key(), target.peripheral(), target.methods(), aliases, references));
                return;
            }
        }
        String key = level.dimension().location() + "/" + resolved.pos().asLong() + "/" + side;
        if(targets.containsKey(key)) return;
        Map<String, ComputerCraftPeripheralHub.Method> methods = new LinkedHashMap<>();
        ServerContext.get(level.getServer()).peripheralMethods().getSelfMethods(peripheral)
                .forEach((name, method) -> methods.put(name,
                        (target, computer, ctx, args) -> method.apply(target, ctx, computer, args)));
        targets.put(key, new ComputerCraftPeripheralHub.Target(key, peripheral, methods, aliases, references));
    }

    private static AdvancedGraphDocument.Node selection(CompoundTag data){
        return new AdvancedGraphDocument.Node("", "", "", 0, 0, data);
    }
}
