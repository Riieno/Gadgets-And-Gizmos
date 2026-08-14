package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlock;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickOrientation;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueJoystickDragPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.Map;

// Run local joystick dragging and only send movement when its value or keepalive changes
public final class AnalogueJoystickClientHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int RELEASE_ACTION = 0;
    private static final float DRAG_SENSITIVITY = 110.0f;
    private static final float DRAG_DEADZONE = 0.0008f;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active pos
    private static BlockPos activePos;
    // Active sub-level id
    private static UUID activeSubLevelId;
    // Local x
    private static float localX;
    // Local z
    private static float localZ;
    // Last sent x
    private static float lastSentX = Float.NaN;
    // Last sent z
    private static float lastSentZ = Float.NaN;
    // Last sent tick
    private static long lastSentTick = Long.MIN_VALUE;
    // Tracks whether hardware input is active
    private static boolean hardwareInputActive;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick client handler
    private AnalogueJoystickClientHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Start the dragging
    public static void startDragging(AnalogueJoystickBlockEntity blockEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        activePos = blockEntity.getBlockPos();
        activeSubLevelId = SimulatedHelper.getContainingSubLevelId(blockEntity);
        localX = (float) blockEntity.getVisualLocalX(1.0f);
        localZ = (float) blockEntity.getVisualLocalZ(1.0f);
        lastSentX = Float.NaN;
        lastSentZ = Float.NaN;
        lastSentTick = Long.MIN_VALUE;
        blockEntity.applyClientPreview(localX, localZ, true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the mouse movement
    public static boolean onMouseMove(double yaw, double pitch) {
        if (activePos == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            stopDragging(false);
            return false;
        }
        if (minecraft.screen != null || !(findActiveJoystick(minecraft) instanceof AnalogueJoystickBlockEntity joystick)) {
            stopDragging(false);
            return false;
        }
        if (isOutOfRange(minecraft, player)) {
            stopDragging(true);
            return false;
        }

        if (yaw != 0.0D || pitch != 0.0D) {
            float sensitivity = joystick.getDragSensitivity();
            float dragScale = sensitivity / DRAG_SENSITIVITY;

            float[] localDelta = rotatePlayerDragToLocal(
                (float) yaw * dragScale,
                -(float) pitch * dragScale,
                player,
                    AnalogueJoystickBlock.getLogicalFacing(joystick.getBlockState()),
                    joystick);

            if (Math.abs(localDelta[0]) < DRAG_DEADZONE) {
                localDelta[0] = 0.0f;
            }
            if (Math.abs(localDelta[1]) < DRAG_DEADZONE) {
                localDelta[1] = 0.0f;
            }
            localX = Mth.clamp(localX + localDelta[0], -1.0f, 1.0f);
            localZ = Mth.clamp(localZ + localDelta[1], -1.0f, 1.0f);
        }

        joystick.applyClientPreview(localX, localZ, true);
        sendUpdateIfNeeded();
        return true;
    }

    // Update the analogue joystick client handler
    public static void tick() {
        if (activePos == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            stopDragging(false);
            return;
        }
        if (minecraft.screen != null || !minecraft.options.keyUse.isDown()) {
            stopDragging(true);
            return;
        }
        if (!(findActiveJoystick(minecraft) instanceof AnalogueJoystickBlockEntity joystick)) {
            stopDragging(false);
            return;
        }
        if (isOutOfRange(minecraft, player)) {
            stopDragging(true);
            return;
        }
        joystick.applyClientPreview(localX, localZ, true);
        sendUpdateIfNeeded();
    }

    // Check if this is dragging
    public static boolean isDragging(BlockPos pos) {
        return activePos != null && activePos.equals(pos);
    }

    // Get the drag HUD state
    public static DragHudState getDragHudState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (activePos == null || minecraft.level == null) {
            return null;
        }
        AnalogueJoystickBlockEntity joystick = findActiveJoystick(minecraft);
        if (joystick == null) {
            return null;
        }
        DirectionalAnalogSnapshot snapshot = joystick.getDirectionalAnalogSnapshot();
        return new DragHudState(
                (float) snapshot.localX(),
                (float) snapshot.localZ(),
                snapshot.forwardRedstone(),
                snapshot.backwardRedstone(),
                snapshot.leftRedstone(),
                snapshot.rightRedstone());
    }

    // Handle the mouse button input
    public static boolean onMouseButton(int btn, int action) {
        if (activePos == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean useButton = minecraft.options.keyUse.matchesMouse(btn);
        if (useButton && action == RELEASE_ACTION) {
            stopDragging(true);

            return false;
        }
        return useButton || minecraft.options.keyAttack.matchesMouse(btn);
    }

    // Handle the hardware controller input event
    public static void onHardwareControllerInput(Map<String, Double> values) {
        if (activePos == null) {
            hardwareInputActive = false;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(findActiveJoystick(minecraft) instanceof AnalogueJoystickBlockEntity joystick)) {
            return;
        }
        if (values == null || values.isEmpty()) {
            if (!hardwareInputActive) {
                return;
            }
            hardwareInputActive = false;
            localX = 0.0F;
            localZ = 0.0F;
            joystick.applyClientPreview(localX, localZ, true);
            sendUpdateIfNeeded();
            return;
        }
        hardwareInputActive = true;
        double hardwareX = values.getOrDefault("hardware:left_x",
                values.getOrDefault("hardware:axis_0", 0.0D));
        double hardwareY = values.getOrDefault("hardware:left_y",
                values.getOrDefault("hardware:axis_1", 0.0D));
        localX = Mth.clamp((float) hardwareX, -1.0F, 1.0F);
        localZ = Mth.clamp((float) -hardwareY, -1.0F, 1.0F);
        joystick.applyClientPreview(localX, localZ, true);
        sendUpdateIfNeeded();
    }

    // Check if this accepts hardware controller input
    public static boolean acceptsHardwareControllerInput() {
        return activePos != null;
    }

    // Stop the dragging
    private static void stopDragging(boolean notifyServer) {
        Minecraft minecraft = Minecraft.getInstance();
        float releaseX = localX;
        float releaseZ = localZ;
        if (activePos != null && minecraft.level != null
                && findActiveJoystick(minecraft) instanceof AnalogueJoystickBlockEntity joystick) {

            if (joystick.isMomentaryMode()) {
                releaseX = 0.0f;
                releaseZ = 0.0f;
            }
            joystick.applyClientPreview(releaseX, releaseZ, false);
        }
        if (notifyServer && activePos != null) {
            PacketDistributor.sendToServer(new AnalogueJoystickDragPayload(activePos, activeSubLevelId, true, releaseX, releaseZ));
        }
        activePos = null;
        activeSubLevelId = null;
        lastSentX = Float.NaN;
        lastSentZ = Float.NaN;
        lastSentTick = Long.MIN_VALUE;
        hardwareInputActive = false;
        localX = 0.0f;
        localZ = 0.0f;
    }

    // Find the active joystick
    private static AnalogueJoystickBlockEntity findActiveJoystick(Minecraft minecraft) {
        if (activePos == null || minecraft.level == null) {
            return null;
        }
        return SimulatedHelper.findBlockEntity(minecraft.level, activeSubLevelId, activePos, AnalogueJoystickBlockEntity.class);
    }

    // Send the update if needed
    private static void sendUpdateIfNeeded() {
        if (activePos == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        if (Float.isNaN(lastSentX) || Math.abs(lastSentX - localX) > 0.01f || Math.abs(lastSentZ - localZ) > 0.01f
                || (now != Long.MIN_VALUE && now - lastSentTick >= 10L)) {
            PacketDistributor.sendToServer(new AnalogueJoystickDragPayload(activePos, activeSubLevelId, false, localX, localZ));
            lastSentX = localX;
            lastSentZ = localZ;
            lastSentTick = now;
        }
    }

    // Check if this is out of range
    private static boolean isOutOfRange(Minecraft minecraft, LocalPlayer player) {
        double range = player.blockInteractionRange();
        return SimulatedHelper.distanceSquaredWithSubLevels(minecraft.level, player.getEyePosition(), activePos.getCenter()) > range * range;
    }

    // Store drag HUD state
    public record DragHudState(float localX, float localZ, int forwardRedstone, int backwardRedstone,
                               int leftRedstone, int rightRedstone) {
    }

    // Rotate the player drag to local
    private static float[] rotatePlayerDragToLocal(float playerRight, float playerForward, LocalPlayer player,
                                                   Direction blockFacing, AnalogueJoystickBlockEntity joystick) {
        Direction resolvedBlockFacing = blockFacing != null && blockFacing.getAxis().isHorizontal()
                ? blockFacing
                : Direction.NORTH;

        // Sable keeps the player's view in the root level even while they stand on a ship
        // Applying the ship pose here would rotate that view twice before the joystick uses it
        Vec3 view = player != null ? player.getViewVector(1.0f) : null;
        Vec3 localView = view == null ? null : SimulatedHelper.toContainingLocalDirection(joystick, view);
        float forwardX = localView != null ? (float) localView.x : 0.0f;
        float forwardZ = localView != null ? (float) localView.z : 0.0f;
        float forwardLength = Mth.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (forwardLength < 1.0E-6f) {
            Direction fallbackFacing = player != null && player.getDirection().getAxis().isHorizontal()
                    ? player.getDirection()
                    : Direction.NORTH;
            forwardX = fallbackFacing.getStepX();
            forwardZ = fallbackFacing.getStepZ();
            forwardLength = 1.0f;
        }
        float normalizedForwardX = forwardX / forwardLength;
        float normalizedForwardZ = forwardZ / forwardLength;
        float normalizedRightX = -normalizedForwardZ;
        float normalizedRightZ = normalizedForwardX;

        float localX = playerRight * normalizedRightX + playerForward * normalizedForwardX;
        float localZ = playerRight * normalizedRightZ + playerForward * normalizedForwardZ;
        float localMagnitude = Mth.sqrt(localX * localX + localZ * localZ);
        if (localMagnitude < 1.0E-6f) {
            return new float[]{0.0f, 0.0f};
        }

        Direction blockRightDirection = resolvedBlockFacing.getClockWise();
        float blockRight = localX * blockRightDirection.getStepX() + localZ * blockRightDirection.getStepZ();
        float blockForward = localX * resolvedBlockFacing.getStepX() + localZ * resolvedBlockFacing.getStepZ();

        return AnalogueJoystickOrientation.localDrag(blockRight, blockForward);
    }
}
