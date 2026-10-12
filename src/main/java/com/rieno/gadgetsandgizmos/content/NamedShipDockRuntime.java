package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.shipping.NamedDockingOperation;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

// Adapt graph dock names to the shared berth scheduler and the SCM's existing movement commands
final class NamedShipDockRuntime implements NamedDockingOperation.Host<NamedShipDockRuntime.Target>{
    private final AdvancedContraptionControllerBlockEntity controller;
    private final ShipControlModuleRuntime runtime;
    private final NamedDockingOperation<Target> operation = new NamedDockingOperation<>(this, 600);
    private String commandId = "";
    private double speed = 0.35D;
    private long lastCommandTick = Long.MIN_VALUE;
    private NamedDockingOperation.Phase lastCommandPhase = NamedDockingOperation.Phase.IDLE;
    private ShipDockScheduler.RequestKey owner;
    private Target selected;

    NamedShipDockRuntime(AdvancedContraptionControllerBlockEntity controller, ShipControlModuleRuntime runtime){
        this.controller = controller;
        this.runtime = runtime;
    }

    boolean start(String id, String name, double requestedSpeed){
        String requested = name == null ? "" : name.trim();
        double nextSpeed = Double.isFinite(requestedSpeed) ? Math.max(0.01D, Math.min(1.0D, requestedSpeed)) : 0.35D;
        if(handles(id, "ship_dock") && requested.equalsIgnoreCase(operation.name())
                && (operation.pending() || operation.complete())){
            if(speed != nextSpeed){
                speed = nextSpeed;
                lastCommandTick = Long.MIN_VALUE;
            }
            return true;
        }
        // A graph movement request takes over from an installed schedule only after its destination is validated
        if(controller.getShipControlGraphValue("shipping_active").asBoolean()){
            if(resolve(requested).isEmpty() || !controller.executeShippingScheduleGraphCommand("shipping_pause")) return false;
        }
        if(!operation.start(requested)) return false;
        commandId = id;
        speed = nextSpeed;
        lastCommandTick = Long.MIN_VALUE;
        lastCommandPhase = NamedDockingOperation.Phase.IDLE;
        runtime.beginNamedDockMovement(movementId("approach"));
        operation.tick(tickTime());
        return operation.pending() || operation.complete();
    }

    void tick(){ operation.tick(tickTime()); }
    void cancel(){ operation.cancel(); }
    void cancelForMovement(){
        boolean docked = complete();
        operation.cancel();
        if(docked) controller.activateShipDockingConnector(-1);
    }
    boolean handles(String id, String type){ return "ship_dock".equals(type) && !commandId.isBlank() && commandId.equals(id); }
    boolean pending(){ return operation.pending(); }
    boolean complete(){ return operation.complete(); }
    AdvancedGraphDocument.Value value(String port){
        return switch(port){
            case "success" -> AdvancedGraphDocument.Value.bool(complete());
            case "progress" -> AdvancedGraphDocument.Value.string(operation.phase().name().toLowerCase(java.util.Locale.ROOT));
            case "progress_percent" -> AdvancedGraphDocument.Value.number(switch(operation.phase()){
                case NAVIGATING -> 25; case DOCKING -> 75; case COMPLETE -> 100; default -> 0;
            });
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    private ServerLevel level(){ return controller.getLevel() instanceof ServerLevel level ? level : null; }
    private long tickTime(){ return level() == null ? 0 : level().getGameTime(); }
    private String movementId(String suffix){ return "graph_dock:" + commandId + ":" + suffix; }

    @Override public Optional<Target> resolve(String name){
        ServerLevel level = level();
        if(level == null || name == null || name.isBlank()) return Optional.empty();
        List<Target> candidates = new ArrayList<>();
        for(ShipDockRegistry.Dock dock : ShipDockRegistry.get(level.getServer()).allIn(level.dimension().location())){
            if(!name.trim().equalsIgnoreCase(dock.name().trim())) continue;
            List<Choice> choices = choices(dock);
            if(!choices.isEmpty()) candidates.add(new Target(dock.id(), dock.name(), choices));
        }
        Vec3 origin = position();
        return NamedDockingOperation.findNamed(candidates, name, target -> target.name,
                target -> !target.choices.isEmpty(), target -> origin.distanceTo(target.choices.getFirst().dock.approach()));
    }

    private List<Choice> choices(ShipDockRegistry.Dock dock){
        List<Choice> choices = new ArrayList<>();
        for(ShipDockRegistry.ConnectorTarget connector : dock.connectorTargets()){
            ShipDockRegistry.Dock target = dock.withConnector(connector);
            if(!target.hasDockingConnector() || target.connectorUp() == null) continue;
            BlockEntity station = DockingConnectorAutomation.resolve(level(), target.connectorSubLevelId(), target.connectorPos());
            if(station == null) continue;
            var ship = controller.getShipDockingConnectors().stream().filter(candidate ->
                    DockingConnectorAutomation.isMagneticCapturePair(DockingConnectorAutomation.resolve(
                            level(), candidate.subLevelId(), candidate.blockPosition()), station)).findFirst().orElse(null);
            if(ship == null) ship = controller.selectShipDockingConnector(target.connectorFacing().scale(-1), target.dockingTarget());
            if(ship == null || DockingConnectorAutomation.resolve(level(), ship.subLevelId(), ship.blockPosition()) == null) continue;
            if(java.util.Objects.equals(ship.subLevelId(), target.connectorSubLevelId())) continue;
            choices.add(new Choice(target, ship.index()));
        }
        choices.sort(Comparator.comparingDouble(choice -> position().distanceTo(choice.dock.approach())));
        return choices;
    }

    @Override public boolean valid(Target target){
        ServerLevel level = level();
        if(level == null) return false;
        ShipDockRegistry.Dock current = ShipDockRegistry.get(level.getServer()).get(target.id);
        if(current == null || !target.name.equalsIgnoreCase(current.name())) return false;
        List<Choice> choices = choices(current);
        if(choices.isEmpty()) return false;
        if(target.choice != null){
            Choice previous = target.choice;
            var ship = controller.getShipDockingConnector(previous.shipIndex);
            Choice updated = ship == null ? null : choices.stream()
                    .filter(choice -> slot(choice.dock).equals(slot(previous.dock)))
                    .map(choice -> new Choice(choice.dock, previous.shipIndex)).findFirst().orElse(null);
            if(updated == null) return false;
            target.choice = updated;
        }
        target.choices = choices;
        return true;
    }

    @Override public NamedDockingOperation.Reservation reserve(Target target, boolean committed){
        ServerLevel level = level();
        if(level == null) return NamedDockingOperation.Reservation.INVALID;
        if(owner == null){
            String key = level.dimension().location() + "|" + SimulatedHelper.getContainingSubLevelId(controller)
                    + "|" + controller.getBlockPos().asLong();
            owner = new ShipDockScheduler.RequestKey(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), "scm_graph_dock");
        }
        selected = target;
        Set<ShipDockScheduler.DockSlot> occupied = new HashSet<>();
        for(ShipDockRegistry.Dock dock : ShipDockRegistry.get(level.getServer()).allIn(level.dimension().location())){
            for(ShipDockRegistry.ConnectorTarget connector : dock.connectorTargets()){
                ShipDockRegistry.Dock endpoint = dock.withConnector(connector);
                if(DockingConnectorAutomation.hasConnectionState(DockingConnectorAutomation.resolve(
                        level, endpoint.connectorSubLevelId(), endpoint.connectorPos()))) occupied.add(slot(endpoint));
            }
        }
        Choice paired = target.choices.stream().filter(this::paired).findFirst().orElse(null);
        if(target.choice == null && paired != null) target.choice = paired;
        ShipDockScheduler.DockSlot owned = paired == null ? null : slot(paired.dock);
        double distance = position().distanceTo(target.choices.getFirst().dock.approach());
        var envelope = controller.getShipEnvelope();
        var vessel = envelope == null ? new ShipDockScheduler.VesselEnvelope(1, 1, 0.5)
                : new ShipDockScheduler.VesselEnvelope(envelope.horizontalRadius(), envelope.height(), envelope.bottomOffset());
        var lease = ShipDockScheduler.get(level.getServer()).request(owner,
                committed && target.choice != null ? List.of(slot(target.choice.dock))
                        : target.choices.stream().map(choice -> slot(choice.dock)).toList(),
                occupied, owned, new ShipDockScheduler.RequestPriority(distance, -1, committed), vessel, tickTime());
        if(!lease.granted()) return NamedDockingOperation.Reservation.WAITING;
        var granted = new ShipDockScheduler.DockSlot(lease.dockId(), lease.connectorKey());
        Choice chosen = target.choices.stream().filter(choice -> slot(choice.dock).equals(granted)).findFirst().orElse(null);
        if(chosen == null) return NamedDockingOperation.Reservation.INVALID;
        if(target.choice != null && slot(target.choice.dock).equals(granted)){
            chosen = new Choice(chosen.dock, target.choice.shipIndex);
        }else{
            stopMovement();
        }
        target.choice = chosen;
        if(paired(chosen)) target.engaged = true;
        return NamedDockingOperation.Reservation.GRANTED;
    }

    private boolean paired(Choice choice){
        var ship = controller.getShipDockingConnector(choice.shipIndex);
        return ship != null && DockingConnectorAutomation.isMagneticCapturePair(
                DockingConnectorAutomation.resolve(level(), ship.subLevelId(), ship.blockPosition()),
                DockingConnectorAutomation.resolve(level(), choice.dock.connectorSubLevelId(), choice.dock.connectorPos()));
    }

    private static ShipDockScheduler.DockSlot slot(ShipDockRegistry.Dock dock){
        return new ShipDockScheduler.DockSlot(dock.id(), dock.dimension() + "|"
                + (dock.connectorSubLevelId() == null ? "world" : dock.connectorSubLevelId()) + "|" + dock.connectorPos().asLong());
    }

    @Override public boolean navigate(Target target){
        if(!target.departed){
            controller.activateShipDockingConnector(-1);
            target.engaged = false;
            target.departed = true;
        }
        return issue(target, false);
    }

    @Override public boolean approachReached(Target target){
        return runtime.isCommandComplete(movementId("approach"), "ship_navigate");
    }

    @Override public boolean dock(Target target){
        var ship = controller.getShipDockingConnector(target.choice.shipIndex);
        BlockEntity station = DockingConnectorAutomation.resolve(level(), target.choice.dock.connectorSubLevelId(), target.choice.dock.connectorPos());
        BlockEntity onboard = ship == null ? null : DockingConnectorAutomation.resolve(level(), ship.subLevelId(), ship.blockPosition());
        if(ship == null || station == null || onboard == null) return false;
        controller.activateShipDockingConnector(ship.index());
        target.engaged = DockingConnectorAutomation.engage(onboard, station);
        boolean capture = target.engaged && ship.worldTipPosition().distanceTo(target.choice.dock.dockingTarget()) <= 4
                && ship.worldFacing().dot(target.choice.dock.connectorFacing().scale(-1)) >= 0.5
                && DockingConnectorAutomation.isMagneticCapturePair(onboard, station);
        controller.setShipDockingMagneticCapture(ship.index(), capture);
        return target.engaged && (capture || issue(target, true));
    }

    private boolean issue(Target target, boolean docking){
        var phase = docking ? NamedDockingOperation.Phase.DOCKING : NamedDockingOperation.Phase.NAVIGATING;
        if(lastCommandPhase == phase && tickTime() - lastCommandTick < 5) return true;
        ShipDockRegistry.Dock dock = target.choice.dock;
        Vec3 destination = docking ? dock.dockingTarget() : dock.approach();
        Vec3 facing = dock.connectorFacing().scale(-1);
        Vec3 up = dock.connectorUp();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("x", destination.x); values.put("y", destination.y); values.put("z", destination.z);
        values.put("speed", docking ? Math.min(speed, 0.35D) : speed);
        values.put("speed_fraction", 1.0D);
        values.put("tolerance", docking ? 0.18D : 0.75D);
        values.put("avoid_collisions", 1.0D);
        values.put("target_direction_x", facing.x); values.put("target_direction_y", facing.y); values.put("target_direction_z", facing.z);
        values.put("target_up_x", up.x); values.put("target_up_y", up.y); values.put("target_up_z", up.z);
        Map<String, String> text = new LinkedHashMap<>();
        text.put("target_point", "docking_connector:" + target.choice.shipIndex);
        UUID body = dock.connectorSubLevelId() == null ? dock.subLevelId() : dock.connectorSubLevelId();
        if(body != null) text.put("dock_sub_level_id", body.toString());
        if(!runtime.execute(movementId(docking ? "capture" : "approach"), docking ? "ship_dock" : "ship_navigate", values, text)) return false;
        lastCommandPhase = phase;
        lastCommandTick = tickTime();
        return true;
    }

    @Override public boolean locked(Target target){
        if(target.choice == null) return false;
        var ship = controller.getShipDockingConnector(target.choice.shipIndex);
        return ship != null && DockingConnectorAutomation.isLockedPair(
                DockingConnectorAutomation.resolve(level(), ship.subLevelId(), ship.blockPosition()),
                DockingConnectorAutomation.resolve(level(), target.choice.dock.connectorSubLevelId(), target.choice.dock.connectorPos()));
    }

    @Override public void stopMovement(){
        runtime.cancelNamedDockMovement(movementId("approach"), movementId("capture"));
        lastCommandTick = Long.MIN_VALUE;
        lastCommandPhase = NamedDockingOperation.Phase.IDLE;
        if(selected != null && selected.choice != null) controller.setShipDockingMagneticCapture(selected.choice.shipIndex, false);
    }

    @Override public void release(Target target, boolean disconnect){
        if(disconnect && target.engaged && target.choice != null){
            var ship = controller.getShipDockingConnector(target.choice.shipIndex);
            BlockEntity onboard = ship == null ? null : DockingConnectorAutomation.resolve(
                    level(), ship.subLevelId(), ship.blockPosition());
            BlockEntity station = DockingConnectorAutomation.resolve(
                    level(), target.choice.dock.connectorSubLevelId(), target.choice.dock.connectorPos());
            DockingConnectorAutomation.disengage(onboard,
                    !DockingConnectorAutomation.hasConnectionState(station)
                            || DockingConnectorAutomation.isMagneticCapturePair(onboard, station) ? station : null);
            target.engaged = false;
        }
        if(owner != null && level() != null) ShipDockScheduler.get(level().getServer()).release(owner);
        if(disconnect) controller.activateShipDockingConnector(-1);
        selected = null;
    }

    private Vec3 position(){
        return new Vec3(controller.getShipControlGraphValue("x").asNumber(),
                controller.getShipControlGraphValue("y").asNumber(), controller.getShipControlGraphValue("z").asNumber());
    }

    static final class Target{
        private final UUID id;
        private final String name;
        private List<Choice> choices;
        private Choice choice;
        private boolean engaged;
        private boolean departed;
        private Target(UUID id, String name, List<Choice> choices){ this.id = id; this.name = name; this.choices = choices; }
    }
    private record Choice(ShipDockRegistry.Dock dock, int shipIndex){}
}
