package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.DocumentedPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.TermMethods;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Expose ACC Display controls and telemetry to ComputerCraft
@PeripheralTypeDoc("monitor")
public final class AccDisplayPeripheral extends TermMethods implements DocumentedPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<AccDisplayBlockEntity, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Root
    private final AccDisplayBlockEntity root;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display peripheral
    public AccDisplayPeripheral(AccDisplayBlockEntity display) {
        AccDisplayBlockEntity networkRoot = display.networkRoot();
        root = networkRoot == null ? display : networkRoot;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "monitor";
    }

    // Set the text scale
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setTextScale", signature = "setTextScale(requested: number)",
            description = "Sets the text scale.")
    public final void setTextScale(double requested) throws LuaException {
        if (!Double.isFinite(requested) || requested < 0.5D || requested > 5.0D) {
            throw new LuaException("Expected number in range 0.5-5");
        }
        State state = state();
        int scale = (int) (requested * 2.0D);
        if (state.textScale != scale) {
            state.textScale = scale;
            state.resizeForScale(root);
            state.computers.forEach(computer -> computer.queueEvent(
                    "monitor_resize", computer.getAttachmentName()));
        }
    }

    // Get the text scale
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTextScale", signature = "getTextScale(): number",
            description = "Returns the text scale.")
    public final double getTextScale() {
        return state().textScale * 0.5D;
    }

    // Get the terminal
    @Override
    public Terminal getTerminal() {
        return state().terminal;
    }

    // Attach the ACC display peripheral
    @Override
    public void attach(IComputerAccess computer) {
        state().computers.add(computer);
        root.queueDisplayRefresh();
    }

    // Detach the ACC display peripheral
    @Override
    public void detach(IComputerAccess computer) {
        state().computers.remove(computer);
        root.queueDisplayRefresh();
    }

    // Compare this ACC display peripheral with another object
    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof AccDisplayPeripheral peripheral
                && peripheral.root == root;
    }

    // Get the target
    @Override
    public Object getTarget() {
        return root;
    }

    // Get the frame
    public static CompoundTag frame(AccDisplayBlockEntity display) {
        AccDisplayBlockEntity root = display.networkRoot();
        if (root == null) root = display;
        State state = STATES.get(root);
        if (state == null || !state.computers.hasComputers()) {
            return new CompoundTag();
        }
        state.resize(root.networkWidth(), root.networkHeight(), root);
        NetworkedTerminal terminal = state.terminal;
        CompoundTag frame = new CompoundTag();
        frame.putString("Format", "terminal");
        frame.putString("Source", "CC:Tweaked");
        frame.putInt("Width", terminal.getWidth());
        frame.putInt("Height", terminal.getHeight());
        frame.putInt("CursorX", terminal.getCursorX());
        frame.putInt("CursorY", terminal.getCursorY());
        frame.putBoolean("CursorBlink", terminal.getCursorBlink());
        ListTag lines = new ListTag();
        ListTag foreground = new ListTag();
        ListTag background = new ListTag();
        for (int row = 0; row < terminal.getHeight(); row++) {
            lines.add(StringTag.valueOf(terminal.getLine(row).toString()));
            foreground.add(StringTag.valueOf(terminal.getTextColourLine(row).toString()));
            background.add(StringTag.valueOf(terminal.getBackgroundColourLine(row).toString()));
        }
        frame.put("Lines", lines);
        frame.put("Foreground", foreground);
        frame.put("Background", background);
        int[] palette = new int[16];
        for (int idx = 0; idx < palette.length; idx++) {
            palette[idx] = terminal.getPalette().getRenderColours(idx) | 0xFF000000;
        }
        frame.put("Palette", new IntArrayTag(palette));
        frame.put("Terminal", terminal.writeToNBT(new CompoundTag()));
        return frame;
    }

    // Handle an ACC display touch
    public static boolean touch(AccDisplayBlockEntity display, double x, double y) {
        AccDisplayBlockEntity root = display.networkRoot();
        if (root == null) root = display;
        State state = STATES.get(root);
        if (state == null || !state.computers.hasComputers()) return false;
        state.resize(root.networkWidth(), root.networkHeight(), root);
        int column = Mth.clamp((int) Math.floor(x * state.terminal.getWidth()) + 1,
                1, state.terminal.getWidth());
        int row = Mth.clamp((int) Math.floor(y * state.terminal.getHeight()) + 1,
                1, state.terminal.getHeight());
        state.computers.forEach(computer -> computer.queueEvent(
                "monitor_touch", computer.getAttachmentName(), column, row));
        return true;
    }

    // Submit display input
    public static boolean input(AccDisplayBlockEntity display, String action,
                                double x, double y, int val) {
        AccDisplayBlockEntity root = display.networkRoot();
        if (root == null) root = display;
        State state = STATES.get(root);
        if (state == null || !state.computers.hasComputers()) return false;
        state.resize(root.networkWidth(), root.networkHeight(), root);
        int column = Mth.clamp((int) Math.floor(x * state.terminal.getWidth()) + 1,
                1, state.terminal.getWidth());
        int row = Mth.clamp((int) Math.floor(y * state.terminal.getHeight()) + 1,
                1, state.terminal.getHeight());
        int computerButton = Mth.clamp(val + 1, 1, 3);
        switch (action) {
            case "click" -> state.computers.forEach(computer -> {
                computer.queueEvent("monitor_touch", computer.getAttachmentName(), column, row);
                computer.queueEvent("monitor_click", computer.getAttachmentName(), computerButton, column, row);
                computer.queueEvent("mouse_click", computerButton, column, row);
            });
            case "drag" -> state.computers.forEach(computer -> {
                computer.queueEvent("monitor_drag", computer.getAttachmentName(), computerButton, column, row);
                computer.queueEvent("mouse_drag", computerButton, column, row);
            });
            case "release" -> state.computers.forEach(computer -> {
                computer.queueEvent("monitor_up", computer.getAttachmentName(), computerButton, column, row);
                computer.queueEvent("mouse_up", computerButton, column, row);
            });
            case "char" -> state.computers.forEach(computer -> computer.queueEvent(
                    "char", String.valueOf((char) val)));
            case "key" -> state.computers.forEach(computer -> computer.queueEvent("key", val, false));
            case "key_up" -> state.computers.forEach(computer -> computer.queueEvent("key_up", val));
            default -> {
                return false;
            }
        }
        return true;
    }

    // Get the state
    private State state() {
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(root, ignored -> new State(root));
            return state;
        }
    }

    // Store the current state
    private static final class State {
        // Computers
        private final AttachedComputerSet computers = new AttachedComputerSet();
        // Current terminal
        private NetworkedTerminal terminal;
        // Text scale
        private int textScale = 2;
        // Current blocks wide
        private int blocksWide = 1;
        // Current blocks high
        private int blocksHigh = 1;

        // Initialize the state
        private State(AccDisplayBlockEntity root) {
            terminal = new NetworkedTerminal(1, 1, true, root::queueDisplayRefresh);
            resizeForScale(root);
        }

        // Resize the state
        private void resize(int blocksWide, int blocksHigh, AccDisplayBlockEntity root) {
            this.blocksWide = Math.max(1, blocksWide);
            this.blocksHigh = Math.max(1, blocksHigh);
            resizeForScale(root);
        }

        // Resize the scale
        private void resizeForScale(AccDisplayBlockEntity root) {
            double scale = textScale * 0.5D;
            int width = Math.max(1, (int) Math.round((blocksWide - 0.3125D)
                    / (scale * 6.0D * 0.015625D)));
            int height = Math.max(1, (int) Math.round((blocksHigh - 0.3125D)
                    / (scale * 9.0D * 0.015625D)));
            if (terminal.getWidth() != width || terminal.getHeight() != height) {
                terminal.resize(width, height);
                terminal.clear();
                computers.forEach(computer -> computer.queueEvent(
                        "monitor_resize", computer.getAttachmentName()));
                root.queueDisplayRefresh();
            }
        }
    }
}
