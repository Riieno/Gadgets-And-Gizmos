package com.rieno.gadgetsandgizmos.compat.computed;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Convert Computed widgets into display-adapter frames and route touch input back to their owner
public final class ComputedDisplayCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the computed display compat
    private ComputedDisplayCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the widgets
    public static boolean applyWidgets(
            BlockEntity computer, String target, List<?> definitions) {
        if (computer == null || computer.getLevel() == null) return false;
        Direction dir = endpointDirection(computer, target);
        if (dir == null) return false;
        BlockEntity targetEntity = computer.getLevel().getBlockEntity(
                computer.getBlockPos().relative(dir));
        if (targetEntity instanceof UniversalDisplayAdapterBlockEntity adapter) {
            CompoundTag frame = buildFrame(adapter.displayWidth(), adapter.displayHeight(),
                    computer.getBlockPos(), definitions);
            adapter.acceptInputFrame("computed", frame);
            return true;
        }
        return false;
    }

    // Handle a computed display touch
    public static boolean touch(UniversalDisplayAdapterBlockEntity adapter,
                                double horizontal, double vertical, int mouseButton) {
        if (adapter == null || adapter.getLevel() == null) {
            return false;
        }
        CompoundTag frame = adapter.externalFrame();
        if (!"computed".equals(frame.getString("Format")) || !frame.contains("Owner")) {
            return false;
        }
        if (mouseButton != 0) {
            return false;
        }
        double x = horizontal * Math.max(1, frame.getInt("Width"));
        double y = vertical * Math.max(1, frame.getInt("Height"));
        ListTag widgets = frame.getList("Widgets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int idx = widgets.size() - 1; idx >= 0; idx--) {
            CompoundTag widget = widgets.getCompound(idx);
            int left = widget.getInt("X");
            int top = widget.getInt("Y");
            int width = Math.max(1, widget.getInt("W"));
            int height = Math.max(1, widget.getInt("H"));
            if (x < left || y < top || x > left + width || y > top + height
                    || !widget.hasUUID("Id")) {
                continue;
            }
            String type = widget.getString("Type");
            double val;
            if ("button".equals(type)) {
                val = 1.0D;
            } else if ("slider".equals(type)) {
                CompoundTag properties = widget.getCompound("Properties");
                double minimum = properties.getDouble("minimum");
                double maximum = properties.getDouble("maximum");
                if (!(maximum > minimum)) maximum = minimum + 1.0D;
                val = minimum + Mth.clamp((x - left) / width, 0.0D, 1.0D)
                        * (maximum - minimum);
                double step = properties.getDouble("step");
                if (step > 0.0D && Double.isFinite(step)) {
                    val = minimum + Math.round((val - minimum) / step) * step;
                }
            } else {
                return false;
            }
            return touchAdapterOwner(adapter, BlockPos.of(frame.getLong("Owner")),
                    widget.getUUID("Id"), val);
        }
        return false;
    }

    // Send a touch to the adapter owner
    private static boolean touchAdapterOwner(UniversalDisplayAdapterBlockEntity adapter,
                                             BlockPos owner, UUID widgetId, double val) {
        BlockEntity computer = adapter.getLevel().getBlockEntity(owner);
        if (computer == null) return false;
        try {
            Object handled = computer.getClass()
                    .getMethod("handleWidgetInput", UUID.class, double.class)
                    .invoke(computer, widgetId, val);
            return handled instanceof Boolean res && res;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Build the frame
    private static CompoundTag buildFrame(
            int screenWidth, int screenHeight, BlockPos owner, List<?> definitions) {
        screenWidth = Math.max(1, screenWidth);
        screenHeight = Math.max(1, screenHeight);
        CompoundTag frame = new CompoundTag();
        frame.putString("Format", "computed");
        frame.putString("Source", "Computed");
        frame.putInt("Width", screenWidth);
        frame.putInt("Height", screenHeight);
        frame.putLong("Owner", owner.asLong());
        ListTag widgets = new ListTag();
        int lineCount = Math.max(1, screenHeight / 32);
        boolean[] occupied = new boolean[lineCount];
        if (definitions != null) {
            for (Object definition : definitions) {
                CompoundTag widget = readWidget(definition);
                if (widget.isEmpty()) continue;
                CompoundTag properties = widget.getCompound("Properties");
                if (!"manual".equalsIgnoreCase(properties.getString("layout_mode"))) {
                    int line = Math.max(1, (int) Math.round(properties.getDouble("line")));
                    int span = Math.max(1, (int) Math.round(properties.getDouble("span")));
                    int lineIndex = Math.min(lineCount, line) - 1;
                    int lineSpan = Math.max(1, Math.min(lineCount - lineIndex, span));
                    boolean conflict = false;
                    for (int idx = lineIndex; idx < lineIndex + lineSpan; idx++) {
                        if (occupied[idx]) conflict = true;
                    }
                    if (conflict) continue;
                    for (int idx = lineIndex; idx < lineIndex + lineSpan; idx++) {
                        occupied[idx] = true;
                    }
                    int availableHeight = Math.max(1, screenHeight - 8 * (lineCount + 1));
                    int lineHeight = Math.max(1, availableHeight / lineCount);
                    int y = 8 + lineIndex * (lineHeight + 8);
                    int spannedBottom = 8 + (lineIndex + lineSpan) * lineHeight
                            + (lineIndex + lineSpan - 1) * 8;
                    widget.putInt("X", 8);
                    widget.putInt("Y", y);
                    widget.putInt("W", Math.max(1, screenWidth - 16));
                    widget.putInt("H", Math.max(1,
                            Math.min(spannedBottom, screenHeight - 8) - y));
                }
                widgets.add(widget);
            }
        }
        frame.put("Widgets", widgets);
        return frame;
    }

    // Read the widget
    private static CompoundTag readWidget(Object definition) {
        if (definition == null) return new CompoundTag();
        try {
            Class<?> type = definition.getClass();
            String widgetType = String.valueOf(type.getMethod("type").invoke(definition));
            if (!List.of("text", "clock", "button", "slider", "progress").contains(widgetType)) {
                return new CompoundTag();
            }
            CompoundTag widget = new CompoundTag();
            Object id = type.getMethod("id").invoke(definition);
            if (id instanceof UUID uuid) widget.putUUID("Id", uuid);
            widget.putString("Type", widgetType);
            widget.putInt("X", integer(type, definition, "x"));
            widget.putInt("Y", integer(type, definition, "y"));
            widget.putInt("W", Math.max(1, integer(type, definition, "width")));
            widget.putInt("H", Math.max(1, integer(type, definition, "height")));
            widget.putInt("Color", integer(type, definition, "color"));
            Object rawProperties = type.getMethod("properties").invoke(definition);
            CompoundTag properties = new CompoundTag();
            if (rawProperties instanceof Map<?, ?> values) {
                values.forEach((key, val) -> putProperty(properties, String.valueOf(key), val));
            }
            widget.put("Properties", properties);
            return widget;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new CompoundTag();
        }
    }

    // Get the integer
    private static int integer(Class<?> type, Object target, String method)
            throws ReflectiveOperationException {
        Object val = type.getMethod(method).invoke(target);
        return val instanceof Number num ? num.intValue() : 0;
    }

    // Put the property
    private static void putProperty(CompoundTag target, String key, Object val) {
        if (val instanceof Number num) {
            target.putDouble(key, num.doubleValue());
        } else if (val instanceof Boolean flag) {
            target.putBoolean(key, flag);
        } else if (val != null) {
            target.putString(key, String.valueOf(val));
        }
    }

    // Get the endpoint direction
    private static Direction endpointDirection(BlockEntity computer, String target) {
        try {
            Method method = computer.getClass().getMethod("worldFaceForEndpoint", String.class);
            Object val = method.invoke(computer, target);
            return val instanceof Direction dir ? dir : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
