package com.rieno.gadgetsandgizmos.compat.aeroworks;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

// Keep optional Aeroworks controls behind cached reflection so the addon can run without it installed
public final class AeroworksControllerCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String JOYSTICK = "aeroworks:joystick";
    public static final String CONTROL_DESK = "aeroworks:control_desk";
    public static final String MECHANICAL_SERVO = "aeroworks:mechanical_servo";
    public static final String STEPPER_SERVO = "aeroworks:stepper_servo";
    public static final String GRAPH_SECTION_ID_KEY = "AeroworksSection";
    public static final String GRAPH_SECTION_LABEL_KEY = "AeroworksSectionLabel";

    private static final String CONSOLE_ROUTE_PREFIX = "aw_console:";
    private static final int MAX_CONSOLE_GROUP_SIZE = 32;
    private static final double DEFAULT_SOURCE_TILT_DEGREES = 28.0D;
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final Object SIGNAL_LOCK = new Object();
    private static final Map<BlockEntity, Map<String, DirectSignal>> DIRECT_SIGNALS = new WeakHashMap<>();
    private static final Map<BlockEntity, Map<String, ConsoleWrite>> CONSOLE_GRAPH_SIGNALS = new WeakHashMap<>();
    private static final ClassValue<ConcurrentMap<MethodKey, Optional<Method>>> METHOD_CACHE =
            new ClassValue<>() {
                // Calculate the value
                @Override
                protected ConcurrentMap<MethodKey, Optional<Method>> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };
    private static final ClassValue<ConcurrentMap<String, Optional<Field>>> FIELD_CACHE =
            new ClassValue<>() {
                // Calculate the value
                @Override
                protected ConcurrentMap<String, Optional<Field>> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };
    private static final ConcurrentMap<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aeroworks controller compat
    private AeroworksControllerCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a direct adapter
    public static boolean isDirectAdapter(@Nullable BlockEntity blockEntity) {
        return JOYSTICK.equals(blockId(blockEntity)) || isConsoleController(blockEntity);
    }

    // Check if this is an advanced data link source
    public static boolean isAdvancedDataLinkSource(@Nullable BlockEntity blockEntity) {
        return JOYSTICK.equals(blockId(blockEntity)) || isConsoleController(blockEntity);
    }

    // Get the directional axes
    public static @Nullable DirectionalAxes directionalAxes(@Nullable BlockEntity blockEntity) {
        String blockId = blockId(blockEntity);
        if (JOYSTICK.equals(blockId)) {
            double localX = Mth.clamp(number(invoke(blockEntity, "getTiltX")).doubleValue() / 15.0D, -1.0D, 1.0D);
            double localZ = Mth.clamp(number(invoke(blockEntity, "getTiltY")).doubleValue() / 15.0D, -1.0D, 1.0D);
            return new DirectionalAxes(localX, localZ, DEFAULT_SOURCE_TILT_DEGREES);
        }
        if (!CONTROL_DESK.equals(blockId)
                && (blockEntity == null || !"com.mred231.aeroworks.content.controls.ConsoleBlockEntity"
                .equals(blockEntity.getClass().getName()))) {
            return null;
        }

        List<ConsoleChannel> channels = consoleChannels(blockEntity);
        ConsoleChannelPair pair = findPrimaryAxes(channels);
        if (pair != null) {
            return new DirectionalAxes(normalizedAxis(pair.localX()), normalizedAxis(pair.localZ()),
                    DEFAULT_SOURCE_TILT_DEGREES);
        }

        ConsoleChannel localX = firstAxis(channels, channel -> containsAny(
                channel.channelId(), "x", "turn", "wheel", "roll", "yaw"));
        ConsoleChannel localZ = firstAxis(channels, channel -> containsAny(
                channel.channelId(), "y", "pitch", "lever", "throttle"));
        if (localX == null && localZ == null) {
            List<ConsoleChannel> axes = channels.stream().filter(ConsoleChannel::axis).toList();
            localX = axes.isEmpty() ? null : axes.getFirst();
            localZ = axes.size() < 2 ? null : axes.get(1);
        }
        return new DirectionalAxes(normalizedAxis(localX), normalizedAxis(localZ),
                DEFAULT_SOURCE_TILT_DEGREES);
    }

    // Sample the direct signal
    public static @Nullable Double sampleDirectSignal(@Nullable BlockEntity blockEntity, String channelId) {
        if (JOYSTICK.equals(blockId(blockEntity))) {
            int x = number(invoke(blockEntity, "getTiltX")).intValue();
            int y = number(invoke(blockEntity, "getTiltY")).intValue();
            String channel = normalize(channelId);
            if (isLeft(channel)) return Mth.clamp(-x / 15.0D, 0.0D, 1.0D);
            if (isRight(channel)) return Mth.clamp(x / 15.0D, 0.0D, 1.0D);
            if (isUp(channel)) return Mth.clamp(-y / 15.0D, 0.0D, 1.0D);
            if (isDown(channel)) return Mth.clamp(y / 15.0D, 0.0D, 1.0D);
            if (channel.contains("button") || channel.contains("stabilize")) {
                return Boolean.TRUE.equals(invoke(blockEntity, "isButtonPressed")) ? 1.0D : 0.0D;
            }
            return Mth.clamp(Math.max(Math.abs(x), Math.abs(y)) / 15.0D, 0.0D, 1.0D);
        }

        ConsoleRoute route = parseConsoleRoute(channelId);
        ConsoleChannel channel = resolveConsoleChannel(blockEntity, route);
        if (channel == null) {
            return null;
        }
        if (channel.button()) {
            return channel.value() == 0 ? 0.0D : 1.0D;
        }
        double signed = Mth.clamp(channel.value() / 15.0D, -1.0D, 1.0D);
        return route.negative() ? Math.max(0.0D, -signed) : Math.max(0.0D, signed);
    }

    // Apply the direct signal
    public static boolean applyDirectSignal(@Nullable BlockEntity blockEntity, String channelId,
                                            String sourceId, float val) {
        if (!isDirectAdapter(blockEntity) || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        if (JOYSTICK.equals(blockId(blockEntity))) {
            return applyJoystickSignal(blockEntity, channelId, sourceId, val);
        }

        ConsoleRoute route = parseConsoleRoute(channelId);
        ConsoleChannel channel = resolveConsoleChannel(blockEntity, route);
        if (channel == null) {
            return false;
        }
        synchronized (SIGNAL_LOCK) {
            Map<String, DirectSignal> signals =
                    DIRECT_SIGNALS.computeIfAbsent(channel.console(), ignored -> new LinkedHashMap<>());
            if (val <= 0.0F) {
                signals.remove(sourceId);
            } else {
                signals.put(sourceId, new DirectSignal(normalize(channelId), Mth.clamp(val, 0.0F, 1.0F)));
            }
            if (signals.isEmpty()) {
                DIRECT_SIGNALS.remove(channel.console());
            }

            int nextValue = resolvedConsoleValue(channel.console(), channel.socket(),
                    channel.channelId(), channel.button());
            return invoke(channel.console(), "setChannelFromController",
                    new Class<?>[]{int.class, String.class, int.class},
                    channel.socket(), channel.channelId(), nextValue);
        }
    }

    // Reapply the direct signals
    public static void reapplyDirectSignals(@Nullable BlockEntity blockEntity) {
        if (!isConsoleController(blockEntity) || blockEntity.getLevel() == null
                || blockEntity.getLevel().isClientSide) {
            return;
        }
        synchronized (SIGNAL_LOCK) {
            Map<String, DirectSignal> signals = DIRECT_SIGNALS.get(blockEntity);
            Map<String, ConsoleWrite> graphSignals = CONSOLE_GRAPH_SIGNALS.get(blockEntity);
            if ((signals == null || signals.isEmpty()) && (graphSignals == null || graphSignals.isEmpty())) {
                return;
            }

            Map<ConsoleSignalKey, Boolean> channels = new LinkedHashMap<>();
            if (signals != null) {
                for (DirectSignal signal : signals.values()) {
                    ConsoleRoute route = parseConsoleRoute(signal.channel());
                    if (route != null) {
                        channels.put(new ConsoleSignalKey(route.socket(), route.channelId()), false);
                    }
                }
            }
            if (graphSignals != null) {
                for (ConsoleWrite graphSignal : graphSignals.values()) {
                    channels.put(new ConsoleSignalKey(graphSignal.socket(), graphSignal.channelId()),
                            graphSignal.button());
                }
            }

            for (Map.Entry<ConsoleSignalKey, Boolean> entry : channels.entrySet()) {
                ConsoleSignalKey key = entry.getKey();
                Object module = call(blockEntity, "module", new Class<?>[]{int.class}, key.socket());
                Object controlChannel = module == null ? null
                        : call(module, "channel", new Class<?>[]{String.class}, key.channelId());
                boolean btn = controlChannel == null
                        ? entry.getValue()
                        : "button".equals(enumName(invoke(controlChannel, "kind")));
                int nextValue = resolvedConsoleValue(blockEntity, key.socket(), key.channelId(), btn);
                invoke(blockEntity, "setChannelFromController",
                        new Class<?>[]{int.class, String.class, int.class},
                        key.socket(), key.channelId(), nextValue);
            }
        }
    }

    // Clear the source
    public static void clearSource(@Nullable String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        synchronized (SIGNAL_LOCK) {
            for (Map.Entry<BlockEntity, Map<String, DirectSignal>> entry :
                    new ArrayList<>(DIRECT_SIGNALS.entrySet())) {
                BlockEntity target = entry.getKey();
                Map<String, DirectSignal> signals = entry.getValue();
                DirectSignal removed = signals.remove(sourceId);
                if (removed == null) {
                    continue;
                }
                if (signals.isEmpty()) {
                    DIRECT_SIGNALS.remove(target);
                }
                if (JOYSTICK.equals(blockId(target))) {
                    applyJoystickSignals(target, signals);
                    continue;
                }
                ConsoleRoute route = parseConsoleRoute(removed.channel());
                reapplyConsoleChannel(target, route);
            }

            for (Map.Entry<BlockEntity, Map<String, ConsoleWrite>> entry :
                    new ArrayList<>(CONSOLE_GRAPH_SIGNALS.entrySet())) {
                BlockEntity target = entry.getKey();
                Map<String, ConsoleWrite> signals = entry.getValue();
                List<ConsoleWrite> removed = new ArrayList<>();
                signals.entrySet().removeIf(signal -> {
                    boolean matches = signal.getKey().equals(sourceId)
                            || signal.getKey().startsWith(sourceId + ":");
                    if (matches) {
                        removed.add(signal.getValue());
                    }
                    return matches;
                });
                if (signals.isEmpty()) {
                    CONSOLE_GRAPH_SIGNALS.remove(target);
                }
                for (ConsoleWrite signal : removed) {
                    reapplyConsoleChannel(target,
                            new ConsoleRoute(0, 0, 0, signal.socket(), false, signal.channelId()));
                }
            }
        }
    }

    // Get the console options
    public static List<ConsoleOption> consoleOptions(@Nullable BlockEntity blockEntity) {
        if (!isConsoleController(blockEntity)) {
            return List.of();
        }
        List<ConsoleOption> opts = new ArrayList<>();
        for (ConsoleChannel channel : consoleChannels(blockEntity)) {
            if (channel.axis()) {
                opts.add(new ConsoleOption(consoleRoute(channel, false), channel.label() + " +"));
                opts.add(new ConsoleOption(consoleRoute(channel, true), channel.label() + " -"));
            } else {
                opts.add(new ConsoleOption(consoleRoute(channel, false), channel.label()));
            }
        }
        return List.copyOf(opts);
    }

    // Get the console sections
    public static List<ConsoleSection> consoleSections(@Nullable BlockEntity blockEntity) {
        if (!isConsoleController(blockEntity)) {
            return List.of();
        }
        Map<String, ConsoleSection> sections = new LinkedHashMap<>();
        for (ConsoleChannel channel : consoleChannels(blockEntity)) {
            String id = consoleSectionId(channel.offset(), channel.socket());
            sections.putIfAbsent(id, new ConsoleSection(id,
                    consoleSectionLabel(blockEntity.getBlockPos(), channel.console().getBlockPos(),
                            channel.socket(), channel.moduleLabel())));
        }
        return List.copyOf(sections.values());
    }

    // Resolve the console section
    public static @Nullable ConsoleSection resolveConsoleSection(@Nullable BlockEntity blockEntity,
                                                                 @Nullable String sectionId) {
        List<ConsoleSection> sections = consoleSections(blockEntity);
        if (sections.isEmpty()) {
            return null;
        }
        if (sectionId != null && !sectionId.isBlank()) {
            for (ConsoleSection section : sections) {
                if (section.id().equals(sectionId)) {
                    return section;
                }
            }
        }
        return sections.getFirst();
    }

    // Get the readable data
    public static Map<String, String> readableData(@Nullable BlockEntity blockEntity) {
        return readableData(blockEntity, null);
    }

    // Get the readable data
    public static Map<String, String> readableData(@Nullable BlockEntity blockEntity,
                                                    @Nullable String consoleSectionId) {
        Map<String, String> base = switch (blockId(blockEntity)) {
            case JOYSTICK -> fields(
                    "tilt_x", "number", "tilt_y", "number", "forward", "number", "right", "number",
                    "back", "number", "left", "number", "button_pressed", "boolean", "powered", "boolean",
                    "spring_back", "boolean", "use_mouse_input", "boolean", "show_hud", "boolean");
            case MECHANICAL_SERVO -> fields(
                    "start_angle", "number", "end_angle", "number", "direction", "string",
                    "signal", "number", "current_angle", "number", "target_angle", "number", "moving", "boolean");
            case STEPPER_SERVO -> fields(
                    "step_degrees", "number", "left_signal", "number", "right_signal", "number",
                    "active_signal_strength", "number", "signal_side", "string",
                    "current_angle", "number", "target_angle", "number", "moving", "boolean");
            default -> new LinkedHashMap<>();
        };
        if (isConsoleController(blockEntity)) {
            ConsoleSection section = resolveConsoleSection(blockEntity, consoleSectionId);
            if (consoleSectionId == null || consoleSectionId.isBlank()) {
                base.put("controller_active", "boolean");
                base.put("controller_parts", "number");
                base.put("socket_count", "number");
            }
            for (ConsoleChannel channel : consoleChannels(blockEntity)) {
                if (consoleSectionId == null || consoleSectionId.isBlank()
                        || section != null && section.id().equals(consoleSectionId(channel))) {
                    base.put(channel.portId(), channel.button() ? "boolean" : "number");
                }
            }
        }
        return base;
    }

    // Get the writable data
    public static Map<String, String> writableData(@Nullable BlockEntity blockEntity) {
        return writableData(blockEntity, null);
    }

    // Get the writable data
    public static Map<String, String> writableData(@Nullable BlockEntity blockEntity,
                                                    @Nullable String consoleSectionId) {
        Map<String, String> base = switch (blockId(blockEntity)) {
            case JOYSTICK -> fields(
                    "tilt_x", "number", "tilt_y", "number", "button_pressed", "boolean",
                    "spring_back", "boolean", "use_mouse_input", "boolean", "show_hud", "boolean");
            case MECHANICAL_SERVO -> fields("start_angle", "number", "end_angle", "number", "direction", "string");
            case STEPPER_SERVO -> fields("step_degrees", "number");
            default -> new LinkedHashMap<>();
        };
        if (isConsoleController(blockEntity)) {
            ConsoleSection section = resolveConsoleSection(blockEntity, consoleSectionId);
            for (ConsoleChannel channel : consoleChannels(blockEntity)) {
                if (consoleSectionId == null || consoleSectionId.isBlank()
                        || section != null && section.id().equals(consoleSectionId(channel))) {
                    base.put(channel.portId(), channel.button() ? "boolean" : "number");
                }
            }
        }
        return base;
    }

    // Get the writable options
    public static CompoundTag writableOptions(@Nullable BlockEntity blockEntity) {
        CompoundTag opts = new CompoundTag();
        if (MECHANICAL_SERVO.equals(blockId(blockEntity))) {
            ListTag directions = new ListTag();
            directions.add(StringTag.valueOf("cw"));
            directions.add(StringTag.valueOf("ccw"));
            opts.put("direction", directions);
        }
        return opts;
    }

    // Read the aeroworks controller compat
    public static @Nullable AdvancedGraphDocument.Value read(@Nullable BlockEntity blockEntity, String field) {
        if (!readableData(blockEntity).containsKey(field)) {
            return null;
        }
        if (isConsoleController(blockEntity)) {
            if ("controller_active".equals(field)) {
                return AdvancedGraphDocument.Value.bool(consoleGroup(blockEntity).stream()
                        .anyMatch(member -> Boolean.TRUE.equals(invoke(member, "hasController"))));
            }
            if ("controller_parts".equals(field)) {
                return AdvancedGraphDocument.Value.number(consoleGroup(blockEntity).size());
            }
            if ("socket_count".equals(field)) {
                int sockets = consoleGroup(blockEntity).stream()
                        .mapToInt(member -> number(invoke(member, "socketCount")).intValue())
                        .sum();
                return AdvancedGraphDocument.Value.number(sockets);
            }
            ConsoleChannel channel = consoleChannels(blockEntity).stream()
                    .filter(candidate -> candidate.portId().equals(field))
                    .findFirst()
                    .orElse(null);
            if (channel != null) {
                return channel.button()
                        ? AdvancedGraphDocument.Value.bool(channel.value() != 0)
                        : AdvancedGraphDocument.Value.number(channel.value());
            }
        }

        Object val = switch (field) {
            case "tilt_x" -> invoke(blockEntity, "getTiltX");
            case "tilt_y" -> invoke(blockEntity, "getTiltY");
            case "forward" -> directionStrength(blockEntity, "forward");
            case "right" -> directionStrength(blockEntity, "right");
            case "back" -> directionStrength(blockEntity, "back");
            case "left" -> directionStrength(blockEntity, "left");
            case "button_pressed" -> invoke(blockEntity, "isButtonPressed");
            case "powered" -> invoke(blockEntity, "isPowered");
            case "spring_back" -> invoke(blockEntity, "isSpringBack");
            case "use_mouse_input" -> invoke(blockEntity, "isUseMouseInput");
            case "show_hud" -> invoke(blockEntity, "isShowHud");
            case "start_angle" -> invoke(blockEntity, "getStartAngle");
            case "end_angle" -> invoke(blockEntity, "getEndAngle");
            case "direction" -> enumName(invoke(blockEntity, "getDirection"));
            case "signal" -> number(readField(blockEntity, "signalFraction")).doubleValue() * 15.0D;
            case "step_degrees" -> invoke(blockEntity, "getStepDegrees");
            case "left_signal" -> invoke(blockEntity, "getLeftSignal");
            case "right_signal" -> invoke(blockEntity, "getRightSignal");
            case "active_signal_strength" -> invoke(blockEntity, "getActiveSignalStrength");
            case "signal_side" -> enumName(invoke(blockEntity, "getSignalSide"));
            case "current_angle" -> readField(blockEntity, "currentAngle");
            case "target_angle" -> readField(blockEntity, "target");
            case "moving" -> readField(blockEntity, "moving");
            default -> null;
        };
        if (val instanceof Boolean bool) return AdvancedGraphDocument.Value.bool(bool);
        if (val instanceof Number num) return AdvancedGraphDocument.Value.number(num.doubleValue());
        return AdvancedGraphDocument.Value.string(val == null ? "" : String.valueOf(val));
    }

    // Write the aeroworks controller compat
    public static boolean write(@Nullable BlockEntity blockEntity, String field, AdvancedGraphDocument.Value val) {
        return write(blockEntity, field, val, null);
    }

    // Write the aeroworks controller compat
    public static boolean write(@Nullable BlockEntity blockEntity, String field,
                                AdvancedGraphDocument.Value val, @Nullable String sourceId) {
        if (!writableData(blockEntity).containsKey(field) || val == null) {
            return false;
        }
        if (isConsoleController(blockEntity)) {
            ConsoleChannel channel = consoleChannels(blockEntity).stream()
                    .filter(candidate -> candidate.portId().equals(field))
                    .findFirst()
                    .orElse(null);
            if (channel == null) {
                return false;
            }
            int next = channel.button()
                    ? (val.asBoolean() ? 1 : 0)
                    : Mth.clamp((int) Math.round(val.asNumber()), -15, 15);
            if (sourceId != null && !sourceId.isBlank()) {
                return applyGraphConsoleWrite(channel, sourceId + ":" + field, next);
            }
            return invoke(channel.console(), "setChannelFromController",
                    new Class<?>[]{int.class, String.class, int.class},
                    channel.socket(), channel.channelId(), next);
        }
        return switch (blockId(blockEntity)) {
            case JOYSTICK -> writeJoystick(blockEntity, field, val);
            case MECHANICAL_SERVO -> writeMechanicalServo(blockEntity, field, val);
            case STEPPER_SERVO -> writeStepperServo(blockEntity, field, val);
            default -> false;
        };
    }

    // Apply the joystick signal
    private static boolean applyJoystickSignal(BlockEntity blockEntity, String channelId,
                                               String sourceId, float val) {
        synchronized (SIGNAL_LOCK) {
            Map<String, DirectSignal> signals =
                    DIRECT_SIGNALS.computeIfAbsent(blockEntity, ignored -> new LinkedHashMap<>());
            if (val <= 0.0F) {
                signals.remove(sourceId);
            } else {
                signals.put(sourceId, new DirectSignal(normalize(channelId), Mth.clamp(val, 0.0F, 1.0F)));
            }
            if (signals.isEmpty()) {
                DIRECT_SIGNALS.remove(blockEntity);
            }
            return applyJoystickSignals(blockEntity, signals);
        }
    }

    // Apply the joystick signals
    private static boolean applyJoystickSignals(BlockEntity blockEntity, Map<String, DirectSignal> signals) {
        float left = strongest(signals, AeroworksControllerCompat::isLeft);
        float right = strongest(signals, AeroworksControllerCompat::isRight);
        float up = strongest(signals, AeroworksControllerCompat::isUp);
        float down = strongest(signals, AeroworksControllerCompat::isDown);
        float btn = strongest(signals, channel -> channel.contains("button") || channel.contains("stabilize"));
        boolean tiltChanged = invoke(blockEntity, "setTiltFromController",
                new Class<?>[]{byte.class, byte.class},
                (byte) Math.round((right - left) * 15.0F),
                (byte) Math.round((down - up) * 15.0F));
        boolean buttonChanged = invoke(blockEntity, "setButtonPressedFromController",
                new Class<?>[]{boolean.class}, btn > 0.0F);
        return tiltChanged || buttonChanged;
    }

    // Apply the graph console write
    private static boolean applyGraphConsoleWrite(ConsoleChannel channel, String sourceId, int val) {
        synchronized (SIGNAL_LOCK) {
            Map<String, ConsoleWrite> signals =
                    CONSOLE_GRAPH_SIGNALS.computeIfAbsent(channel.console(), ignored -> new LinkedHashMap<>());
            if (val == 0) {
                signals.remove(sourceId);
            } else {
                signals.remove(sourceId);
                signals.put(sourceId, new ConsoleWrite(
                        channel.socket(), channel.channelId(), channel.button(), val));
            }
            if (signals.isEmpty()) {
                CONSOLE_GRAPH_SIGNALS.remove(channel.console());
            }
            int nextValue = resolvedConsoleValue(channel.console(), channel.socket(),
                    channel.channelId(), channel.button());
            return invoke(channel.console(), "setChannelFromController",
                    new Class<?>[]{int.class, String.class, int.class},
                    channel.socket(), channel.channelId(), nextValue);
        }
    }

    // Get the resolved console value
    private static int resolvedConsoleValue(BlockEntity console, int socket, String channelId, boolean btn) {
        Map<String, ConsoleWrite> graphSignals = CONSOLE_GRAPH_SIGNALS.get(console);
        ConsoleWrite latestGraphSignal = null;
        if (graphSignals != null) {
            for (ConsoleWrite signal : graphSignals.values()) {
                if (signal.socket() == socket && signal.channelId().equals(channelId)) {
                    latestGraphSignal = signal;
                }
            }
        }
        if (latestGraphSignal != null) {
            return latestGraphSignal.value();
        }

        Map<String, DirectSignal> signals = DIRECT_SIGNALS.get(console);
        if (signals == null || signals.isEmpty()) {
            return 0;
        }
        float positive = strongest(signals, candidate -> matchesConsoleChannel(candidate, socket, channelId)
                && !parseConsoleRoute(candidate).negative());
        float negative = strongest(signals, candidate -> matchesConsoleChannel(candidate, socket, channelId)
                && parseConsoleRoute(candidate).negative());
        return btn
                ? (positive > 0.0F || negative > 0.0F ? 1 : 0)
                : Math.round((positive - negative) * 15.0F);
    }

    // Reapply the console channel
    private static void reapplyConsoleChannel(@Nullable BlockEntity console, @Nullable ConsoleRoute route) {
        if (!isConsoleController(console) || route == null) {
            return;
        }
        Object module = call(console, "module", new Class<?>[]{int.class}, route.socket());
        Object controlChannel = module == null ? null
                : call(module, "channel", new Class<?>[]{String.class}, route.channelId());
        if (controlChannel == null) {
            return;
        }
        boolean btn = "button".equals(enumName(invoke(controlChannel, "kind")));
        int nextValue = resolvedConsoleValue(console, route.socket(), route.channelId(), btn);
        invoke(console, "setChannelFromController",
                new Class<?>[]{int.class, String.class, int.class},
                route.socket(), route.channelId(), nextValue);
    }

    // Write a joystick field
    private static boolean writeJoystick(BlockEntity target, String field, AdvancedGraphDocument.Value val) {
        return switch (field) {
            case "tilt_x" -> invoke(target, "setTiltFromController", new Class<?>[]{byte.class, byte.class},
                    (byte) Mth.clamp((int) val.asNumber(), -15, 15), number(invoke(target, "getTiltY")).byteValue());
            case "tilt_y" -> invoke(target, "setTiltFromController", new Class<?>[]{byte.class, byte.class},
                    number(invoke(target, "getTiltX")).byteValue(), (byte) Mth.clamp((int) val.asNumber(), -15, 15));
            case "button_pressed" -> invoke(target, "setButtonPressedFromController",
                    new Class<?>[]{boolean.class}, val.asBoolean());
            case "spring_back" -> invoke(target, "setSpringBack", new Class<?>[]{boolean.class}, val.asBoolean());
            case "use_mouse_input" -> invoke(target, "setUseMouseInput", new Class<?>[]{boolean.class}, val.asBoolean());
            case "show_hud" -> invoke(target, "setShowHud", new Class<?>[]{boolean.class}, val.asBoolean());
            default -> false;
        };
    }

    // Write the mechanical servo
    private static boolean writeMechanicalServo(BlockEntity target, String field, AdvancedGraphDocument.Value val) {
        float start = number(invoke(target, "getStartAngle")).floatValue();
        float end = number(invoke(target, "getEndAngle")).floatValue();
        Object dir = invoke(target, "getDirection");
        if (dir == null) return false;
        if ("start_angle".equals(field)) start = (float) val.asNumber();
        if ("end_angle".equals(field)) end = (float) val.asNumber();
        if ("direction".equals(field)) dir = enumValue(dir.getClass(), val.asString());
        return dir != null && invoke(target, "applyConfig",
                new Class<?>[]{float.class, float.class, dir.getClass()}, start, end, dir);
    }

    // Write the stepper servo
    private static boolean writeStepperServo(BlockEntity target, String field, AdvancedGraphDocument.Value val) {
        Object behaviour = readField(target, "stepBehaviour");
        return behaviour != null && invoke(behaviour, "setValue", new Class<?>[]{int.class},
                Mth.clamp((int) val.asNumber(), 1, 180));
    }

    // Get the direction strength
    private static int directionStrength(BlockEntity target, String dir) {
        int x = number(invoke(target, "getTiltX")).intValue();
        int y = number(invoke(target, "getTiltY")).intValue();
        return switch (dir) {
            case "forward" -> Math.max(0, -y);
            case "right" -> Math.max(0, x);
            case "back" -> Math.max(0, y);
            default -> Math.max(0, -x);
        };
    }

    // Get the console channels
    private static List<ConsoleChannel> consoleChannels(@Nullable BlockEntity origin) {
        if (!isConsoleController(origin)) {
            return List.of();
        }
        List<ConsoleChannel> channels = new ArrayList<>();
        BlockPos originPos = origin.getBlockPos();
        for (BlockEntity console : consoleGroup(origin)) {
            BlockPos offset = console.getBlockPos().subtract(originPos);
            int socketCount = Math.max(0, number(invoke(console, "socketCount")).intValue());
            for (int socket = 0; socket < socketCount; socket++) {
                Object module = call(console, "module", new Class<?>[]{int.class}, socket);
                if (module == null) {
                    continue;
                }
                String moduleId = moduleId(module);
                String moduleLabel = moduleLabel(module, moduleId);
                Object channelList = invoke(module, "channels");
                if (!(channelList instanceof Iterable<?> iterable)) {
                    continue;
                }
                for (Object channel : iterable) {
                    String channelId = string(invoke(channel, "id"));
                    if (channelId.isBlank()) {
                        continue;
                    }
                    String kind = enumName(invoke(channel, "kind"));
                    boolean btn = "button".equals(kind);
                    int channelValue = number(call(module, "value",
                            new Class<?>[]{String.class}, channelId)).intValue();
                    String label = consoleChannelLabel(originPos, console.getBlockPos(), socket,
                            moduleLabel, channelLabel(channel, channelId));
                    String portId = consolePortId(offset, socket, moduleId, channelId);
                    channels.add(new ConsoleChannel(console, offset, socket, moduleId, moduleLabel, channelId,
                            portId, label, btn, channelValue));
                }
            }
        }
        return List.copyOf(channels);
    }

    // Get the console group
    private static List<BlockEntity> consoleGroup(@Nullable BlockEntity origin) {
        if (!isConsoleController(origin)) {
            return List.of();
        }
        Level level = origin.getLevel();
        if (level == null) {
            return List.of(origin);
        }
        List<BlockEntity> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockEntity> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin.getBlockPos());
        while (!queue.isEmpty() && members.size() < MAX_CONSOLE_GROUP_SIZE) {
            BlockEntity member = queue.removeFirst();
            members.add(member);
            for (Direction dir : Direction.values()) {
                BlockPos nextPos = member.getBlockPos().relative(dir);
                if (!visited.add(nextPos) || !level.isLoaded(nextPos)) {
                    continue;
                }
                BlockEntity next = level.getBlockEntity(nextPos);
                if (isConsoleController(next)) {
                    queue.addLast(next);
                }
            }
        }
        return List.copyOf(members);
    }

    // Find the primary axes
    private static @Nullable ConsoleChannelPair findPrimaryAxes(List<ConsoleChannel> channels) {
        for (String moduleToken : List.of("joystick", "yoke")) {
            for (ConsoleChannel first : channels) {
                if (!first.axis() || !first.moduleId().contains(moduleToken)) {
                    continue;
                }
                ConsoleChannel localX = findSiblingAxis(channels, first,
                        moduleToken.equals("joystick") ? List.of("x") : List.of("turn", "roll", "yaw"));
                ConsoleChannel localZ = findSiblingAxis(channels, first,
                        moduleToken.equals("joystick") ? List.of("y") : List.of("pitch"));
                if (localX != null && localZ != null) {
                    return new ConsoleChannelPair(localX, localZ);
                }
            }
        }
        return null;
    }

    // Find the sibling axis
    private static @Nullable ConsoleChannel findSiblingAxis(List<ConsoleChannel> channels,
                                                             ConsoleChannel sibling,
                                                             List<String> names) {
        for (ConsoleChannel candidate : channels) {
            if (!candidate.axis()
                    || candidate.console() != sibling.console()
                    || candidate.socket() != sibling.socket()
                    || !candidate.moduleId().equals(sibling.moduleId())) {
                continue;
            }
            String id = normalize(candidate.channelId());
            if (names.stream().anyMatch(name -> id.equals(name) || id.endsWith("/" + name))) {
                return candidate;
            }
        }
        return null;
    }

    // Get the first axis
    private static @Nullable ConsoleChannel firstAxis(List<ConsoleChannel> channels,
                                                       Predicate<ConsoleChannel> predicate) {
        return channels.stream().filter(ConsoleChannel::axis).filter(predicate).findFirst().orElse(null);
    }

    // Get the normalized axis
    private static double normalizedAxis(@Nullable ConsoleChannel channel) {
        return channel == null ? 0.0D : Mth.clamp(channel.value() / 15.0D, -1.0D, 1.0D);
    }

    // Check if the value contains any token
    private static boolean containsAny(String val, String... tokens) {
        String normalized = normalize(val);
        for (String token : tokens) {
            if (normalized.equals(token) || normalized.endsWith("/" + token)
                    || normalized.contains("_" + token) || normalized.contains(token + "_")) {
                return true;
            }
        }
        return false;
    }

    // Get the console route
    private static String consoleRoute(ConsoleChannel channel, boolean negative) {
        return CONSOLE_ROUTE_PREFIX
                + channel.offset().getX() + ":"
                + channel.offset().getY() + ":"
                + channel.offset().getZ() + ":"
                + channel.socket() + ":"
                + (negative ? "negative" : "positive") + ":"
                + HexFormat.of().formatHex(channel.channelId().getBytes(StandardCharsets.UTF_8));
    }

    // Parse the console route
    private static @Nullable ConsoleRoute parseConsoleRoute(@Nullable String routeId) {
        if (routeId == null || !normalize(routeId).startsWith(CONSOLE_ROUTE_PREFIX)) {
            return null;
        }
        String[] parts = routeId.trim().split(":", 7);
        if (parts.length != 7 || !"aw_console".equalsIgnoreCase(parts[0])) {
            return null;
        }
        try {
            String channelId = new String(HexFormat.of().parseHex(parts[6]), StandardCharsets.UTF_8);
            return new ConsoleRoute(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    "negative".equalsIgnoreCase(parts[5]),
                    channelId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Resolve the console channel
    private static @Nullable ConsoleChannel resolveConsoleChannel(@Nullable BlockEntity origin,
                                                                   @Nullable ConsoleRoute route) {
        if (!isConsoleController(origin) || route == null) {
            return null;
        }
        for (ConsoleChannel channel : consoleChannels(origin)) {
            BlockPos offset = channel.offset();
            if (offset.getX() == route.offsetX()
                    && offset.getY() == route.offsetY()
                    && offset.getZ() == route.offsetZ()
                    && channel.socket() == route.socket()
                    && channel.channelId().equals(route.channelId())) {
                return channel;
            }
        }
        return null;
    }

    // Check if this matches console channel
    private static boolean matchesConsoleChannel(String routeId, int socket, String channelId) {
        ConsoleRoute candidate = parseConsoleRoute(routeId);
        return candidate != null
                && candidate.socket() == socket
                && candidate.channelId().equals(channelId);
    }

    // Get the console port id
    private static String consolePortId(BlockPos offset, int socket, String moduleId, String channelId) {
        StringBuilder id = new StringBuilder("controller");
        if (!BlockPos.ZERO.equals(offset)) {
            id.append("_part_")
                    .append(coordinateToken(offset.getX())).append('_')
                    .append(coordinateToken(offset.getY())).append('_')
                    .append(coordinateToken(offset.getZ()));
        }
        id.append("_socket_").append(socket + 1)
                .append('_').append(sanitizeId(pathPart(moduleId)))
                .append('_').append(sanitizeId(channelId));
        return id.toString();
    }

    // Get the console section id
    private static String consoleSectionId(ConsoleChannel channel) {
        return consoleSectionId(channel.offset(), channel.socket());
    }

    // Get the console section id
    private static String consoleSectionId(BlockPos offset, int socket) {
        return "aw_section:"
                + offset.getX() + ":"
                + offset.getY() + ":"
                + offset.getZ() + ":"
                + socket;
    }

    // Get the coordinate token
    private static String coordinateToken(int val) {
        return val < 0 ? "m" + -val : "p" + val;
    }

    // Get the console channel label
    private static String consoleChannelLabel(BlockPos originPos, BlockPos memberPos, int socket,
                                              String moduleLabel, String channelLabel) {
        String part = originPos.equals(memberPos)
                ? "Control Desk"
                : "Control Desk " + memberPos.toShortString();
        return part + " - Socket " + (socket + 1) + " - " + moduleLabel + " - " + channelLabel;
    }

    // Get the console section label
    private static String consoleSectionLabel(BlockPos originPos, BlockPos memberPos, int socket,
                                              String moduleLabel) {
        String part = originPos.equals(memberPos)
                ? "Control Desk"
                : "Control Desk " + memberPos.toShortString();
        return part + " - Socket " + (socket + 1) + " - " + moduleLabel;
    }

    // Get the module id
    private static String moduleId(Object module) {
        Object type = invoke(module, "type");
        if (type == null) {
            return "module";
        }
        Object id = invokeStatic("com.mred231.aeroworks.content.controls.ModuleTypes",
                "idOf", new Class<?>[]{type.getClass()}, type);
        return id == null ? type.getClass().getSimpleName() : String.valueOf(id);
    }

    // Get the module label
    private static String moduleLabel(Object module, String moduleId) {
        Object customName = invoke(module, "customName");
        if (customName instanceof Component component && !component.getString().isBlank()) {
            return component.getString();
        }
        return titleCase(pathPart(moduleId));
    }

    // Get the channel label
    private static String channelLabel(Object channel, String channelId) {
        String key = string(invoke(channel, "displayNameKey"));
        if (!key.isBlank()) {
            String translated = Component.translatable(key).getString();
            if (!translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        return titleCase(channelId.replace('/', ' '));
    }

    // Get the path part
    private static String pathPart(String id) {
        int separator = id.indexOf(':');
        return separator >= 0 ? id.substring(separator + 1) : id;
    }

    // Sanitize the controller id
    private static String sanitizeId(String val) {
        StringBuilder sanitized = new StringBuilder();
        boolean previousUnderscore = false;
        for (char character : normalize(val).toCharArray()) {
            boolean alphaNumeric = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9';
            if (alphaNumeric) {
                sanitized.append(character);
                previousUnderscore = false;
            } else if (!previousUnderscore && !sanitized.isEmpty()) {
                sanitized.append('_');
                previousUnderscore = true;
            }
        }
        while (!sanitized.isEmpty() && sanitized.charAt(sanitized.length() - 1) == '_') {
            sanitized.deleteCharAt(sanitized.length() - 1);
        }
        return sanitized.isEmpty() ? "value" : sanitized.toString();
    }

    // Get the title case
    private static String titleCase(String val) {
        String normalized = val == null ? "" : val.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder res = new StringBuilder();
        for (String piece : normalized.split("\\s+")) {
            if (piece.isBlank()) {
                continue;
            }
            if (!res.isEmpty()) {
                res.append(' ');
            }
            res.append(Character.toUpperCase(piece.charAt(0)));
            if (piece.length() > 1) {
                res.append(piece.substring(1));
            }
        }
        return res.isEmpty() ? "Control" : res.toString();
    }

    // Check if this is a console controller
    private static boolean isConsoleController(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if ("com.mred231.aeroworks.content.controls.ConsoleBlockEntity"
                .equals(blockEntity.getClass().getName())) {
            return true;
        }
        return CONTROL_DESK.equals(blockId(blockEntity));
    }

    // Get the strongest
    private static float strongest(Map<String, DirectSignal> signals, Predicate<String> matches) {
        float strongest = 0.0F;
        for (DirectSignal signal : signals.values()) {
            if (matches.test(signal.channel())) {
                strongest = Math.max(strongest, signal.value());
            }
        }
        return strongest;
    }

    // Check if this is left
    private static boolean isLeft(String channel) {
        return channel.contains("left");
    }

    // Check if this is right
    private static boolean isRight(String channel) {
        return channel.contains("right");
    }

    // Check if this is up
    private static boolean isUp(String channel) {
        return channel.contains("up") || channel.contains("forward");
    }

    // Check if this is down
    private static boolean isDown(String channel) {
        return channel.contains("down") || channel.contains("back");
    }

    // Get the fields
    private static Map<String, String> fields(String... values) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int idx = 0; idx + 1 < values.length; idx += 2) {
            fields.put(values[idx], values[idx + 1]);
        }
        return fields;
    }

    // Get the block id
    private static String blockId(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return "";
        }
        if ("com.mred231.aeroworks.content.controls.ConsoleBlockEntity"
                .equals(blockEntity.getClass().getName())) {
            return CONTROL_DESK;
        }
        return String.valueOf(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()));
    }

    // Normalize the aeroworks controller compat
    private static String normalize(String val) {
        return val == null ? "" : val.trim().toLowerCase(Locale.ROOT);
    }

    // Get the string
    private static String string(@Nullable Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    // Read the numeric value
    private static Number number(@Nullable Object val) {
        return val instanceof Number num ? num : 0;
    }

    // Get the enum name
    private static String enumName(@Nullable Object val) {
        return val instanceof Enum<?> enumeration ? enumeration.name().toLowerCase(Locale.ROOT) : "";
    }

    // Get the enum value
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable Object enumValue(Class<?> type, String name) {
        try {
            return Enum.valueOf((Class<? extends Enum>) type, normalize(name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Run the aeroworks controller compat
    private static @Nullable Object invoke(Object target, String methodName) {
        return call(target, methodName, NO_PARAMETERS);
    }

    // Get the call
    private static @Nullable Object call(Object target, String methodName,
                                         Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = cachedMethod(target.getClass(), methodName, parameterTypes);
            return method == null ? null : method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // Run the aeroworks controller compat
    private static boolean invoke(Object target, String methodName, Class<?>[] parameterTypes,
                                  Object firstArg, Object... remainingArgs) {
        Object[] args = new Object[remainingArgs.length + 1];
        args[0] = firstArg;
        System.arraycopy(remainingArgs, 0, args, 1, remainingArgs.length);
        if (target == null) {
            return false;
        }
        try {
            Method method = cachedMethod(target.getClass(), methodName, parameterTypes);
            if (method == null) {
                return false;
            }
            method.invoke(target, args);
            if (target instanceof BlockEntity blockEntity) {
                blockEntity.setChanged();
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Run the static
    private static @Nullable Object invokeStatic(String className, String methodName,
                                                  Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> type = cachedClass(className);
            Method method = type == null ? null : cachedMethod(type, methodName, parameterTypes);
            if (method == null || !Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // Read the field
    private static @Nullable Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Optional<Field> cached = FIELD_CACHE.get(target.getClass()).computeIfAbsent(fieldName, ignored -> {
            for (Class<?> type = target.getClass(); type != null && type != Object.class;
                 type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (ReflectiveOperationException | RuntimeException ignoredException) {
                }
            }
            return Optional.empty();
        });
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // Get the cached method
    private static @Nullable Method cachedMethod(Class<?> type, String methodName,
                                                  Class<?>[] parameterTypes) {
        MethodKey key = new MethodKey(methodName,
                List.copyOf(Arrays.asList(parameterTypes)));
        return METHOD_CACHE.get(type).computeIfAbsent(key, ignored -> {
            try {
                return Optional.of(type.getMethod(methodName, parameterTypes));
            } catch (ReflectiveOperationException | RuntimeException ignoredException) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    // Get the cached class
    private static @Nullable Class<?> cachedClass(String className) {
        return CLASS_CACHE.computeIfAbsent(className, ignored -> {
            try {
                return Optional.of(Class.forName(className, false,
                        AeroworksControllerCompat.class.getClassLoader()));
            } catch (ReflectiveOperationException | RuntimeException ignoredException) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    // Store the console option
    public record ConsoleOption(String channelId, String label) {
        // Initialize the console option
        public ConsoleOption {
            channelId = channelId == null ? "" : channelId;
            label = label == null ? "" : label;
        }
    }

    // Store the console section
    public record ConsoleSection(String id, String label) {
        // Initialize the console section
        public ConsoleSection {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
        }
    }

    // Store the directional axes
    public record DirectionalAxes(double localX, double localZ, double maxTiltDegrees) {
        // Initialize the directional axes
        public DirectionalAxes {
            localX = Mth.clamp(localX, -1.0D, 1.0D);
            localZ = Mth.clamp(localZ, -1.0D, 1.0D);
            maxTiltDegrees = Math.max(0.0D, maxTiltDegrees);
        }
    }

    // Store the direct signal
    private record DirectSignal(String channel, float value) {
    }

    // Store the console write
    private record ConsoleWrite(int socket, String channelId, boolean button, int value) {
    }

    // Store the console signal key
    private record ConsoleSignalKey(int socket, String channelId) {
    }

    // Store the console route
    private record ConsoleRoute(int offsetX, int offsetY, int offsetZ, int socket,
                                boolean negative, String channelId) {
    }

    // Store the console channel
    private record ConsoleChannel(BlockEntity console, BlockPos offset, int socket, String moduleId,
                                  String moduleLabel, String channelId, String portId, String label,
                                  boolean button, int value) {
        // Check if this mapping controls an axis
        private boolean axis() {
            return !button;
        }
    }

    // Store the console channel pair
    private record ConsoleChannelPair(ConsoleChannel localX, ConsoleChannel localZ) {
    }

    // Store the method key
    private record MethodKey(String name, List<Class<?>> parameterTypes) {
    }
}
