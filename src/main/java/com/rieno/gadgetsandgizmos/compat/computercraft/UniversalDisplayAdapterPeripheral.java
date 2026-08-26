package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.DocumentedPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.TermMethods;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Expose Universal Display Adapter controls and telemetry to ComputerCraft
@PeripheralTypeDoc("monitor")
public final class UniversalDisplayAdapterPeripheral extends TermMethods implements DocumentedPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String INPUT_ID = "computercraft";
    private static final Map<UniversalDisplayAdapterBlockEntity, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Universal display adapter peripheral adapter
    private final UniversalDisplayAdapterBlockEntity adapter;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the universal display adapter peripheral
    public UniversalDisplayAdapterPeripheral(UniversalDisplayAdapterBlockEntity adapter) {
        this.adapter = adapter;
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
        state.setTextScale(scale);
    }

    // Get the text scale
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTextScale", signature = "getTextScale(): number",
            description = "Returns the text scale.")
    public final double getTextScale() {
        return state().textScale() * 0.5D;
    }

    // Get the terminal
    @Override
    public Terminal getTerminal() {
        State state = state();
        state.resize();
        return state.terminal;
    }

    // Attach the universal display adapter peripheral
    @Override
    public void attach(IComputerAccess computer) {
        State state = state();
        state.computers.add(computer);
        state.resize();
        adapter.queueComputerCraftFrame();
    }

    // Detach the universal display adapter peripheral
    @Override
    public void detach(IComputerAccess computer) {
        State state = state();
        state.computers.remove(computer);
        if (state.computers.hasComputers()) {
            adapter.queueComputerCraftFrame();
        } else {
            adapter.queueComputerCraftClear();
        }
    }

    // Compare this universal display adapter peripheral with another object
    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof UniversalDisplayAdapterPeripheral peripheral
                && peripheral.adapter == adapter;
    }

    // Get the target
    @Override
    public Object getTarget() {
        return adapter;
    }

    // Resize the universal display adapter peripheral
    public static void resize(UniversalDisplayAdapterBlockEntity adapter) {
        State state = STATES.get(adapter);
        if (state != null) {
            state.resize();
        }
    }

    // Publish the universal display adapter peripheral
    public static void publish(UniversalDisplayAdapterBlockEntity adapter) {
        State state = STATES.get(adapter);
        if (state != null && state.computers.hasComputers()) {
            state.publishNow();
        } else {
            adapter.clearInputFrame(INPUT_ID);
        }
    }

    // Handle a display adapter touch
    public static boolean touch(UniversalDisplayAdapterBlockEntity adapter,
                                double horizontal, double vertical, int mouseButton) {
        State state = STATES.get(adapter);
        if (state == null || !state.computers.hasComputers()) {
            return false;
        }
        state.resize();
        int column = Mth.clamp((int) Math.floor(horizontal * state.terminal.getWidth()) + 1,
                1, state.terminal.getWidth());
        int row = Mth.clamp((int) Math.floor(vertical * state.terminal.getHeight()) + 1,
                1, state.terminal.getHeight());
        int computerButton = Mth.clamp(mouseButton + 1, 1, 3);
        state.computers.forEach(computer -> {
            if (mouseButton == 0) {
                computer.queueEvent("monitor_touch", computer.getAttachmentName(), column, row);
            } else {
                computer.queueEvent("mouse_click", computerButton, column, row);
                computer.queueEvent("mouse_up", computerButton, column, row);
            }
        });
        return true;
    }

    // Submit display input
    public static boolean input(UniversalDisplayAdapterBlockEntity adapter, String action,
                                double horizontal, double vertical, int val) {
        State state = STATES.get(adapter);
        if (state == null || !state.computers.hasComputers()) return false;
        state.resize();
        int column = Mth.clamp((int) Math.floor(horizontal * state.terminal.getWidth()) + 1,
                1, state.terminal.getWidth());
        int row = Mth.clamp((int) Math.floor(vertical * state.terminal.getHeight()) + 1,
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
            case "key" -> state.computers.forEach(computer -> computer.queueEvent(
                    "key", val, false));
            case "key_up" -> state.computers.forEach(computer -> computer.queueEvent(
                    "key_up", val));
            default -> {
                return false;
            }
        }
        return true;
    }

    // Get the state
    private State state() {
        synchronized (STATES) {
            return STATES.computeIfAbsent(adapter, State::new);
        }
    }

    // Store the current state
    private static final class State {
        // State adapter
        private final UniversalDisplayAdapterBlockEntity adapter;
        // Computers
        private final AttachedComputerSet computers = new AttachedComputerSet();
        // Terminal
        private final NetworkedTerminal terminal;
        // Text scale
        private int textScale = 2;
        // Applied text scale
        private int appliedTextScale;
        // Current pixel width
        private int pixelWidth;
        // Current pixel height
        private int pixelHeight;

        // Initialize the state
        private State(UniversalDisplayAdapterBlockEntity adapter) {
            this.adapter = adapter;
            terminal = new NetworkedTerminal(1, 1, true,
                    adapter::queueComputerCraftFrame);
            resize();
        }

        // Set the text scale
        private synchronized void setTextScale(int scale) {
            if (textScale == scale) {
                return;
            }
            textScale = scale;
            resize();
        }

        // Get the text scale
        private synchronized int textScale() {
            return textScale;
        }

        // Resize the state
        private synchronized void resize() {
            int nextPixelWidth = adapter.displayWidth();
            int nextPixelHeight = adapter.displayHeight();
            if (pixelWidth == nextPixelWidth && pixelHeight == nextPixelHeight
                    && appliedTextScale == textScale
                    && terminal.getWidth() > 1 && terminal.getHeight() > 1) {
                return;
            }
            pixelWidth = nextPixelWidth;
            pixelHeight = nextPixelHeight;
            appliedTextScale = textScale;
            double scale = textScale * 0.5D;
            int width = Math.max(1, (int) Math.floor(pixelWidth / (scale * 6.0D)));
            int height = Math.max(1, (int) Math.floor(pixelHeight / (scale * 9.0D)));
            if (terminal.getWidth() == width && terminal.getHeight() == height) {
                return;
            }
            terminal.resize(width, height);
            terminal.clear();
            computers.forEach(computer -> computer.queueEvent(
                    "monitor_resize", computer.getAttachmentName()));
            adapter.queueComputerCraftFrame();
        }

        // Publish the now
        private void publishNow() {
            if (!computers.hasComputers()) {
                return;
            }
            CompoundTag frame = new CompoundTag();
            frame.putString("Format", "terminal");
            frame.putString("Source", "CC:Tweaked");
            frame.putInt("Width", terminal.getWidth());
            frame.putInt("Height", terminal.getHeight());
            frame.putInt("PixelWidth", adapter.displayWidth());
            frame.putInt("PixelHeight", adapter.displayHeight());
            frame.putDouble("TextScale", textScale * 0.5D);
            frame.put("Terminal", terminal.writeToNBT(new CompoundTag()));
            adapter.acceptInputFrame(INPUT_ID, frame);
        }
    }
}
