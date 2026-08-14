package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.neoforge.network.ArmorStandPosePreferencePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// Keep the current Armor Stand Pose Client state in one place
public final class ArmorStandPoseClientState {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String STRAW_STATUES_MOD_ID = "strawstatues";
    private static final int RESTORE_BUTTON_WIDTH = 96;
    private static final int RESTORE_BUTTON_HEIGHT = 18;
    private static final int RESTORE_BUTTON_MARGIN = 8;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared hidden entity id
    private static int hiddenEntityId = -1;
    // Tracks whether hidden for other GUI is set
    private static boolean hiddenForOtherGui;
    // Tracks whether saw other screen is set
    private static boolean sawOtherScreen;
    // Shared hidden player x
    private static double hiddenPlayerX = Double.NaN;
    // Shared hidden player y
    private static double hiddenPlayerY = Double.NaN;
    // Shared hidden player z
    private static double hiddenPlayerZ = Double.NaN;
    // Shared hidden player y rot
    private static float hiddenPlayerYRot;
    // Shared hidden player x rot
    private static float hiddenPlayerXRot;
    // Last sent enabled state
    private static Boolean lastSentEnabled;
    // Last sent prefer straw statues state
    private static Boolean lastSentPreferStrawStatues;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the armor stand pose client state
    private ArmorStandPoseClientState() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the locally is enabled
    public static boolean isLocallyEnabled() {
        return CTConfigs.CLIENT.enableMannequinPoserGui.get() && !hiddenForOtherGui;
    }

    // Hide the other gui
    public static void hideForOtherGui(int entityId) {
        hiddenEntityId = entityId;
        hiddenForOtherGui = true;
        sawOtherScreen = false;
        rememberPlayerState();
        lastSentEnabled = null;
        lastSentPreferStrawStatues = null;
        syncPreference();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the armor stand pose client state
    public static void tick() {
        if (!CTConfigs.CLIENT.enableMannequinPoserGui.get()) {
            closeHiddenMode();
            syncPreference();
            return;
        }

        if (hiddenForOtherGui) {
            Minecraft minecraft = Minecraft.getInstance();
            Screen screen = minecraft.screen;
            if (screen != null && !(screen instanceof ArmorStandPoseScreen)) {
                sawOtherScreen = true;
            } else if (screen == null && (sawOtherScreen || hasPlayerMoved(minecraft))) {
                closeHiddenMode();
            }
        }
        syncPreference();
    }

    // Reset the armor stand pose client state
    public static void reset() {
        hiddenEntityId = -1;
        hiddenForOtherGui = false;
        sawOtherScreen = false;
        clearPlayerState();
        lastSentEnabled = null;
        lastSentPreferStrawStatues = null;
    }

    // Draw the overlay button
    public static void renderOverlayButton(ScreenEvent.Render.Post evt) {
        if (!hiddenForOtherGui || evt.getScreen() instanceof ArmorStandPoseScreen) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int x = minecraft.getWindow().getGuiScaledWidth() - RESTORE_BUTTON_WIDTH - RESTORE_BUTTON_MARGIN;
        int y = RESTORE_BUTTON_MARGIN;
        boolean hovered = inside(evt.getMouseX(), evt.getMouseY(), x, y,
                RESTORE_BUTTON_WIDTH, RESTORE_BUTTON_HEIGHT);
        CTCreateScreenHelper.renderTextButton(evt.getGuiGraphics(), minecraft.font, x, y,
                RESTORE_BUTTON_WIDTH, RESTORE_BUTTON_HEIGHT,
                Component.translatable("createthrusters.pose_screen.show_poser"), hovered, true, true);
    }

    // Handle the overlay click
    public static void handleOverlayClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!hiddenForOtherGui || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || evt.getScreen() instanceof ArmorStandPoseScreen) {
            return;
        }

        int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() - RESTORE_BUTTON_WIDTH - RESTORE_BUTTON_MARGIN;
        int y = RESTORE_BUTTON_MARGIN;
        if (!inside(evt.getMouseX(), evt.getMouseY(), x, y, RESTORE_BUTTON_WIDTH, RESTORE_BUTTON_HEIGHT)) {
            return;
        }

        restorePoseScreen(evt.getScreen());
        evt.setCanceled(true);
    }

    // Sync the preference
    public static void syncPreference() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            lastSentEnabled = null;
            lastSentPreferStrawStatues = null;
            return;
        }

        boolean enabled = isLocallyEnabled();
        boolean preferStrawStatues = CTConfigs.CLIENT.preferStrawStatuesPoserGui.get()
                && ModList.get().isLoaded(STRAW_STATUES_MOD_ID);
        if (lastSentEnabled != null && lastSentEnabled == enabled
                && lastSentPreferStrawStatues != null && lastSentPreferStrawStatues == preferStrawStatues) {
            return;
        }
        PacketDistributor.sendToServer(new ArmorStandPosePreferencePayload(enabled, preferStrawStatues));
        lastSentEnabled = enabled;
        lastSentPreferStrawStatues = preferStrawStatues;
    }

    // Restore the pose screen
    private static void restorePoseScreen(Screen currentScreen) {
        int entityId = hiddenEntityId;
        closeHiddenMode();
        syncPreference();
        if (currentScreen != null) {
            currentScreen.onClose();
        }
        if (entityId >= 0) {
            ArmorStandPoseScreen.open(entityId);
        }
    }

    // Close the hidden mode
    private static void closeHiddenMode() {
        if (!hiddenForOtherGui && hiddenEntityId < 0 && !sawOtherScreen) {
            return;
        }
        hiddenEntityId = -1;
        hiddenForOtherGui = false;
        sawOtherScreen = false;
        clearPlayerState();
        lastSentEnabled = null;
        lastSentPreferStrawStatues = null;
    }

    // Remember the player state
    private static void rememberPlayerState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            clearPlayerState();
            return;
        }
        hiddenPlayerX = minecraft.player.getX();
        hiddenPlayerY = minecraft.player.getY();
        hiddenPlayerZ = minecraft.player.getZ();
        hiddenPlayerYRot = minecraft.player.getYRot();
        hiddenPlayerXRot = minecraft.player.getXRot();
    }

    // Check if this has player moved
    private static boolean hasPlayerMoved(Minecraft minecraft) {
        if (minecraft.player == null) {
            return true;
        }
        if (Double.isNaN(hiddenPlayerX)) {
            rememberPlayerState();
            return false;
        }

        double dx = minecraft.player.getX() - hiddenPlayerX;
        double dy = minecraft.player.getY() - hiddenPlayerY;
        double dz = minecraft.player.getZ() - hiddenPlayerZ;
        if (dx * dx + dy * dy + dz * dz > 1.0E-4D) {
            return true;
        }

        return Math.abs(Mth.wrapDegrees(minecraft.player.getYRot() - hiddenPlayerYRot)) > 1.0F
                || Math.abs(minecraft.player.getXRot() - hiddenPlayerXRot) > 1.0F;
    }

    // Clear the player state
    private static void clearPlayerState() {
        hiddenPlayerX = Double.NaN;
        hiddenPlayerY = Double.NaN;
        hiddenPlayerZ = Double.NaN;
        hiddenPlayerYRot = 0.0F;
        hiddenPlayerXRot = 0.0F;
    }

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
