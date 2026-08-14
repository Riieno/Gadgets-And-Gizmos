package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Implement the tablet's JourneyMap page and navigation actions
final class DiagnosticTabletJourneyApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet journey app
    private DiagnosticTabletJourneyApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet journey app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return TabletActionHandler.Result.failure(
                Component.literal("The tablet identity is not available"));
        String val = action.arguments().getOrDefault("value", "").strip();
        if ("journey_search".equals(action.actionId())) {
            String[] route = val.split("\\|", 2);
            if (route.length < 2 || route[0].isBlank() || route[1].isBlank()) {
                return TabletActionHandler.Result.failure(Component.literal(
                        "Choose an origin and destination"));
            }
            TabletStorageApi.storage().updateApp(
                    tabletId, DiagnosticTabletData.appId("journey"), data -> {
                        data.putString("From", route[0].strip());
                        data.putString("To", route[1].strip());
                        return data;
                    });
        } else if ("journey_save".equals(action.actionId())
                || "journey_remove".equals(action.actionId())) {
            TabletStorageApi.storage().updateApp(
                    tabletId, DiagnosticTabletData.appId("journey"), data -> {
                        ListTag saved = data.getList("Saved", net.minecraft.nbt.Tag.TAG_COMPOUND);
                        ListTag next = new ListTag();
                        for (int idx = 0; idx < saved.size(); idx++) {
                            CompoundTag entry = saved.getCompound(idx);
                            if (!entry.getString("Route").equals(val)) next.add(entry.copy());
                        }
                        if ("journey_save".equals(action.actionId()) && !val.isBlank()) {
                            CompoundTag entry = new CompoundTag();
                            entry.putString("Route", val);
                            next.add(entry);
                        }
                        data.put("Saved", next);
                        return data;
                    });
        } else if (!"refresh".equals(action.actionId())
                && !"select".equals(action.actionId())) {
            return TabletActionHandler.Result.failure(Component.literal("Unknown Journey action"));
        }
        CompoundTag snapshot = snapshot(ctx);
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("journey"), ctx, snapshot));
        return TabletActionHandler.Result.success(Component.empty());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the snapshot
    static CompoundTag snapshot(TabletActionContext ctx) {
        CompoundTag root = new CompoundTag();
        root.putLong("UpdatedAt", System.currentTimeMillis());
        root.putString("Dimension", ctx.player().level().dimension().location().toString());
        if (ctx.sourceTabletId() != null) {
            CompoundTag preferences = DiagnosticTabletAppStorage.data(ctx.player().server,
                    ctx.sourceTabletId(), DiagnosticTabletData.appId("journey"));
            root.putString("From", preferences.getString("From"));
            root.putString("To", preferences.getString("To"));
            root.put("Saved", preferences.getList("Saved", net.minecraft.nbt.Tag.TAG_COMPOUND).copy());
            root.put("Settings", preferences.getCompound("Settings").copy());
        }
        ShipDockRegistry registry = ShipDockRegistry.get(ctx.player().server);
        List<ShipDockRegistry.Dock> docks = registry.allIn(
                ctx.player().level().dimension().location());
        ListTag dockTags = new ListTag();
        List<Departure> departures = new ArrayList<>();
        for (ShipDockRegistry.Dock dock : docks) {
            CompoundTag dockTag = new CompoundTag();
            dockTag.putUUID("Id", dock.id());
            dockTag.putString("Name", dock.name());
            dockTag.putDouble("Distance", Math.sqrt(dock.worldPosition()
                    .distanceToSqr(ctx.player().position())));
            dockTag.putBoolean("Refuel", dock.refuel());
            dockTag.putBoolean("Restock", dock.restock());
            dockTag.putBoolean("Packages", dock.packages());
            dockTag.putInt("Connectors", dock.connectorTargets().size());
            List<ShipDockRegistry.ShipTelemetry> telemetry = registry.telemetry(dock.id());
            dockTag.putInt("Ships", telemetry.size());
            dockTags.add(dockTag);
            telemetry.forEach(ship -> departures.add(new Departure(dock.name(), ship)));
        }
        root.put("Docks", dockTags);
        ListTag stationTags = new ListTag();
        for (DiagnosticTabletJourneyTrainCompat.Station station
                : DiagnosticTabletJourneyTrainCompat.stations()) {
            CompoundTag stationTag = new CompoundTag();
            stationTag.putUUID("Id", station.id());
            stationTag.putString("Name", station.name());
            stationTags.add(stationTag);
        }
        root.put("TrainStations", stationTags);

        departures.sort(Comparator
                .comparingLong((Departure departure) -> normalizedEta(departure.ship().etaSeconds()))
                .thenComparing(departure -> departure.ship().shipName())
                .thenComparing(Departure::dock));
        ListTag departureTags = new ListTag();
        for (Departure departure : departures) {
            ShipDockRegistry.ShipTelemetry ship = departure.ship();
            CompoundTag tag = new CompoundTag();
            tag.putUUID("ShipId", ship.shipId());
            tag.putString("Ship", ship.shipName());
            tag.putString("Dock", departure.dock());
            tag.putString("CurrentStop", ship.currentStop());
            tag.putString("Destination", ship.targetName());
            tag.putString("NextStop", ship.nextStop());
            tag.putString("Status", ship.journeyStatus());
            tag.putString("Phase", ship.journeyPhase());
            tag.putLong("Eta", ship.etaSeconds());
            tag.putDouble("Distance", ship.distanceToTarget());
            tag.putDouble("Fuel", ship.fuelRatio());
            tag.putDouble("Progress", ship.progressPercent());
            departureTags.add(tag);
        }
        root.put("Departures", departureTags);
        String from = root.getString("From");
        String to = root.getString("To");
        root.put("Itinerary", itinerary(departures, from, to));
        return root;
    }

    // Get the itinerary
    private static ListTag itinerary(List<Departure> departures, String from, String to) {
        ListTag res = new ListTag();
        if (from.isBlank() || to.isBlank()) return res;
        Departure direct = departures.stream().filter(row -> startsAt(row, from)
                        && endsAt(row, to)).findFirst().orElse(null);
        if (direct != null) {
            res.add(step(direct, from, to));
            return res;
        }
        for (Departure first : departures) {
            if (!startsAt(first, from)) continue;
            String interchange = destination(first);
            Departure second = departures.stream().filter(row -> startsAt(row, interchange)
                    && endsAt(row, to)).findFirst().orElse(null);
            if (second == null) continue;
            res.add(step(first, from, interchange));
            res.add(step(second, interchange, to));
            break;
        }
        return res;
    }

    // Check if the journey starts at the place
    private static boolean startsAt(Departure row, String place) {
        return matches(row.dock(), place) || matches(row.ship().currentStop(), place);
    }

    // Check if the journey ends at the place
    private static boolean endsAt(Departure row, String place) {
        return matches(row.ship().targetName(), place) || matches(row.ship().nextStop(), place);
    }

    // Get the destination
    private static String destination(Departure row) {
        return row.ship().nextStop().isBlank() ? row.ship().targetName() : row.ship().nextStop();
    }

    // Check if this matches the value
    private static boolean matches(String val, String requested) {
        return val != null && requested != null && val.strip().equalsIgnoreCase(requested.strip());
    }

    // Get the step
    private static CompoundTag step(Departure departure, String from, String to) {
        CompoundTag step = new CompoundTag();
        step.putString("From", from);
        step.putString("To", to);
        step.putString("CurrentStop", from);
        step.putString("Destination", to);
        step.putString("Service", departure.ship().shipName());
        step.putString("Status", departure.ship().shipName() + "  "
                + departure.ship().journeyStatus());
        step.putLong("Eta", departure.ship().etaSeconds());
        return step;
    }

    // Get the normalized eta
    private static long normalizedEta(long eta) {
        return eta < 0L ? Long.MAX_VALUE : eta;
    }

    // Store the departure
    private record Departure(String dock, ShipDockRegistry.ShipTelemetry ship) {
    }
}
