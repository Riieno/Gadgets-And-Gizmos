package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.LecternPortableContraptionController;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerMouseInputPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerPhysicalInteractionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ControllerRuntimeSyncPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.LecternPortableContraptionControllerKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.LecternPortableContraptionControllerModePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.HardwareControllerInputPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PortableContraptionControllerKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PortableContraptionControllerModePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PortableContraptionControllerOpenPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Own the local controller session and send input only while its target remains valid
public final class AnalogueContraptionControllerClientHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String STEP_DOWN_KEY_SUFFIX = "#step_down";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active controller pos
    private static BlockPos activeControllerPos;
    // Active controller sub-level id
    private static UUID activeControllerSubLevelId;
    // Tracks whether controller remote is active
    private static boolean activeControllerRemote;
    // Tracks whether portable is active
    private static boolean activePortable;
    // Tracks whether portable advanced is active
    private static boolean activePortableAdvanced;
    // Active portable hand
    private static InteractionHand activePortableHand = InteractionHand.MAIN_HAND;
    // Active lectern pos
    private static BlockPos activeLecternPos;
    // Tracks whether lectern advanced is active
    private static boolean activeLecternAdvanced;
    private static final Set<String> pressedChannelIds = new HashSet<>();
    // Active controller
    private static AnalogueContraptionControllerBlockEntity activeController;
    // Active portable controller
    private static AnalogueContraptionControllerBlockEntity activePortableController;
    // Active lectern controller
    private static AnalogueContraptionControllerBlockEntity activeLecternController;
    // Tracked suppressed key codes
    private static Set<Integer> suppressedKeyCodes = Set.of();
    // Tracks whether gate use input until release is set
    private static boolean gateUseInputUntilRelease;
    // Last sent mouse x
    private static double lastSentMouseX = Double.NaN;
    // Last sent mouse y
    private static double lastSentMouseY = Double.NaN;
    // Last hardware controller values
    private static Map<String, Double> lastHardwareControllerValues = Map.of();
    // Last hardware controller send tick
    private static long lastHardwareControllerSendTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller client handler
    private AnalogueContraptionControllerClientHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the runtime signal
    public static void applyRuntimeSignal(ControllerRuntimeSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || minecraft.level == null) {
            return;
        }
        AnalogueContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                minecraft.level,
                payload.subLevelId(),
                payload.pos(),
                AnalogueContraptionControllerBlockEntity.class);
        if (controller != null) {
            controller.applyClientRuntimeSignal(payload.outputSignal());
        }
    }

    // Apply the advanced runtime
    public static void applyAdvancedRuntime(AdvancedControllerRuntimePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        AdvancedContraptionControllerBlockEntity controller;
        if (payload.containerId() >= 0) {
            if (minecraft.player.containerMenu.containerId != payload.containerId()
                    || !(minecraft.player.containerMenu instanceof AdvancedContraptionControllerMenu menu)) {
                return;
            }
            controller = menu.getMenuConfigTargetBlockEntity();
        } else {
            controller = SimulatedHelper.findBlockEntity(
                    minecraft.level,
                    payload.subLevelId(),
                    payload.pos(),
                    AdvancedContraptionControllerBlockEntity.class);
            if (controller == null) {
                controller = CTPhysicsGogglesClient.resolvePortableBoundController(
                        minecraft, payload.gogglesTrackerPairs().keySet());
            }
        }
        if (controller != null) {
            applyCachedAdvancedGraphSnapshot(
                    controller, payload.pos(), payload.subLevelId());
            controller.applyClientGraphRuntime(
                    payload.gogglesTrackerPairs(),
                    payload.liveInputs(), payload.liveOutputs(), payload.executionPulses());
        }
    }

    // Apply the advanced graph snapshot
    public static void applyAdvancedGraphSnapshot(
            BlockPos pos,
            UUID subLevelId,
            AdvancedControllerGraphSnapshotPayload.GraphSnapshot snapshot
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        AdvancedContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                minecraft.level, subLevelId, pos,
                AdvancedContraptionControllerBlockEntity.class);
        if (controller == null) {
            controller = CTPhysicsGogglesClient.resolvePortableBoundController(
                    minecraft, null);
        }
        if (controller != null) {
            applyAdvancedGraphSnapshot(controller, snapshot);
        }
    }

    // Apply the cached advanced graph snapshot
    public static void applyCachedAdvancedGraphSnapshot(
            AdvancedContraptionControllerBlockEntity controller,
            BlockPos pos,
            UUID subLevelId
    ) {
        AdvancedControllerGraphSnapshotPayload.GraphSnapshot snapshot =
                AdvancedControllerGraphSnapshotPayload.latestClientSnapshot(pos, subLevelId);
        if (snapshot != null) {
            applyAdvancedGraphSnapshot(controller, snapshot);
        }
    }

    // Apply the advanced graph snapshot
    private static void applyAdvancedGraphSnapshot(
            AdvancedContraptionControllerBlockEntity controller,
            AdvancedControllerGraphSnapshotPayload.GraphSnapshot snapshot
    ) {
        if (controller.hasClientGraphSnapshot(
                snapshot.draftRevision(), snapshot.activeRevision())) {
            return;
        }
        controller.applyClientGraphSnapshot(
                snapshot.draftRevision(),
                snapshot.activeRevision(),
                AdvancedGraphDocument.fromTag(snapshot.draft()),
                AdvancedGraphDocument.fromTag(snapshot.active()));
    }

    // Toggle controller interaction mode
    public static void toggleInteractMode(AnalogueContraptionControllerBlockEntity controller) {
        if (controller == null) {
            return;
        }

        BlockPos controllerPos = controller.getBlockPos();
        if (activeControllerPos != null && activeControllerPos.equals(controllerPos)) {
            stopInteractMode(true);
            return;
        }

        startBlockInteractMode(controller, false);
    }

    // Start the block interact mode
    private static void startBlockInteractMode(AnalogueContraptionControllerBlockEntity controller,
                                               boolean remote) {
        BlockPos controllerPos = controller.getBlockPos();
        stopInteractMode(false);
        activeControllerPos = controllerPos.immutable();
        activeControllerSubLevelId = SimulatedHelper.getContainingSubLevelId(controller);
        activeController = controller;
        activeControllerRemote = remote;
        resetMouseSendState();
        refreshSuppressedKeyCodes(controller);
        armUseInputGate();
        releaseSuppressedMappings();
        if (controller instanceof AdvancedContraptionControllerBlockEntity) {
            PacketDistributor.sendToServer(new AdvancedControllerPhysicalInteractionPayload(
                    activeControllerPos, activeControllerSubLevelId, true, remote, "use"));
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("Controller interact mode enabled"), true);
        }
    }

    // Toggle portable controller interaction mode
    public static void togglePortableInteractMode(boolean advanced, InteractionHand hand) {
        if (activePortable && activePortableAdvanced == advanced && activePortableHand == hand) {
            stopInteractMode(true);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        AnalogueContraptionControllerBlockEntity controller = portableController(minecraft, hand, advanced);
        if (controller == null) {
            return;
        }

        stopInteractMode(false);
        activePortable = true;
        activePortableAdvanced = advanced;
        activePortableHand = hand;
        activePortableController = controller;
        resetMouseSendState();
        PacketDistributor.sendToServer(new PortableContraptionControllerModePayload(hand, advanced, true));
        refreshSuppressedKeyCodes(controller);
        armUseInputGate();
        releaseSuppressedMappings();
        minecraft.player.displayClientMessage(Component.literal("Portable controller interact mode enabled"), true);
    }

    // Toggle lectern controller interaction mode
    public static void toggleLecternPortableInteractMode(BlockPos pos, boolean advanced) {
        if (pos == null) {
            return;
        }
        if (activeLecternPos != null && activeLecternPos.equals(pos) && activeLecternAdvanced == advanced) {
            stopInteractMode(true);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        AnalogueContraptionControllerBlockEntity controller = lecternController(minecraft, pos, advanced);
        if (controller == null) {
            return;
        }

        stopInteractMode(false);
        activeLecternPos = pos.immutable();
        activeLecternAdvanced = advanced;
        activeLecternController = controller;
        resetMouseSendState();
        PacketDistributor.sendToServer(new LecternPortableContraptionControllerModePayload(
                activeLecternPos, activeLecternAdvanced, true));
        refreshSuppressedKeyCodes(controller);
        armUseInputGate();
        releaseSuppressedMappings();
        minecraft.player.displayClientMessage(Component.literal("Lectern controller interact mode enabled"), true);
    }

    // Clear the lectern portable interact mode
    public static void clearLecternPortableInteractMode(BlockPos pos) {
        if (pos != null && activeLecternPos != null && activeLecternPos.equals(pos)) {
            stopInteractMode(false);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the analogue contraption controller client handler
    public static void tick() {
        PortableContraptionControllerItemRenderer.tick();
        if (activeControllerPos == null && !activePortable && activeLecternPos == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.screen != null) {
            stopInteractMode(false);
            return;
        }

        if (activePortable) {
            ItemStack stack = player.getItemInHand(activePortableHand);
            if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)
                    || portable.isAdvanced() != activePortableAdvanced
                    || activePortableController == null) {
                stopInteractMode(false);
                return;
            }
            activePortableController.tickClientAnimation();
            releaseSuppressedMappings();
            return;
        }

        if (activeLecternPos != null) {
            ItemStack stack = LecternPortableContraptionController.getControllerStack(
                    minecraft.level, activeLecternPos);
            if (!LecternPortableContraptionController.isMatchingPortable(stack, activeLecternAdvanced)
                    || activeLecternController == null) {
                stopInteractMode(false);
                return;
            }
            activeLecternController.tickClientAnimation();
            releaseSuppressedMappings();
            double range = player.blockInteractionRange();
            if (player.distanceToSqr(activeLecternPos.getCenter()) > range * range) {
                stopInteractMode(false);
            }
            return;
        }

        AnalogueContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
            minecraft.level, activeControllerSubLevelId, activeControllerPos, AnalogueContraptionControllerBlockEntity.class);
        if (controller == null) {
            stopInteractMode(false);
            return;
        }

        if (controller != activeController) {
            activeController = controller;
            refreshSuppressedKeyCodes(controller);
        }
        releaseSuppressedMappings();

        double range = player.blockInteractionRange();
        if (!activeControllerRemote && SimulatedHelper.distanceSquaredWithSubLevels(
                minecraft.level, player.getEyePosition(), activeControllerPos.getCenter()) > range * range) {
            stopInteractMode(false);
        }
    }

    // Handle the key input
    public static boolean onKeyInput(int key, int scanCode, int action) {
        if (activeControllerPos == null && !activePortable && activeLecternPos == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stopInteractMode(false);
            return false;
        }
        AnalogueContraptionControllerBlockEntity controller;
        if (activePortable) {
            controller = activePortableController;
        } else if (activeLecternPos != null) {
            controller = activeLecternController;
        } else {
            controller = SimulatedHelper.findBlockEntity(
                    minecraft.level, activeControllerSubLevelId, activeControllerPos, AnalogueContraptionControllerBlockEntity.class);
        }
        if (controller == null) {
            stopInteractMode(false);
            return false;
        }

        if (action != 0 && action != 1 && action != 2) {
            return false;
        }
        if (key < 32) {
            return true;
        }

        if (key == 256 && action == 1) {
            stopInteractMode(true);
            return true;
        }

        sendPhysicalInteraction(keyName(key), action != 0);

        if (gateUseInputUntilRelease && minecraft.options.keyUse.matches(key, scanCode)) {
            consumeMatchingKeyMapping(key, scanCode);
            if (action == 0) gateUseInputUntilRelease = false;
            return true;
        }

        Set<String> matchingChannels = new HashSet<>();
        collectMatchingChannels(controller, key, matchingChannels);
        if (matchingChannels.isEmpty()) {
            return false;
        }

        consumeMatchingKeyMapping(key, scanCode);

        if (action == 1) {
            for (String channelId : matchingChannels) {
                if (pressedChannelIds.add(channelId)) {
                    controller.applyClientKeyAnimation(channelId, true);
                    sendKeyPayload(channelId, true);
                }
            }
            return true;
        }

        if (action == 0) {
            for (String channelId : matchingChannels) {
                if (pressedChannelIds.remove(channelId)) {
                    controller.applyClientKeyAnimation(channelId, false);
                    sendKeyPayload(channelId, false);
                }
            }
            return true;
        }

        return true;
    }

    // Start the remote interact mode
    public static void startRemoteInteractMode(BlockPos pos, UUID subLevelId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || pos == null) return;
        AnalogueContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                minecraft.level, subLevelId, pos, AnalogueContraptionControllerBlockEntity.class);
        if (controller == null) {
            minecraft.player.displayClientMessage(Component.literal(
                    "The remote controller is no longer loaded"), true);
            return;
        }
        minecraft.setScreen(null);
        if (activeControllerPos != null && activeControllerPos.equals(pos)
                && java.util.Objects.equals(activeControllerSubLevelId, subLevelId)) return;
        startBlockInteractMode(controller, true);
    }

    // Handle the hardware controller input event
    public static void onHardwareControllerInput(Map<String, Double> values) {
        if (!acceptsHardwareControllerInput()) {
            lastHardwareControllerValues = Map.of();
            lastHardwareControllerSendTick = Long.MIN_VALUE;
            return;
        }
        AnalogueContraptionControllerBlockEntity controller = activePortable ? activePortableController
                : activeLecternPos != null ? activeLecternController
                : activeController != null ? activeController : openAdvMenuCtrl();
        if (controller == null) {
            return;
        }
        Map<String, Double> snapshot = values == null ? Map.of() : new LinkedHashMap<>(values);
        controller.applyHardwareControllerInput(snapshot);
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        boolean keepAlive = gameTime != Long.MIN_VALUE
                && (lastHardwareControllerSendTick == Long.MIN_VALUE
                || gameTime - lastHardwareControllerSendTick >= 20L);
        if (!hardwareValuesEqual(lastHardwareControllerValues, snapshot) || keepAlive) {
            sendHardwareCtrlPayload(snapshot);
            lastHardwareControllerValues = Map.copyOf(snapshot);
            lastHardwareControllerSendTick = gameTime;
        }
    }

    // Check if this accepts hardware controller input
    public static boolean acceptsHardwareControllerInput() {
        return activeControllerPos != null || activePortable || activeLecternPos != null
                || openAdvMenuCtrl() != null;
    }

    // Clear the open menu hardware controller input
    public static void clearOpenMenuHardwareControllerInput(AdvancedContraptionControllerMenu menu) {
        if (menu == null || activeControllerPos != null || activePortable || activeLecternPos != null) {
            return;
        }
        if (!lastHardwareControllerValues.isEmpty()) {
            PacketDistributor.sendToServer(HardwareControllerInputPayload.block(
                    menu.getContentPos(), menu.getContentSubLevelId(), Map.of()));
        }
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (controller != null) {
            controller.clearHardwareControllerInput();
        }
        lastHardwareControllerValues = Map.of();
        lastHardwareControllerSendTick = Long.MIN_VALUE;
    }

    // Handle the mouse movement
    public static boolean onMouseMove(double yaw, double pitch) {
        AdvancedContraptionControllerBlockEntity controller = activeAdvCtrl();
        if (controller == null) return false;
        Set<String> inputs = controller.getConfiguredMouseInputs();
        boolean captureX = inputs.contains("mouse_x");
        boolean captureY = inputs.contains("mouse_y");
        if (!captureX && !captureY) return false;

        if (captureX) {
            double val = Mth.clamp(controller.getMouseInputValue("mouse_x") + yaw / 110.0D, -1.0D, 1.0D);
            controller.applyClientMouseInput("mouse_x", val, Math.abs(val) > 0.0001D);
            if (Double.isNaN(lastSentMouseX) || Math.abs(val - lastSentMouseX) > 0.0001D) {
                sendMousePayload("mouse_x", val, Math.abs(val) > 0.0001D);
                lastSentMouseX = val;
            }
        }
        if (captureY) {
            double val = Mth.clamp(controller.getMouseInputValue("mouse_y") - pitch / 110.0D, -1.0D, 1.0D);
            controller.applyClientMouseInput("mouse_y", val, Math.abs(val) > 0.0001D);
            if (Double.isNaN(lastSentMouseY) || Math.abs(val - lastSentMouseY) > 0.0001D) {
                sendMousePayload("mouse_y", val, Math.abs(val) > 0.0001D);
                lastSentMouseY = val;
            }
        }
        return true;
    }

    // Handle the mouse button input
    public static boolean onMouseButton(int btn, int action) {
        AdvancedContraptionControllerBlockEntity controller = activeAdvCtrl();
        if (controller == null || (action != 0 && action != 1 && action != 2)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (gateUseInputUntilRelease && minecraft.options.keyUse.matchesMouse(btn)) {
            minecraft.options.keyUse.consumeClick();
            minecraft.options.keyUse.setDown(false);
            if (action == 0) gateUseInputUntilRelease = false;
            return true;
        }
        String input = switch (btn) {
            case 0 -> "left_click";
            case 1 -> "right_click";
            case 2 -> "middle_click";
            default -> "";
        };
        if (input.isBlank() || !controller.getConfiguredMouseInputs().contains(input)) return false;
        boolean active = action != 0;
        controller.applyClientMouseInput(input, active ? 1.0D : 0.0D, active);
        sendMousePayload(input, active ? 1.0D : 0.0D, active);
        return true;
    }

    // Handle the mouse wheel input
    public static boolean onMouseScroll(double horizontal, double vertical) {
        AdvancedContraptionControllerBlockEntity controller = activeAdvCtrl();
        if (controller == null) return false;
        double delta = vertical != 0.0D ? vertical : horizontal;
        if (delta == 0.0D) return true;
        String input = delta > 0.0D ? "scroll_up" : "scroll_down";
        if (!controller.getConfiguredMouseInputs().contains(input)) return false;
        double val = Mth.clamp(Math.abs(delta), 0.0D, 1.0D);
        controller.applyClientMouseInput(input, val, true);
        sendMousePayload(input, val, true);
        controller.applyClientMouseInput(input, 0.0D, false);
        return true;
    }

    // Stop the interact mode
    private static void stopInteractMode(boolean notifyPlayer) {
        AnalogueContraptionControllerBlockEntity hardwareController = activePortable ? activePortableController
                : activeLecternPos != null ? activeLecternController : activeController;
        if (!lastHardwareControllerValues.isEmpty()) {
            sendHardwareCtrlPayload(Map.of());
        }
        if (hardwareController != null) {
            hardwareController.clearHardwareControllerInput();
        }
        AdvancedContraptionControllerBlockEntity advancedController = activeAdvCtrl();
        if (advancedController != null) {
            for (String input : advancedController.getConfiguredMouseInputs()) {
                if (advancedController.isMouseInputActive(input)
                        || Math.abs(advancedController.getMouseInputValue(input)) > 0.0001D) {
                    sendMousePayload(input, 0.0D, false);
                }
            }
            advancedController.clearMouseInputs();
        }
        if (activeControllerPos != null) {
            for (String channelId : pressedChannelIds) {
                PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(activeControllerPos, activeControllerSubLevelId, channelId, false));
            }
            if (hardwareController instanceof AdvancedContraptionControllerBlockEntity) {
                PacketDistributor.sendToServer(new AdvancedControllerPhysicalInteractionPayload(
                        activeControllerPos, activeControllerSubLevelId, false,
                        activeControllerRemote, "use"));
            }
        } else if (activePortable) {
            for (String channelId : pressedChannelIds) {
                PacketDistributor.sendToServer(new PortableContraptionControllerKeyPayload(
                        activePortableHand, activePortableAdvanced, channelId, false));
            }
            PacketDistributor.sendToServer(new PortableContraptionControllerModePayload(
                    activePortableHand, activePortableAdvanced, false));
        } else if (activeLecternPos != null) {
            for (String channelId : pressedChannelIds) {
                PacketDistributor.sendToServer(new LecternPortableContraptionControllerKeyPayload(
                        activeLecternPos, activeLecternAdvanced, channelId, false));
            }
            PacketDistributor.sendToServer(new LecternPortableContraptionControllerModePayload(
                    activeLecternPos, activeLecternAdvanced, false));
        }
        pressedChannelIds.clear();
        activeControllerPos = null;
        activeControllerSubLevelId = null;
        activeControllerRemote = false;
        activePortable = false;
        activeLecternPos = null;
        activeController = null;
        activePortableController = null;
        activeLecternController = null;
        suppressedKeyCodes = Set.of();
        gateUseInputUntilRelease = false;
        resetMouseSendState();
        lastHardwareControllerValues = Map.of();
        lastHardwareControllerSendTick = Long.MIN_VALUE;

        LocalPlayer player = Minecraft.getInstance().player;
        if (notifyPlayer && player != null) {
            player.displayClientMessage(Component.literal("Controller interact mode disabled"), true);
        }
    }

    // Send the key payload
    private static void sendKeyPayload(String channelId, boolean pressed) {
        if (activePortable) {
            PacketDistributor.sendToServer(new PortableContraptionControllerKeyPayload(
                    activePortableHand, activePortableAdvanced, channelId, pressed));
            return;
        }
        if (activeLecternPos != null) {
            PacketDistributor.sendToServer(new LecternPortableContraptionControllerKeyPayload(
                    activeLecternPos, activeLecternAdvanced, channelId, pressed));
            return;
        }
        PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(
                activeControllerPos, activeControllerSubLevelId, channelId, pressed));
    }

    // Send the physical interaction
    private static void sendPhysicalInteraction(String keyPressed, boolean active) {
        if (activeControllerPos == null || !(activeController instanceof AdvancedContraptionControllerBlockEntity)) {
            return;
        }
        PacketDistributor.sendToServer(new AdvancedControllerPhysicalInteractionPayload(
                activeControllerPos, activeControllerSubLevelId, active,
                activeControllerRemote, keyPressed));
    }

    // Handle key name
    private static String keyName(int key) {
        String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
        return name == null || name.isBlank() ? "key_" + key : name;
    }

    // Send the mouse payload
    private static void sendMousePayload(String input, double val, boolean active) {
        if (activePortable) {
            PacketDistributor.sendToServer(AdvancedControllerMouseInputPayload.portable(
                    activePortableHand, input, val, active));
            return;
        }
        if (activeLecternPos != null) {
            PacketDistributor.sendToServer(AdvancedControllerMouseInputPayload.lectern(
                    activeLecternPos, input, val, active));
            return;
        }
        if (activeControllerPos != null) {
            PacketDistributor.sendToServer(AdvancedControllerMouseInputPayload.block(
                    activeControllerPos, activeControllerSubLevelId, input, val, active));
        }
    }

    // Send the hardware ctrl payload
    private static void sendHardwareCtrlPayload(Map<String, Double> values) {
        if (activePortable) {
            PacketDistributor.sendToServer(HardwareControllerInputPayload.portable(
                    activePortableHand, activePortableAdvanced, values));
        } else if (activeLecternPos != null) {
            PacketDistributor.sendToServer(HardwareControllerInputPayload.lectern(
                    activeLecternPos, activeLecternAdvanced, values));
        } else if (activeControllerPos != null) {
            PacketDistributor.sendToServer(HardwareControllerInputPayload.block(
                    activeControllerPos, activeControllerSubLevelId, values));
        } else {
            AdvancedContraptionControllerMenu menu = openAdvancedMenu();
            if (menu != null) {
                PacketDistributor.sendToServer(HardwareControllerInputPayload.block(
                        menu.getContentPos(), menu.getContentSubLevelId(), values));
            }
        }
    }

    // Open the adv menu ctrl
    private static AdvancedContraptionControllerBlockEntity openAdvMenuCtrl() {
        AdvancedContraptionControllerMenu menu = openAdvancedMenu();
        return menu == null ? null : menu.getMenuConfigTargetBlockEntity();
    }

    // Open the advanced menu
    private static AdvancedContraptionControllerMenu openAdvancedMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.player.containerMenu instanceof AdvancedContraptionControllerMenu menu
                ? menu : null;
    }

    // Check if the hardware values match
    private static boolean hardwareValuesEqual(Map<String, Double> first, Map<String, Double> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (Map.Entry<String, Double> entry : first.entrySet()) {
            if (Math.abs(entry.getValue() - second.getOrDefault(entry.getKey(), 0.0D)) > 0.002D) {
                return false;
            }
        }
        return true;
    }

    // Get the active adv ctrl
    private static AdvancedContraptionControllerBlockEntity activeAdvCtrl() {
        AnalogueContraptionControllerBlockEntity controller = activePortable ? activePortableController
                : activeLecternPos != null ? activeLecternController : activeController;
        return controller instanceof AdvancedContraptionControllerBlockEntity advanced ? advanced : null;
    }

    // Reset the mouse send state
    private static void resetMouseSendState() {
        lastSentMouseX = Double.NaN;
        lastSentMouseY = Double.NaN;
    }

    // Arm the use input gate
    private static void armUseInputGate() {
        Minecraft minecraft = Minecraft.getInstance();
        gateUseInputUntilRelease = minecraft.options.keyUse.isDown();
        if (gateUseInputUntilRelease) {
            minecraft.options.keyUse.consumeClick();
            minecraft.options.keyUse.setDown(false);
        }
    }

    // Get the portable controller
    private static AnalogueContraptionControllerBlockEntity portableController(Minecraft minecraft,
                                                                              InteractionHand hand,
                                                                              boolean advanced) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)
                || portable.isAdvanced() != advanced) {
            return null;
        }
        return PortableContraptionControllerItem.createControllerFromStack(stack, minecraft.level, advanced);
    }

    // Get the lectern controller
    private static AnalogueContraptionControllerBlockEntity lecternController(Minecraft minecraft, BlockPos pos,
                                                                              boolean advanced) {
        if (minecraft.level == null || minecraft.player == null || pos == null) {
            return null;
        }
        return LecternPortableContraptionController.createControllerFromLectern(minecraft.level, pos, advanced);
    }

    // Get the active lectern controller
    public static AnalogueContraptionControllerBlockEntity activeLecternController(BlockPos pos, boolean advanced) {
        if (pos == null || activeLecternPos == null || !activeLecternPos.equals(pos)
                || activeLecternAdvanced != advanced) {
            return null;
        }
        return activeLecternController;
    }

    // Check if the portable interact mode is active
    static boolean isPortableInteractModeActive() {
        return activePortable;
    }

    // Get the portable interaction hand
    static InteractionHand portableInteractionHand() {
        return activePortableHand;
    }

    // Check if the request portable menu is open
    public static boolean requestPortableMenuOpen(InteractionHand hand, boolean advanced) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.screen != null) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)
                || portable.isAdvanced() != advanced) {
            return false;
        }

        if (activeControllerPos != null || activePortable || activeLecternPos != null) {
            stopInteractMode(false);
        }
        PacketDistributor.sendToServer(new PortableContraptionControllerOpenPayload(hand, portable.isAdvanced()));
        return true;
    }

    // Collect the matching channels
    private static void collectMatchingChannels(AnalogueContraptionControllerBlockEntity controller, int key,
                                                Set<String> matchingChannels) {
        if (key < 0) {
            return;
        }
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            if (controller.getKeyBinding(channel.id()) == key) {
                matchingChannels.add(channel.id());
            }
        }

        for (com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry entry : controller.getCustomKeyEntries()) {
            if (entry.keyCode == key) {
                matchingChannels.add(entry.id());
            }

            if (entry.stepDownKeyCode == key) {
                matchingChannels.add(entry.id() + STEP_DOWN_KEY_SUFFIX);
            }
        }
        if (controller instanceof AdvancedContraptionControllerBlockEntity advancedController) {
            advancedController.collectGraphOwnedKeyBindings(key, matchingChannels);
        }
    }

    // Consume the matching key mapping
    private static void consumeMatchingKeyMapping(int key, int scanCode) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (!mapping.matches(key, scanCode)) {
                continue;
            }
            mapping.consumeClick();
            mapping.setDown(false);
        }
    }

    // Refresh the suppressed key codes
    private static void refreshSuppressedKeyCodes(AnalogueContraptionControllerBlockEntity controller) {
        Set<Integer> boundKeys = new HashSet<>();
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            int keyCode = controller.getKeyBinding(channel.id());
            if (keyCode >= 0) {
                boundKeys.add(keyCode);
            }
        }

        for (com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry entry : controller.getCustomKeyEntries()) {
            if (entry.keyCode >= 0) {
                boundKeys.add(entry.keyCode);
            }
            if (entry.stepDownKeyCode >= 0) {
                boundKeys.add(entry.stepDownKeyCode);
            }
        }
        if (controller instanceof AdvancedContraptionControllerBlockEntity advancedController) {
            advancedController.collectGraphOwnedKeyCodes(boundKeys);
        }

        suppressedKeyCodes = Set.copyOf(boundKeys);
    }

    // Release the suppressed mappings
    private static void releaseSuppressedMappings() {
        if (suppressedKeyCodes.isEmpty()) {
            return;
        }
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (!suppressedKeyCodes.contains(mapping.getKey().getValue())) {
                continue;
            }
            mapping.consumeClick();
            mapping.setDown(false);
        }
    }
}
