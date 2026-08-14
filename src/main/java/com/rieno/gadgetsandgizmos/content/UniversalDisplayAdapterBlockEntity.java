package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.display.DisplayFrameEnvelope;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

// Convert supported display sources into one cached payload understood by ACC displays
public final class UniversalDisplayAdapterBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int DEFAULT_DISPLAY_WIDTH = 320;
    private static final int DEFAULT_DISPLAY_HEIGHT = 180;
    private static final int MAX_DISPLAY_DIMENSION = 4096;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current external frame
    private CompoundTag externalFrame = new CompoundTag();
    // Current direct frame
    private CompoundTag directFrame = new CompoundTag();
    // Current direct input
    private String directInput = "";
    // Current ACC display presentation
    private CompoundTag accDisplayPresentation = new CompoundTag();
    // Current ACC display presentation input
    private String accDisplayPresentationInput = "";
    // Tracked linked text
    private final List<String> linkedText = new ArrayList<>();
    // Current display width
    private int displayWidth = DEFAULT_DISPLAY_WIDTH;
    // Current display height
    private int displayHeight = DEFAULT_DISPLAY_HEIGHT;
    // Current frame revision
    private long frameRevision;
    // ComputerCraft frame action
    private final AtomicReference<ComputerCraftFrameAction> computerCraftFrameAction =
            new AtomicReference<>(ComputerCraftFrameAction.NONE);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the universal display adapter
    public UniversalDisplayAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state,
                                  UniversalDisplayAdapterBlockEntity adapter) {
        if (AccDisplayControllerRegistry.isStopping(level)) {
            adapter.computerCraftFrameAction.set(ComputerCraftFrameAction.NONE);
            return;
        }
        adapter.flushComputerCraftFrameAction();
    }

    // Get the external frame
    public CompoundTag externalFrame() {
        return externalFrame.copy();
    }

    // Get the ACC display frame
    public CompoundTag accDisplayFrame() {
        return DisplayFrameEnvelope.create(
                externalFrame, accDisplayPresentation, displayWidth, displayHeight);
    }

    // Get the requested ACC display mode
    public String requestedAccDisplayMode() {
        return DisplayFrameEnvelope.requestedMode(accDisplayPresentation);
    }

    // Get the frame revision
    public long frameRevision() {
        return frameRevision;
    }

    // Get the display link source frame
    public CompoundTag displayLinkSourceFrame() {
        return linkedText.isEmpty() ? new CompoundTag() : linkedTextFrame();
    }

    // Get the display width
    public int displayWidth() {
        return displayWidth;
    }

    // Get the display height
    public int displayHeight() {
        return displayHeight;
    }

    // Configure the display size
    public void configureDisplaySize(int width, int height) {
        int nextWidth = Mth.clamp(width, 1, MAX_DISPLAY_DIMENSION);
        int nextHeight = Mth.clamp(height, 1, MAX_DISPLAY_DIMENSION);
        if (displayWidth == nextWidth && displayHeight == nextHeight) {
            return;
        }
        displayWidth = nextWidth;
        displayHeight = nextHeight;
        notifyComputerCraftResize();
        flushComputerCraftFrameAction();
        setChanged();
        sendData();
    }

    // Accept the input frame
    public void acceptInputFrame(String input, CompoundTag frame) {
        if (level == null || level.isClientSide || input == null || input.isBlank()
                || frame == null || frame.isEmpty()) {
            return;
        }
        CompoundTag next = frame.copy();
        if (next.getString("Source").isBlank()) {
            next.putString("Source", input);
        }
        if (input.equals(directInput) && next.equals(directFrame)) {
            return;
        }
        directInput = input;
        directFrame = next;
        publishFrame(directFrame);
    }

    // Clear the input frame
    public void clearInputFrame(String input) {
        if (input == null || !input.equals(directInput)) {
            return;
        }
        directInput = "";
        directFrame = new CompoundTag();
        refreshSource();
    }

    // Accept the controller frame
    public void acceptControllerFrame(BlockEntity controller, CompoundTag frame) {
        acceptAccDisplayPresentation(controller, frame);
    }

    // Clear the controller frame
    public void clearControllerFrame(BlockEntity controller) {
        clearAccDisplayPresentation(controller);
    }

    // Accept the ACC display presentation
    public void acceptAccDisplayPresentation(BlockEntity controller, CompoundTag presentation) {
        if (controller == null || presentation == null || presentation.isEmpty()) {
            return;
        }
        String input = controllerInput(controller);
        CompoundTag next = presentation.copy();
        next.putString(DisplayFrameEnvelope.TARGET_MODE_KEY, "ship_information");
        if (input.equals(accDisplayPresentationInput)
                && next.equals(accDisplayPresentation)) {
            return;
        }
        accDisplayPresentationInput = input;
        accDisplayPresentation = next;
        publishPresentationChange();
    }

    // Clear the ACC display presentation
    public void clearAccDisplayPresentation(BlockEntity controller) {
        if (controller == null
                || !controllerInput(controller).equals(accDisplayPresentationInput)) {
            return;
        }
        accDisplayPresentationInput = "";
        accDisplayPresentation = new CompoundTag();
        publishPresentationChange();
    }

    // Queue the ComputerCraft frame
    public void queueComputerCraftFrame() {
        computerCraftFrameAction.set(ComputerCraftFrameAction.PUBLISH);
    }

    // Queue the ComputerCraft clear
    public void queueComputerCraftClear() {
        computerCraftFrameAction.set(ComputerCraftFrameAction.CLEAR);
    }

    // Handle the universal display adapter
    public boolean interact(double horizontal, double vertical, int mouseButton) {
        if ("computercraft".equals(directInput)
                && invokeDirectInteraction(
                "com.rieno.gadgetsandgizmos.compat.computercraft.UniversalDisplayAdapterPeripheral",
                horizontal, vertical, mouseButton)) {
            return true;
        }
        if ("computed".equals(directInput)
                && invokeDirectInteraction(
                "com.rieno.gadgetsandgizmos.compat.computed.ComputedDisplayCompat",
                horizontal, vertical, mouseButton)) {
            return true;
        }
        BlockEntity src = sourceBlockEntity();
        return interactSource(src, horizontal, vertical,
                "universal_display_adapter", mouseButton);
    }

    // Submit display input
    public boolean input(String action, double horizontal, double vertical, int val) {
        if ("computercraft".equals(directInput)
                && invokeDirectInput(
                "com.rieno.gadgetsandgizmos.compat.computercraft.UniversalDisplayAdapterPeripheral",
                action, horizontal, vertical, val)) {
            return true;
        }
        return inputSource(sourceBlockEntity(), action, horizontal, vertical,
                "universal_display_adapter", val);
    }

    // Accept the display link text
    public void acceptDisplayLinkText(int line, List<MutableComponent> text) {
        int firstLine = Math.max(0, line);
        int required = firstLine + (text == null ? 0 : text.size());
        while (linkedText.size() < required) {
            linkedText.add("");
        }
        if (text != null) {
            for (int idx = 0; idx < text.size(); idx++) {
                linkedText.set(firstLine + idx, text.get(idx).getString());
            }
        }
        refreshSource();
        AccDisplayControllerRegistry.markDisplayFramesDirty(level);
    }

    // Refresh the source
    private void refreshSource() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!directFrame.isEmpty()) {
            if (directFrameIsAvailable()) {
                publishFrame(directFrame);
                return;
            }
            directInput = "";
            directFrame = new CompoundTag();
        }
        BlockEntity src = sourceBlockEntity();
        CompoundTag next = terminalFrame(src);
        if (next.isEmpty() && !linkedText.isEmpty()) {
            next = linkedTextFrame();
        }
        publishFrame(next);
    }

    // Check if the direct frame is available
    private boolean directFrameIsAvailable() {
        if (directInput.startsWith("ship_information:")) {
            return false;
        }
        if (!"computed".equals(directInput) || !directFrame.contains("Owner")) {
            return true;
        }
        return level != null && level.getBlockEntity(BlockPos.of(directFrame.getLong("Owner"))) != null;
    }

    // Publish the frame
    private void publishFrame(CompoundTag frame) {
        CompoundTag next = frame == null ? new CompoundTag() : frame.copy();
        if (next.equals(externalFrame)) {
            return;
        }
        externalFrame = next;
        frameRevision++;
        setChanged();
        sendData();
        notifyAdjacentDisplays(false);
        AccDisplayControllerRegistry.markDisplayFramesDirty(level);
    }

    // Publish the presentation change
    private void publishPresentationChange() {
        frameRevision++;
        setChanged();
        sendData();
        notifyAdjacentDisplays(true);
    }

    // Get the linked text frame
    private CompoundTag linkedTextFrame() {
        CompoundTag frame = new CompoundTag();
        frame.putString("Format", "text");
        frame.putString("Source", "Display Link");
        ListTag lines = new ListTag();
        linkedText.forEach(line -> lines.add(StringTag.valueOf(line)));
        frame.put("Lines", lines);
        frame.putInt("Width", linkedText.stream().mapToInt(String::length).max().orElse(1));
        frame.putInt("Height", Math.max(1, linkedText.size()));
        return frame;
    }

    // Get the controller input
    private static String controllerInput(BlockEntity controller) {
        java.util.UUID subLevelId = SimulatedHelper.getContainingSubLevelId(controller);
        return "ship_information:" + (subLevelId == null ? "world" : subLevelId)
                + ":" + controller.getBlockPos().asLong();
    }

    // Notify the adjacent displays
    private void notifyAdjacentDisplays(boolean applyRequestedMode) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockEntity candidate = level.getBlockEntity(worldPosition.relative(dir));
            if (candidate instanceof AccDisplayBlockEntity display) {
                String mode = requestedAccDisplayMode();
                if (applyRequestedMode && !mode.isBlank()) {
                    display.requestDisplayPresentation(mode);
                }
                display.requestDisplayRefresh();
            }
        }
    }

    // Initialize the universal display adapter
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            refreshSource();
            AccDisplayControllerRegistry.markDisplayTargetsDirty(level);
        }
    }

    // Remove the universal display adapter
    @Override
    public void remove() {
        Level currentLevel = level;
        List<AccDisplayBlockEntity> adjacentDisplays = new ArrayList<>();
        if (currentLevel != null && !currentLevel.isClientSide) {
            for (Direction dir : Direction.values()) {
                BlockEntity candidate = currentLevel.getBlockEntity(
                        worldPosition.relative(dir));
                if (candidate instanceof AccDisplayBlockEntity display) {
                    adjacentDisplays.add(display);
                }
            }
        }
        super.remove();
        if (currentLevel != null && currentLevel.getServer() != null) {
            currentLevel.getServer().execute(() -> {
                AccDisplayControllerRegistry.markDisplayTargetsDirty(currentLevel);
                adjacentDisplays.forEach(AccDisplayBlockEntity::requestDisplayRefresh);
            });
        }
    }

    // Run the direct interaction
    private boolean invokeDirectInteraction(String className, double horizontal,
                                            double vertical, int mouseButton) {
        try {
            Object handled = Class.forName(className)
                    .getMethod("touch", UniversalDisplayAdapterBlockEntity.class,
                            double.class, double.class, int.class)
                    .invoke(null, this, horizontal, vertical, mouseButton);
            return handled instanceof Boolean res && res;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Run the direct input
    private boolean invokeDirectInput(String className, String action, double horizontal,
                                      double vertical, int val) {
        try {
            Object handled = Class.forName(className)
                    .getMethod("input", UniversalDisplayAdapterBlockEntity.class,
                            String.class, double.class, double.class, int.class)
                    .invoke(null, this, action, horizontal, vertical, val);
            return handled instanceof Boolean res && res;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Notify the ComputerCraft resize
    private void notifyComputerCraftResize() {
        try {
            Class.forName("com.rieno.gadgetsandgizmos.compat.computercraft.UniversalDisplayAdapterPeripheral")
                    .getMethod("resize", UniversalDisplayAdapterBlockEntity.class)
                    .invoke(null, this);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    // Flush the ComputerCraft frame action
    private void flushComputerCraftFrameAction() {
        ComputerCraftFrameAction action = computerCraftFrameAction.getAndSet(
                ComputerCraftFrameAction.NONE);
        if (action == ComputerCraftFrameAction.NONE) {
            return;
        }
        if (action == ComputerCraftFrameAction.CLEAR) {
            clearInputFrame("computercraft");
            return;
        }
        try {
            Class.forName("com.rieno.gadgetsandgizmos.compat.computercraft.UniversalDisplayAdapterPeripheral")
                    .getMethod("publish", UniversalDisplayAdapterBlockEntity.class)
                    .invoke(null, this);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    // Get the source block entity
    BlockEntity sourceBlockEntity() {
        if (level == null) {
            return null;
        }
        Direction facing = getBlockState().getValue(UniversalDisplayAdapterBlock.FACING);
        return level.getBlockEntity(worldPosition.relative(facing.getOpposite()));
    }

    // Get the terminal frame
    static CompoundTag terminalFrame(BlockEntity src) {
        if (src == null) {
            return new CompoundTag();
        }
        Object terminal = findTerminal(src, 0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (terminal == null) {
            return new CompoundTag();
        }
        try {
            int width = number(invoke(terminal, "getWidth"));
            int height = number(invoke(terminal, "getHeight"));
            if (width <= 0 || height <= 0 || width > 512 || height > 256) {
                return new CompoundTag();
            }
            CompoundTag frame = new CompoundTag();
            frame.putString("Format", "terminal");
            frame.putString("Source", src.getBlockState().getBlock().getName().getString());
            frame.putInt("Width", width);
            frame.putInt("Height", height);
            ListTag lines = new ListTag();
            ListTag foreground = new ListTag();
            ListTag background = new ListTag();
            for (int y = 0; y < height; y++) {
                lines.add(StringTag.valueOf(String.valueOf(invoke(terminal, "getLine", y))));
                foreground.add(StringTag.valueOf(optionalLine(terminal, "getTextColourLine", y, width, '0')));
                background.add(StringTag.valueOf(optionalLine(terminal, "getBackgroundColourLine", y, width, 'f')));
            }
            frame.put("Lines", lines);
            frame.put("Foreground", foreground);
            frame.put("Background", background);
            return frame;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new CompoundTag();
        }
    }

    // Handle source interaction
    static boolean interactSource(BlockEntity src, double horizontal, double vertical,
                                  String attachmentName, int mouseButton) {
        if (src == null) {
            return false;
        }
        CompoundTag frame = terminalFrame(src);
        int width = frame.getInt("Width");
        int height = frame.getInt("Height");
        if (width <= 0 || height <= 0) {
            return false;
        }
        int column = Mth.clamp((int) Math.floor(horizontal * width) + 1, 1, width);
        int row = Mth.clamp((int) Math.floor(vertical * height) + 1, 1, height);
        Object receiver = findEventReceiver(src, 0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (receiver == null) {
            return false;
        }
        String receiverName = receiver.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        try {
            int computerButton = Mth.clamp(mouseButton + 1, 1, 3);
            if (mouseButton == 0 && (receiverName.contains("attachedcomputerset")
                    || receiverName.contains("monitor"))) {
                queueEvent(receiver, "monitor_touch",
                        new Object[]{attachmentName, column, row});
            } else {
                queueEvent(receiver, "mouse_click", new Object[]{computerButton, column, row});
                queueEvent(receiver, "mouse_up", new Object[]{computerButton, column, row});
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Submit input through the display source
    static boolean inputSource(BlockEntity src, String action, double horizontal,
                               double vertical, String attachmentName, int val) {
        if (src == null || action == null) return false;
        CompoundTag frame = terminalFrame(src);
        int width = frame.getInt("Width");
        int height = frame.getInt("Height");
        if (width <= 0 || height <= 0) return false;
        int column = Mth.clamp((int) Math.floor(horizontal * width) + 1, 1, width);
        int row = Mth.clamp((int) Math.floor(vertical * height) + 1, 1, height);
        Object receiver = findEventReceiver(src, 0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (receiver == null) return false;
        String receiverName = receiver.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        boolean monitor = receiverName.contains("attachedcomputerset")
                || receiverName.contains("monitor");
        int computerButton = Mth.clamp(val + 1, 1, 3);
        try {
            switch (action) {
                case "click" -> {
                    if (monitor) {
                        queueEvent(receiver, "monitor_touch",
                                new Object[]{attachmentName, column, row});
                        queueEvent(receiver, "monitor_click",
                                new Object[]{attachmentName, computerButton, column, row});
                    } else {
                        queueEvent(receiver, "mouse_click",
                                new Object[]{computerButton, column, row});
                    }
                }
                case "drag" -> queueEvent(receiver, monitor ? "monitor_drag" : "mouse_drag",
                        monitor ? new Object[]{attachmentName, computerButton, column, row}
                                : new Object[]{computerButton, column, row});
                case "release" -> queueEvent(receiver, monitor ? "monitor_up" : "mouse_up",
                        monitor ? new Object[]{attachmentName, computerButton, column, row}
                                : new Object[]{computerButton, column, row});
                case "char" -> queueEvent(receiver, "char",
                        new Object[]{String.valueOf((char) val)});
                case "key" -> queueEvent(receiver, "key", new Object[]{val, false});
                case "key_up" -> queueEvent(receiver, "key_up", new Object[]{val});
                default -> {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Find the event receiver
    private static Object findEventReceiver(Object val, int depth, Set<Object> visited) {
        if (val == null || depth > 4 || !visited.add(val)) {
            return null;
        }
        if (hasMethod(val.getClass(), "queueEvent", String.class, Object[].class)) {
            return val;
        }
        for (String method : List.of("getServerComputer", "createServerComputer",
                "getComputer", "getServerMonitor", "getMonitor")) {
            try {
                Object receiver = findEventReceiver(invoke(val, method), depth + 1, visited);
                if (receiver != null) {
                    return receiver;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        for (Class<?> type = val.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("computer") && !name.contains("monitor")
                        && !name.contains("event")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object receiver = findEventReceiver(field.get(val), depth + 1, visited);
                    if (receiver != null) {
                        return receiver;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        return null;
    }

    // Queue the event
    private static void queueEvent(Object receiver, String evt, Object[] args)
            throws ReflectiveOperationException {
        Method method = findMethod(receiver.getClass(), "queueEvent", String.class, Object[].class);
        method.setAccessible(true);
        method.invoke(receiver, evt, args);
    }

    // Find the terminal
    private static Object findTerminal(Object val, int depth, Set<Object> visited) {
        if (val == null || depth > 4 || !visited.add(val)) {
            return null;
        }
        if (hasMethod(val.getClass(), "getWidth")
                && hasMethod(val.getClass(), "getHeight")
                && hasMethod(val.getClass(), "getLine", int.class)) {
            return val;
        }
        for (String method : List.of("getTerminal", "getServerComputer", "createServerComputer",
                "getServerMonitor", "getMonitor", "getComputer", "getScheduler")) {
            try {
                Object child = invoke(val, method);
                Object terminal = findTerminal(child, depth + 1, visited);
                if (terminal != null) {
                    return terminal;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        for (Class<?> type = val.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("terminal") && !name.contains("computer")
                        && !name.contains("monitor") && !name.contains("screen")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object terminal = findTerminal(field.get(val), depth + 1, visited);
                    if (terminal != null) {
                        return terminal;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        return null;
    }

    // Get the optional line
    private static String optionalLine(Object terminal, String method, int line,
                                       int width, char fallback) {
        try {
            return String.valueOf(invoke(terminal, method, line));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return String.valueOf(fallback).repeat(width);
        }
    }

    // Run the universal display adapter
    private static Object invoke(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        Class<?>[] types = new Class<?>[args.length];
        for (int idx = 0; idx < args.length; idx++) {
            types[idx] = args[idx] instanceof Integer ? int.class : args[idx].getClass();
        }
        Method method = findMethod(target.getClass(), name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    // Check if this has method
    private static boolean hasMethod(Class<?> type, String name, Class<?>... args) {
        try {
            findMethod(type, name, args);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Find the method
    private static Method findMethod(Class<?> type, String name, Class<?>... args)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, args);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    // Read the numeric value
    private static int number(Object val) {
        return val instanceof Number num ? num.intValue() : 0;
    }

    // Define the ComputerCraft frame action values
    private enum ComputerCraftFrameAction {
        NONE,
        PUBLISH,
        CLEAR
    }

    // Write the universal display adapter
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("DisplayWidth", displayWidth);
        tag.putInt("DisplayHeight", displayHeight);
        if (!clientPacket) {
            tag.put("ExternalFrame", externalFrame.copy());
            tag.put("DirectFrame", directFrame.copy());
            tag.putString("DirectInput", directInput);
            ListTag text = new ListTag();
            linkedText.forEach(line -> text.add(StringTag.valueOf(line)));
            tag.put("LinkedText", text);
        }
    }

    // Read the universal display adapter
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (tag.contains("ExternalFrame", Tag.TAG_COMPOUND)) {
            externalFrame = tag.getCompound("ExternalFrame").copy();
            directFrame = tag.getCompound("DirectFrame").copy();
            directInput = tag.getString("DirectInput");
        }
        displayWidth = Mth.clamp(tag.contains("DisplayWidth")
                ? tag.getInt("DisplayWidth") : DEFAULT_DISPLAY_WIDTH, 1, MAX_DISPLAY_DIMENSION);
        displayHeight = Mth.clamp(tag.contains("DisplayHeight")
                ? tag.getInt("DisplayHeight") : DEFAULT_DISPLAY_HEIGHT, 1, MAX_DISPLAY_DIMENSION);
        if (tag.contains("LinkedText", Tag.TAG_LIST)) {
            linkedText.clear();
            ListTag text = tag.getList("LinkedText", Tag.TAG_STRING);
            for (int idx = 0; idx < text.size(); idx++) {
                linkedText.add(text.getString(idx));
            }
        }
    }
}
