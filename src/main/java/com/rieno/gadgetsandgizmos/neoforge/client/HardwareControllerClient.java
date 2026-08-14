package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings;
import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerState;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;

// Read gamepad input and send only changed controller state to the server
public final class HardwareControllerClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int DEVICE_RESCAN_INTERVAL_TICKS = 40;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active device
    private static int activeDevice = -1;
    // Rescan tick count
    private static int rescanTicks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the hardware controller client
    private HardwareControllerClient() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the hardware controller client
    public static void tick() {
        if (!AnalogueContraptionControllerClientHandler.acceptsHardwareControllerInput()
                && !AnalogueJoystickClientHandler.acceptsHardwareControllerInput()) {
            activeDevice = -1;
            rescanTicks = 0;
            return;
        }
        HardwareControllerState state = pollController();
        Map<String, Double> values = HardwareControllerBindings.values(state);
        AnalogueContraptionControllerClientHandler.onHardwareControllerInput(values);
        AnalogueJoystickClientHandler.onHardwareControllerInput(values);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Poll the hardware controller
    private static HardwareControllerState pollController() {
        if (activeDevice >= GLFW.GLFW_JOYSTICK_1 && GLFW.glfwJoystickPresent(activeDevice)) {
            return GLFW.glfwJoystickIsGamepad(activeDevice)
                    ? standardizedState(activeDevice)
                    : rawState(activeDevice);
        }
        activeDevice = -1;
        if (rescanTicks-- > 0) {
            return null;
        }
        rescanTicks = DEVICE_RESCAN_INTERVAL_TICKS;
        activeDevice = findFirstController();
        if (activeDevice < 0) {
            return null;
        }
        return GLFW.glfwJoystickIsGamepad(activeDevice)
                ? standardizedState(activeDevice)
                : rawState(activeDevice);
    }

    // Find the first controller
    private static int findFirstController() {
        int fallback = -1;
        for (int device = GLFW.GLFW_JOYSTICK_1; device <= GLFW.GLFW_JOYSTICK_LAST; device++) {
            if (!GLFW.glfwJoystickPresent(device)) {
                continue;
            }
            if (GLFW.glfwJoystickIsGamepad(device)) {
                return device;
            }
            if (fallback < 0) {
                fallback = device;
            }
        }
        return fallback;
    }

    // Get the standardized state
    private static HardwareControllerState standardizedState(int device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState gamepad = GLFWGamepadState.malloc(stack);
            if (!GLFW.glfwGetGamepadState(device, gamepad)) {
                return rawState(device);
            }
            double[] axes = new double[6];
            for (int idx = 0; idx < axes.length; idx++) {
                axes[idx] = gamepad.axes(idx);
            }
            boolean[] buttons = new boolean[15];
            for (int idx = 0; idx < buttons.length; idx++) {
                buttons[idx] = gamepad.buttons(idx) == GLFW.GLFW_PRESS;
            }
            String name = GLFW.glfwGetGamepadName(device);
            return new HardwareControllerState(device, name, true, axes, buttons);
        }
    }

    // Get the raw state
    private static HardwareControllerState rawState(int device) {
        FloatBuffer axisBuffer = GLFW.glfwGetJoystickAxes(device);
        double[] axes = new double[axisBuffer == null ? 0
                : Math.min(axisBuffer.remaining(), HardwareControllerBindings.MAX_RAW_AXES)];
        if (axisBuffer != null) {
            int start = axisBuffer.position();
            for (int idx = 0; idx < axes.length; idx++) {
                axes[idx] = axisBuffer.get(start + idx);
            }
        }
        ByteBuffer buttonBuffer = GLFW.glfwGetJoystickButtons(device);
        boolean[] buttons = new boolean[buttonBuffer == null ? 0
                : Math.min(buttonBuffer.remaining(), HardwareControllerBindings.MAX_RAW_BUTTONS)];
        if (buttonBuffer != null) {
            int start = buttonBuffer.position();
            for (int idx = 0; idx < buttons.length; idx++) {
                buttons[idx] = buttonBuffer.get(start + idx) == GLFW.GLFW_PRESS;
            }
        }
        return new HardwareControllerState(device, GLFW.glfwGetJoystickName(device), false, axes, buttons);
    }
}
