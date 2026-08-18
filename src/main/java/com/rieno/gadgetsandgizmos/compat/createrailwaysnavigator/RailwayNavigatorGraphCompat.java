package com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShipDockRegistry;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

// Feed ship schedule and dock state into Create Railways Navigator displays through guarded reflection
public final class RailwayNavigatorGraphCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DISPLAY_CLASS = "de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity";
    private static final String DISPLAY_TYPE_CLASS = "de.mrjulsen.crn.block.properties.EDisplayType";
    private static final String DISPLAY_KEY_CLASS = "de.mrjulsen.crn.client.AdvancedDisplaysRegistry$DisplayTypeResourceKey";
    private static final String DISPLAY_REGISTRY_CLASS = "de.mrjulsen.crn.client.AdvancedDisplaysRegistry";
    private static final String DISPLAY_SETTINGS_CLASS = "de.mrjulsen.crn.block.display.properties.IDisplaySettings";
    private static final String DISPLAY_GETTER_CLASS = "de.mrjulsen.crn.block.IBlockGetter";
    private static final String DISPLAY_WORLD_GETTER_CLASS = "de.mrjulsen.crn.block.IBlockGetter$WorldBlockGetter";
    private static final String STATION_DISPLAY_DATA_CLASS =
            "de.mrjulsen.crn.data.train.portable.StationDisplayData";
    private static final String BASIC_TRAIN_DISPLAY_DATA_CLASS =
            "de.mrjulsen.crn.data.train.portable.BasicTrainDisplayData";
    private static final String TRAIN_DISPLAY_DATA_CLASS =
            "de.mrjulsen.crn.data.train.portable.TrainDisplayData";
    private static final String STATION_INFO_CLASS =
            "de.mrjulsen.crn.data.StationTag$StationInfo";
    private static final String SHIP_TRAIN_DATA_TAG = "CreateThrustersShipTrainData";
    private static final Map<Object, CompoundTag> SHIP_TRAIN_DATA =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, DisplaySnapshot> INITIALIZATION_DISPLAY_SNAPSHOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the railway navigator graph compat
    private RailwayNavigatorGraphCompat() {
    }

    // Store only the portable ship data needed by CRN's train information displays
    public record ShipDisplayData(
            UUID shipId,
            String shipName,
            String scheduleTitle,
            boolean scheduleActive,
            boolean pilotPresent,
            boolean docked,
            boolean waiting,
            String status,
            String phase,
            String currentStop,
            String targetStop,
            String nextStop,
            int currentEntry,
            int nextEntry,
            int stopCount,
            int remainingStops,
            long etaSeconds,
            double cruiseSpeed,
            double fuelRatio,
            double progressPercent,
            double distanceToTarget,
            net.minecraft.world.phys.Vec3 position,
            net.minecraft.world.phys.Vec3 targetPosition
    ) {
        // Initialize the ship display data
        public ShipDisplayData {
            shipId = shipId == null ? new UUID(0L, 0L) : shipId;
            shipName = usableName(shipName, "Unnamed Ship");
            scheduleTitle = scheduleTitle == null ? "" : scheduleTitle;
            status = usableName(status, "Awaiting shipping schedule");
            phase = usableName(phase, "idle");
            currentStop = usableName(currentStop, "At sea");
            targetStop = targetStop == null ? "" : targetStop;
            nextStop = nextStop == null ? "" : nextStop;
            currentEntry = Math.max(0, currentEntry);
            nextEntry = Math.max(0, nextEntry);
            stopCount = Math.max(0, stopCount);
            remainingStops = Math.max(0, remainingStops);
            etaSeconds = Math.max(-1L, etaSeconds);
            cruiseSpeed = Math.max(0.0D, cruiseSpeed);
            fuelRatio = Math.max(0.0D, Math.min(1.0D, fuelRatio));
            progressPercent = Math.max(0.0D, Math.min(100.0D, progressPercent));
            distanceToTarget = Math.max(0.0D, distanceToTarget);
            position = position == null ? net.minecraft.world.phys.Vec3.ZERO : position;
            targetPosition = targetPosition == null
                    ? net.minecraft.world.phys.Vec3.ZERO : targetPosition;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ship display frame
    public static CompoundTag shipDisplayFrame(ShipDisplayData telemetry) {
        CompoundTag frame = new CompoundTag();
        if (telemetry == null) {
            return frame;
        }
        frame.putUUID("ShipId", telemetry.shipId());
        frame.putString("ShipName", telemetry.shipName());
        frame.putString("ScheduleTitle", telemetry.scheduleTitle());
        frame.putBoolean("ScheduleActive", telemetry.scheduleActive());
        frame.putBoolean("PilotPresent", telemetry.pilotPresent());
        frame.putBoolean("Docked", telemetry.docked());
        frame.putBoolean("Waiting", telemetry.waiting());
        frame.putString("Status", telemetry.status());
        frame.putString("Phase", telemetry.phase());
        frame.putString("CurrentStop", telemetry.currentStop());
        frame.putString("TargetStop", telemetry.targetStop());
        frame.putString("NextStop", telemetry.nextStop());
        frame.putInt("CurrentEntry", telemetry.currentEntry());
        frame.putInt("NextEntry", telemetry.nextEntry());
        frame.putInt("StopCount", telemetry.stopCount());
        frame.putInt("RemainingStops", telemetry.remainingStops());
        frame.putLong("EtaSeconds", telemetry.etaSeconds());
        frame.putDouble("CruiseSpeed", telemetry.cruiseSpeed());
        frame.putDouble("FuelRatio", telemetry.fuelRatio());
        frame.putDouble("ProgressPercent", telemetry.progressPercent());
        frame.putDouble("DistanceToTarget", telemetry.distanceToTarget());
        putVec3(frame, "Position", telemetry.position());
        putVec3(frame, "TargetPosition", telemetry.targetPosition());
        return frame;
    }

    // Write a position vector
    private static void putVec3(CompoundTag target, String key,
                                net.minecraft.world.phys.Vec3 val) {
        CompoundTag vector = new CompoundTag();
        vector.putDouble("X", val.x);
        vector.putDouble("Y", val.y);
        vector.putDouble("Z", val.z);
        target.put(key, vector);
    }

    // Get the readable data
    public static Map<String, String> readableData(BlockEntity blockEntity) {
        Object display = displayController(blockEntity);
        if (display == null) return Map.of();
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("display_text", "string");
        ports.put("display_lines", "list");
        ports.put("display_settings", "map");
        return ports;
    }

    // Get the writable data
    public static Map<String, String> writableData(BlockEntity blockEntity) {
        Object display = displayController(blockEntity);
        if (display == null) return Map.of();
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("display_text", "string");
        ports.put("display_lines", "list");
        return ports;
    }

    // Get the writable options
    public static CompoundTag writableOptions(BlockEntity blockEntity) {
        return new CompoundTag();
    }

    // Use CRN's rich text renderer for Ship Dock text and apply it to the complete panel
    public static boolean prepareShipDockPanel(BlockEntity blockEntity) {
        Object display = displayController(blockEntity);
        if (display == null) return false;
        if (hasMethod(settings(display), "getComponents")) return true;

        Object richSettings = staticTextSettings(display, true);
        if (!hasMethod(richSettings, "getComponents")) return false;
        notifyDisplay(display);

        Consumer<Object> configurePanelPart = panelPart -> {
            Object before = settings(panelPart);
            Object after = staticTextSettings(panelPart, true);
            if (after != before && hasMethod(after, "getComponents")) {
                notifyDisplay(panelPart);
            }
        };
        Object applied = invoke(display, "applyToAll", new Class<?>[]{Consumer.class}, configurePanelPart);
        if (applied == null) {
            notifyDisplay(display);
        }
        return true;
    }

    // Send Ship Dock predictions through CRN's own format so its selected renderer stays intact
    public static boolean populateShipDockPredictions(DisplayLinkContext ctx) {
        if (ctx == null || !(ctx.getSourceBlockEntity() instanceof ShipDockBlockEntity dock)) {
            return false;
        }
        BlockEntity target = ctx.getTargetBlockEntity();
        Object display = displayController(target);
        if (display == null) {
            return false;
        }

        try {
            long gameTime = target.getLevel() == null ? 0L : target.getLevel().getDayTime();
            List<Object> predictions = new ArrayList<>();
            List<ShipDockRegistry.ShipTelemetry> telemetry = dock.getShippingTelemetry();
            for (int idx = 0; idx < telemetry.size() && idx < 50; idx++) {
                predictions.add(shipPrediction(
                        telemetry.get(idx), dock.getDockName(), dock.getDockId(), idx, gameTime));
            }
            Object stationInfo = Class.forName(STATION_INFO_CLASS)
                    .getConstructor(String.class).newInstance("Ship");
            Method setData = publicMethod(display, "setData", 4);
            if (setData == null) {
                return false;
            }
            setData.invoke(display, predictions, dock.getDockName(), stationInfo, gameTime);
            notifyDisplay(display);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Find only the CRN controller block so the SCM stores one stable address per display
    public static boolean isAdvancedDisplayController(BlockEntity blockEntity) {
        return blockEntity != null && !blockEntity.isRemoved()
                && DISPLAY_CLASS.equals(blockEntity.getClass().getName())
                && Boolean.TRUE.equals(invoke(blockEntity, "isController", new Class<?>[0]));
    }

    // Show initialization through CRN's renderer and restore the full panel when it finishes
    public static boolean publishShipInitializationProgress(
            BlockEntity blockEntity, boolean visible, int percent, String status
    ) {
        Object display = displayController(blockEntity);
        if (display == null) {
            return false;
        }
        Consumer<Object> updatePart = part -> {
            if (visible) {
                showInitOnDisplay(part, percent, status);
            } else {
                restoreInitDisplay(part);
            }
        };
        invoke(display, "applyToAll", new Class<?>[]{Consumer.class}, updatePart);
        updatePart.accept(display);
        return true;
    }

    // Publish the ship schedule and pilot data through CRN's normal portable train format
    public static boolean publishShipTelemetry(
            BlockEntity blockEntity,
            ShipDisplayData telemetry
    ) {
        Object display = displayController(blockEntity);
        if (display == null || telemetry == null) {
            return false;
        }
        if (!telemetry.scheduleActive() || !telemetry.pilotPresent()) {
            return false;
        }
        try {
            long gameTime = blockEntity.getLevel() == null
                    ? 0L : blockEntity.getLevel().getDayTime();
            CompoundTag trainDataTag = shipTrainDisplayData(telemetry, gameTime);
            Object trainData = Class.forName(TRAIN_DISPLAY_DATA_CLASS)
                    .getMethod("fromNbt", CompoundTag.class).invoke(null, trainDataTag);
            if (!setPrivateField(display, "trainData", trainData)) {
                return false;
            }

            List<Object> predictions = shipPredictions(telemetry, gameTime);
            Object stationInfo = Class.forName(STATION_INFO_CLASS)
                    .getConstructor(String.class).newInstance("Ship");
            Method setData = publicMethod(display, "setData", 4);
            if (setData == null) {
                return false;
            }
            setData.invoke(display, predictions, telemetry.currentStop(), stationInfo, gameTime);
            SHIP_TRAIN_DATA.put(display, trainDataTag.copy());
            notifyDisplay(display);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Add portable ship data to CRN's normal display sync and save payload
    public static void writeShipTelemetry(BlockEntity blockEntity, CompoundTag tag) {
        if (blockEntity == null || tag == null) {
            return;
        }
        CompoundTag data = SHIP_TRAIN_DATA.get(blockEntity);
        if (data != null) {
            tag.put(SHIP_TRAIN_DATA_TAG, data.copy());
        }
    }

    // Restore the extra ship data which CRN leaves out when it reads a synced display
    public static void readShipTelemetry(BlockEntity blockEntity, CompoundTag tag) {
        if (blockEntity == null || tag == null || !tag.contains(SHIP_TRAIN_DATA_TAG)) {
            return;
        }
        try {
            CompoundTag data = tag.getCompound(SHIP_TRAIN_DATA_TAG).copy();
            Object trainData = Class.forName(TRAIN_DISPLAY_DATA_CLASS)
                    .getMethod("fromNbt", CompoundTag.class).invoke(null, data);
            if (!setPrivateField(blockEntity, "trainData", trainData)) {
                return;
            }
            SHIP_TRAIN_DATA.put(blockEntity, data);
            refreshDisplayRenderer(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // CRN is optional and can change this portable data API between releases
        }
    }

    // Get the ship predictions
    private static List<Object> shipPredictions(ShipDisplayData telemetry, long gameTime)
            throws ReflectiveOperationException {
        List<Object> predictions = new ArrayList<>();
        if (!telemetry.scheduleActive() || !telemetry.pilotPresent()) {
            return predictions;
        }
        String station = telemetry.targetStop().isBlank()
                ? telemetry.currentStop() : telemetry.targetStop();
        CompoundTag prediction = new CompoundTag();
        prediction.put("Train", shipTrainData(telemetry.shipId(), telemetry.shipName()));
        prediction.put("Station", shipScheduleStopData(
                telemetry, station, telemetry.nextStop(), telemetry.currentEntry(), gameTime,
                telemetry.waiting() || telemetry.docked()));
        prediction.putString("FirstStop", telemetry.currentStop());
        prediction.putBoolean("IsFirst", false);
        prediction.putBoolean("IsLast", telemetry.nextStop().isBlank());
        prediction.putBoolean("IsNextSectionExcluded", telemetry.nextStop().isBlank());
        prediction.putBoolean("IsPrevSectionExcluded", false);
        ListTag stopovers = new ListTag();
        if (!telemetry.nextStop().isBlank()
                && !telemetry.nextStop().equals(telemetry.targetStop())) {
            stopovers.add(StringTag.valueOf(telemetry.nextStop()));
        }
        prediction.put("Stopovers", stopovers);
        prediction.putInt("State", telemetry.waiting() || telemetry.docked() ? 2 : 1);
        predictions.add(Class.forName(STATION_DISPLAY_DATA_CLASS)
                .getMethod("fromNbt", CompoundTag.class).invoke(null, prediction));
        return predictions;
    }

    // Get the ship train display data
    private static CompoundTag shipTrainDisplayData(ShipDisplayData telemetry, long gameTime)
            throws ReflectiveOperationException {
        CompoundTag data = new CompoundTag();
        data.put("Train", shipTrainData(telemetry.shipId(), telemetry.shipName()));
        ListTag stops = new ListTag();
        boolean inService = telemetry.scheduleActive() && telemetry.pilotPresent();
        boolean atDock = inService && (telemetry.waiting() || telemetry.docked());
        int currentIndex = telemetry.currentEntry();

        if (!inService) {
            stops.add(shipScheduleStopData(
                    telemetry, telemetry.currentStop(), "", 0, gameTime, true));
            currentIndex = 0;
        } else if (atDock) {
            stops.add(shipScheduleStopData(
                    telemetry, telemetry.currentStop(), telemetry.targetStop(),
                    currentIndex, gameTime, true));
            if (!telemetry.nextStop().isBlank()) {
                stops.add(shipScheduleStopData(
                        telemetry, telemetry.nextStop(), "", Math.max(currentIndex + 1,
                        telemetry.nextEntry()), gameTime, false));
            }
        } else {
            stops.add(shipScheduleStopData(
                    telemetry, telemetry.currentStop(), telemetry.targetStop(),
                    currentIndex - 1, gameTime, true));
            stops.add(shipScheduleStopData(
                    telemetry, usableName(telemetry.targetStop(), "Awaiting route"),
                    telemetry.nextStop(), currentIndex, gameTime, false));
            if (!telemetry.nextStop().isBlank()) {
                stops.add(shipScheduleStopData(
                        telemetry, telemetry.nextStop(), "", Math.max(currentIndex + 1,
                        telemetry.nextEntry()), gameTime, false));
            }
        }
        data.put("Stops", stops);
        data.putInt("CurrentIndex", currentIndex);
        data.putDouble("Speed", telemetry.cruiseSpeed());
        data.putBoolean("Opposite", false);
        data.putByte("ExitSide", (byte) 0);
        data.putBoolean("AtStation", atDock || !inService);
        data.putInt("State", inService ? 0 : 1);
        return data;
    }

    // Get the ship schedule stop data
    private static CompoundTag shipScheduleStopData(
            ShipDisplayData telemetry,
            String station,
            String destination,
            int idx,
            long gameTime,
            boolean waiting
    ) {
        CompoundTag data = new CompoundTag();
        String resolvedStation = usableName(station, "At sea");
        CompoundTag stationTag = shipStationTag(resolvedStation,
                UUID.nameUUIDFromBytes(resolvedStation.getBytes(StandardCharsets.UTF_8)));
        long etaTicks = waiting || telemetry.etaSeconds() < 0L ? 0L
                : Math.min(telemetry.etaSeconds(), Long.MAX_VALUE / 20L) * 20L;
        long arrival = safeAdd(gameTime, etaTicks);
        long departure = waiting ? gameTime : arrival;
        data.putInt("Index", idx);
        data.put("ScheduledStation", stationTag.copy());
        data.put("RealTimeStation", stationTag);
        data.putLong("ScheduledDeparture", departure);
        data.putLong("ScheduledArrival", arrival);
        data.putLong("RealTimeArrival", departure);
        data.putLong("RealTimeDeparture", arrival);
        data.putString("Destination", destination == null ? "" : destination);
        data.putString("TrainName", telemetry.shipName());
        return data;
    }

    // Get the ship prediction
    private static Object shipPrediction(
            ShipDockRegistry.ShipTelemetry telemetry,
            String dockName,
            UUID dockId,
            int idx,
            long gameTime
    ) throws ReflectiveOperationException {
        CompoundTag prediction = new CompoundTag();
        prediction.put("Train", shipTrainData(telemetry.shipId(), telemetry.shipName()));
        prediction.put("Station", shipStopData(
                telemetry, dockName, dockId, idx, gameTime));
        prediction.putString("FirstStop", telemetry.currentStop());
        prediction.putBoolean("IsFirst", false);
        prediction.putBoolean("IsLast", false);
        prediction.putBoolean("IsNextSectionExcluded", false);
        prediction.putBoolean("IsPrevSectionExcluded", false);
        ListTag stopovers = new ListTag();
        if (!telemetry.nextStop().isBlank() && !telemetry.nextStop().equals(telemetry.targetName())) {
            stopovers.add(StringTag.valueOf(telemetry.nextStop()));
        }
        prediction.put("Stopovers", stopovers);
        prediction.putInt("State", shipIsWaiting(telemetry) ? 2 : 1);
        return Class.forName(STATION_DISPLAY_DATA_CLASS)
                .getMethod("fromNbt", CompoundTag.class).invoke(null, prediction);
    }

    // Get the ship train data
    private static CompoundTag shipTrainData(UUID shipId, String shipName)
            throws ReflectiveOperationException {
        Class<?> basicData = Class.forName(BASIC_TRAIN_DISPLAY_DATA_CLASS);
        Object empty = basicData.getMethod("empty", int.class).invoke(null, 0);
        CompoundTag data = (CompoundTag) basicData.getMethod("toNbt").invoke(empty);
        UUID id = shipId == null ? new UUID(0L, 0L) : shipId;
        data.putUUID("Id", id);
        data.putBoolean("Cancelled", false);
        CompoundTag stateData = new CompoundTag();
        int colour = 0xFF000000 | (id.hashCode() & 0x00FFFFFF);
        for (int state = 0; state <= 1; state++) {
            CompoundTag stateEntry = new CompoundTag();
            stateEntry.putString("DisplayName", shipName);
            stateEntry.putUUID("LineId", new UUID(0L, 0L));
            stateEntry.putUUID("CategoryId", new UUID(0L, 0L));
            stateEntry.putInt("Color", colour);
            stateData.put(Integer.toString(state), stateEntry);
        }
        data.put("StateData", stateData);
        return data;
    }

    // Get the ship stop data
    static CompoundTag shipStopData(
            ShipDockRegistry.ShipTelemetry telemetry,
            String dockName,
            UUID dockId,
            int idx,
            long gameTime
    ) {
        CompoundTag data = new CompoundTag();
        String station = dockName == null || dockName.isBlank() ? "Ship Dock" : dockName;
        UUID stationId = dockId == null
                ? UUID.nameUUIDFromBytes(station.getBytes(StandardCharsets.UTF_8)) : dockId;
        CompoundTag stationTag = shipStationTag(station, stationId);
        long etaTicks = telemetry.etaSeconds() < 0L ? 0L
                : Math.min(telemetry.etaSeconds(), Long.MAX_VALUE / 20L) * 20L;
        long arrival = safeAdd(gameTime, etaTicks);
        long departure = shipIsWaiting(telemetry) ? gameTime : arrival;
        data.putInt("Index", idx);
        data.put("ScheduledStation", stationTag.copy());
        data.put("RealTimeStation", stationTag);
        data.putLong("ScheduledDeparture", departure);
        data.putLong("ScheduledArrival", arrival);
        data.putLong("RealTimeArrival", departure);
        data.putLong("RealTimeDeparture", arrival);
        data.putString("Destination", telemetry.targetName());
        data.putString("TrainName", telemetry.shipName());
        return data;
    }

    // Get the ship station tag
    private static CompoundTag shipStationTag(String station, UUID stationId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("TagName", station);
        tag.putString("StationName", station);
        CompoundTag info = new CompoundTag();
        info.putString("Platform", "Ship");
        tag.put("StationInfo", info);
        tag.putUUID("Id", stationId);
        return tag;
    }

    // Check if the ship is waiting
    private static boolean shipIsWaiting(ShipDockRegistry.ShipTelemetry telemetry) {
        String phase = telemetry.journeyPhase().toLowerCase(java.util.Locale.ROOT);
        return telemetry.etaSeconds() == 0L
                || phase.contains("wait") || phase.contains("dock") || phase.contains("hold");
    }

    // Get the safe add
    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    // Read the railway navigator graph compat
    public static AdvancedGraphDocument.Value read(BlockEntity blockEntity, String port) {
        Object display = displayController(blockEntity);
        if (display == null) return null;
        Object settings = settings(display);
        if ("display_settings".equals(port)) return AdvancedGraphDocument.Value.map(serialize(settings));
        if ("display_text".equals(port)) return AdvancedGraphDocument.Value.string(text(settings));
        if ("display_lines".equals(port)) return AdvancedGraphDocument.Value.list(lines(settings));
        return null;
    }

    // Write the railway navigator graph compat
    public static boolean write(BlockEntity blockEntity, String port, AdvancedGraphDocument.Value val) {
        if (val == null || !writableData(blockEntity).containsKey(port)) {
            return false;
        }
        Object display = displayController(blockEntity);
        if (display == null) return false;
        Object settings = settings(display);
        boolean changed;
        if ("display_text".equals(port)) {
            settings = staticTextSettings(display, false);
            changed = setText(settings, val.asString());
        } else if ("display_lines".equals(port)) {
            settings = staticTextSettings(display, true);
            changed = setLines(settings, val.payload());
        } else {
            return false;
        }
        if (changed) notifyDisplay(display);
        return changed;
    }

    // Get the display controller
    private static Object displayController(BlockEntity blockEntity) {
        if (blockEntity == null || !DISPLAY_CLASS.equals(blockEntity.getClass().getName())) return null;
        Level level = blockEntity.getLevel();
        if (level == null) return blockEntity;
        try {
            Class<?> getterClass = Class.forName(DISPLAY_GETTER_CLASS);
            Class<?> worldGetterClass = Class.forName(DISPLAY_WORLD_GETTER_CLASS);
            Object worldGetter = worldGetterClass.getConstructor(Level.class).newInstance(level);
            Object controller = invoke(blockEntity, "getController", new Class<?>[]{getterClass}, worldGetter);
            return controller == null ? blockEntity : controller;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return blockEntity;
        }
    }

    // Set the private field
    private static boolean setPrivateField(Object target, String name, Object val) {
        if (target == null) {
            return false;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, val);
                return true;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    // Refresh the display renderer
    private static void refreshDisplayRenderer(Object display) {
        if (!(display instanceof BlockEntity blockEntity)
                || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide) {
            return;
        }
        try {
            Object renderer = invoke(display, "getRenderer", new Class<?>[0]);
            if (renderer == null) {
                return;
            }
            Class<?> reasonClass = Class.forName(DISPLAY_CLASS + "$EUpdateReason");
            Object reason = java.util.Arrays.stream(reasonClass.getEnumConstants())
                    .filter(val -> "DATA_CHANGED".equals(((Enum<?>) val).name()))
                    .findFirst().orElse(null);
            if (reason == null) {
                return;
            }
            for (Method method : renderer.getClass().getMethods()) {
                if (!method.getName().equals("update") || method.getParameterCount() != 5) {
                    continue;
                }
                method.invoke(renderer, blockEntity.getLevel(), blockEntity.getBlockPos(),
                        blockEntity.getBlockState(), blockEntity, reason);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The display may finish loading between the SCM update and its client sync
        }
    }

    // Get the usable name
    private static String usableName(String val, String fallback) {
        return val == null || val.isBlank() ? fallback : val;
    }

    // Get the settings
    private static Object settings(Object display) {
        return invoke(display, "getSettings", new Class<?>[0]);
    }

    // Show the init on display
    private static void showInitOnDisplay(
            Object display, int percent, String status
    ) {
        if (display == null) {
            return;
        }
        Object currentSettings = settings(display);
        Object currentType = invoke(display, "getDisplayType", new Class<?>[0]);
        if (currentSettings == null || currentType == null) {
            return;
        }
        boolean firstUpdate;
        synchronized (INITIALIZATION_DISPLAY_SNAPSHOTS) {
            firstUpdate = !INITIALIZATION_DISPLAY_SNAPSHOTS.containsKey(display);
            INITIALIZATION_DISPLAY_SNAPSHOTS.putIfAbsent(
                    display, new DisplaySnapshot(currentType, serialize(currentSettings)));
        }
        Object richSettings = firstUpdate
                ? createStaticTextSettings(display, true)
                : staticTextSettings(display, true);
        if (!hasMethod(richSettings, "getComponents")) {
            return;
        }
        int safePercent = Math.max(0, Math.min(100, percent));
        int segments = initBarSegments(display);
        int filled = Math.max(0, Math.min(
                segments, (safePercent * segments + 50) / 100));
        CompoundTag lines = new CompoundTag();
        putStringValue(lines, 0, richText(
                "Initializing ship controls: " + safePercent + "%", "gold", true));
        putStringValue(lines, 1, richProgressBar(filled, segments - filled));
        String normalizedStatus = status == null ? "" : status.strip();
        if (!normalizedStatus.isEmpty()) {
            putStringValue(lines, 2, richText(normalizedStatus.substring(
                    0, Math.min(96, normalizedStatus.length())), "gray", false));
        }
        setLines(richSettings, lines);
        configInitRichText(display, richSettings);
        notifyDisplay(display);
    }

    // Initialize the bar segments
    private static int initBarSegments(Object display) {
        double width = displayMetric(display, "getXSizeScaled", 4.0D) * 16.0D - 6.0D;
        return Math.max(6, Math.min(20, (int) Math.floor(width / 4.0D) - 2));
    }

    // Configure the init rich text
    private static void configInitRichText(Object display, Object settings) {
        Object components = invoke(settings, "getComponents", new Class<?>[0]);
        if (!(components instanceof List<?> list)) {
            return;
        }
        double width = Math.max(10.0D,
                displayMetric(display, "getXSizeScaled", 4.0D) * 16.0D - 6.0D);
        double height = Math.max(10.0D,
                displayMetric(display, "getYSizeScaled", 1.0D) * 16.0D - 6.0D);
        boolean compact = height < 20.0D;
        float scale = compact ? 0.5F : 0.7F;
        float[] rows = compact
                ? new float[]{0.0F, 5.0F, 12.0F}
                : new float[]{0.0F, 7.5F, 15.0F};
        for (int idx = 0; idx < list.size() && idx < rows.length; idx++) {
            configInitComponent(
                    list.get(idx), scale, rows[idx], (float) width);
        }
    }

    // Configure the init component
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void configInitComponent(
            Object component, float scale, float y, float width
    ) {
        invoke(component, "setX", new Class<?>[]{float.class}, 0.0F);
        invoke(component, "setY", new Class<?>[]{float.class}, y);
        invoke(component, "setXScale", new Class<?>[]{float.class}, scale);
        invoke(component, "setYScale", new Class<?>[]{float.class}, scale);
        invoke(component, "setMinXScale", new Class<?>[]{float.class}, 0.3F);
        invoke(component, "setTextMaxWidth", new Class<?>[]{float.class}, width);
        Object bounds = invoke(component, "getBoundsAction", new Class<?>[0]);
        if (bounds instanceof Enum<?> val) {
            Object cutOff = Enum.valueOf((Class<? extends Enum>) val.getDeclaringClass(), "CUT_OFF");
            invoke(component, "setBoundsAction",
                    new Class<?>[]{val.getDeclaringClass()}, cutOff);
        }
    }

    // Get the display metric
    private static double displayMetric(Object display, String methodName, double fallback) {
        Object val = invoke(display, methodName, new Class<?>[0]);
        return val instanceof Number num && Double.isFinite(num.doubleValue())
                ? Math.max(1.0D, num.doubleValue()) : fallback;
    }

    // Get the rich progress bar
    private static String richProgressBar(int filled, int empty) {
        return "{\"text\":\"[\",\"color\":\"dark_gray\",\"extra\":["
                + "{\"text\":\"" + "=".repeat(Math.max(0, filled))
                + "\",\"color\":\"green\"},"
                + "{\"text\":\"" + "-".repeat(Math.max(0, empty))
                + "\",\"color\":\"dark_gray\"},"
                + "{\"text\":\"]\",\"color\":\"dark_gray\"}]}";
    }

    // Get the rich text
    private static String richText(String text, String col, boolean bold) {
        return "{\"text\":\"" + escapeJson(text) + "\",\"color\":\""
                + col + "\",\"bold\":" + bold + "}";
    }

    // Get the escape JSON
    private static String escapeJson(String val) {
        StringBuilder escaped = new StringBuilder(val == null ? 0 : val.length());
        if (val == null) {
            return "";
        }
        for (int idx = 0; idx < val.length(); idx++) {
            char character = val.charAt(idx);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    // Restore the init display
    private static void restoreInitDisplay(Object display) {
        DisplaySnapshot snapshot = INITIALIZATION_DISPLAY_SNAPSHOTS.remove(display);
        if (display == null || snapshot == null) {
            return;
        }
        try {
            Class<?> keyClass = Class.forName(DISPLAY_KEY_CLASS);
            Class<?> settingsClass = Class.forName(DISPLAY_SETTINGS_CLASS);
            Object restoredSettings = Class.forName(DISPLAY_REGISTRY_CLASS)
                    .getMethod("createSettings", keyClass)
                    .invoke(null, snapshot.displayType());
            invoke(restoredSettings, "deserializeNbt",
                    new Class<?>[]{CompoundTag.class}, snapshot.settings().copy());
            display.getClass().getMethod("setDisplayType", keyClass, settingsClass)
                    .invoke(display, snapshot.displayType(), restoredSettings);
            notifyDisplay(display);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // CRN is optional and can change its display settings API between releases
        }
    }

    // Get the serialize
    private static CompoundTag serialize(Object settings) {
        Object val = invoke(settings, "serializeNbt", new Class<?>[0]);
        return val instanceof CompoundTag tag ? tag.copy() : new CompoundTag();
    }

    // Get the text
    private static String text(Object settings) {
        Object val = invoke(settings, "getStaticText", new Class<?>[0]);
        return val == null ? "" : String.valueOf(val);
    }

    // Get the lines
    private static CompoundTag lines(Object settings) {
        CompoundTag res = new CompoundTag();
        Object components = invoke(settings, "getComponents", new Class<?>[0]);
        if (components instanceof List<?> list) {
            for (int idx = 0; idx < list.size(); idx++) putStringValue(res, idx, componentText(list.get(idx)));
        } else {
            putStringValue(res, 0, text(settings));
        }
        return res;
    }

    // Set the text
    private static boolean setText(Object settings, String text) {
        return invoke(settings, "setStaticText", new Class<?>[]{String.class}, text) != null;
    }

    // Set the lines
    private static boolean setLines(Object settings, CompoundTag lines) {
        Object components = invoke(settings, "getComponents", new Class<?>[0]);
        if (!(components instanceof List<?> initialList)) return setText(settings, graphString(lines, "0"));
        List<?> list = initialList;
        boolean changed = false;
        for (String key : lines.getAllKeys()) {
            int idx;
            try {
                idx = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }
            while (idx >= list.size() && invoke(settings, "createNewComponent", new Class<?>[0]) != null) {
                components = invoke(settings, "getComponents", new Class<?>[0]);
                if (!(components instanceof List<?> refreshed)) break;
                list = refreshed;
            }
            if (idx >= 0 && idx < list.size()) {
                changed |= invoke(list.get(idx), "setStaticText", new Class<?>[]{String.class}, graphString(lines, key)) != null;
            }
        }
        return changed;
    }

    // Get the static text settings
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object staticTextSettings(Object display, boolean rich) {
        Object current = settings(display);
        if (current != null && hasMethod(current, rich ? "getComponents" : "setStaticText")) return current;
        Object created = createStaticTextSettings(display, rich);
        return created == null ? current : created;
    }

    // Create the static text settings
    private static Object createStaticTextSettings(Object display, boolean rich) {
        try {
            Class<?> categoryClass = Class.forName(DISPLAY_TYPE_CLASS);
            Object category = Enum.valueOf((Class<? extends Enum>) categoryClass.asSubclass(Enum.class), "STATIC_TEXT");
            Class<?> keyClass = Class.forName(DISPLAY_KEY_CLASS);
            Object key = keyClass.getConstructor(categoryClass, String.class)
                    .newInstance(category, rich ? "rich_text" : "simple_text");
            Class<?> registryClass = Class.forName(DISPLAY_REGISTRY_CLASS);
            Object newSettings = registryClass.getMethod("createSettings", keyClass).invoke(null, key);
            Class<?> settingsClass = Class.forName(DISPLAY_SETTINGS_CLASS);
            display.getClass().getMethod("setDisplayType", keyClass, settingsClass).invoke(display, key, newSettings);
            return newSettings;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // Check if this has method
    private static boolean hasMethod(Object target, String methodName) {
        if (target == null) return false;
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName)) return true;
        }
        return false;
    }

    // Get the public method
    private static Method publicMethod(Object target, String methodName, int parameterCount) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    // Notify the display
    private static void notifyDisplay(Object display) {
        invoke(display, "notifyUpdate", new Class<?>[0]);
        if (display instanceof BlockEntity blockEntity) blockEntity.setChanged();
    }

    // Add the Railway Navigator prefix
    private static void prefix(Map<String, String> target, String prefix, Map<String, String> values) {
        values.forEach((key, type) -> target.put(prefix + key, type));
    }

    // Get the component text
    private static String componentText(Object component) {
        Object val = invoke(component, "getStaticText", new Class<?>[0]);
        return val == null ? "" : String.valueOf(val);
    }

    // Put the string value
    private static void putStringValue(CompoundTag target, int idx, String text) {
        CompoundTag val = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        val.putString("Type", "string");
        payload.putString("Value", text);
        val.put("Payload", payload);
        target.put(Integer.toString(idx), val);
    }

    // Store the display snapshot
    private record DisplaySnapshot(Object displayType, CompoundTag settings) {
    }

    // Get the graph string
    private static String graphString(CompoundTag values, String key) {
        CompoundTag val = values.getCompound(key);
        return val.contains("Payload") ? val.getCompound("Payload").getString("Value") : values.getString(key);
    }

    // Run the railway navigator graph compat
    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            Object res = method.invoke(target, args);
            return method.getReturnType() == Void.TYPE ? Boolean.TRUE : res;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
