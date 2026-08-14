package com.rieno.gadgetsandgizmos.content.pose;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Store one named mannequin pose with every body rotation
public final class ArmorStandPosePreset {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final List<ArmorStandPosePreset> DEFAULTS = List.of(
            preset("attention", "Attention",
                    v(0, 0, 0), v(0, 0, 0), v(0, 0, 0), v(0, 0, 0), v(0, 0, 0), v(0, 0, 0)),
            preset("walking", "Walking",
                    v(0, 0, 0), v(0, 0, 0), v(-20, 0, -10), v(20, 0, 10), v(20, 0, 0), v(-20, 0, 0)),
            preset("running", "Running",
                    v(0, 0, 0), v(0, 0, 0), v(40, 0, -10), v(-40, 0, 10), v(-40, 0, 0), v(40, 0, 0)),
            preset("pointing", "Pointing",
                    v(0, 20, 0), v(0, 0, 0), v(0, 0, -10), v(-90, 18, 0), v(0, 0, 0), v(0, 0, 0)),
            preset("blocking", "Blocking",
                    v(0, 0, 0), v(0, 0, 0), v(-50, 50, 0), v(-20, -20, 0), v(20, 0, 0), v(-20, 0, 0)),
            preset("lunging", "Lunging",
                    v(0, 0, 0), v(15, 0, 0), v(10, 0, -10), v(-60, -10, 0), v(30, 0, 0), v(-15, 0, 0)),
            preset("winning", "Winning",
                    v(-15, 0, 0), v(0, 0, 0), v(10, 0, -10), v(-120, -10, 0), v(15, 0, 0), v(0, 0, 0)),
            preset("sitting", "Sitting",
                    v(0, 0, 0), v(0, 0, 0), v(-80, -20, 0), v(-80, 20, 0), v(-90, -10, 0), v(-90, 10, 0)),
            preset("arabesque", "Arabesque",
                    v(-15, 0, 0), v(10, 0, 0), v(70, 0, -10), v(-140, -10, 0), v(75, 0, 0), v(0, 0, 0)),
            preset("cupid", "Cupid",
                    v(0, 0, 0), v(10, 0, 0), v(-75, 0, 10), v(-90, -10, 0), v(75, 0, 0), v(0, 0, 0)),
            preset("point_and_laugh", "Point and Laugh",
                    v(25, 17, -8), v(10, 7, 8), v(-90, 0, 20), v(-8, 0, -77), v(20, 30, -10), v(20, -10, 20)),
            preset("confident", "Confident",
                    v(-10, 20, 0), v(-2, 0, 0), v(5, 0, 0), v(5, 0, 0), v(0, -10, -4), v(16, 2, 10)),
            preset("salute", "Salute",
                    v(0, 0, 0), v(5, 0, 0), v(29, 0, 25), v(-124, -51, -35), v(0, 4, 2), v(0, -4, -2)),
            preset("death", "Death",
                    v(-85, 0, 0), v(-90, 0, 0), v(-90, -10, 0), v(-90, 10, 0), v(-90, 0, 0), v(-90, 0, 0)),
            preset("facepalm", "Facepalm",
                    v(45, -4, 1), v(10, 0, 0), v(-72, 24, 47), v(18, -14, 0), v(-4, -6, -2), v(25, -2, 0)),
            preset("lazing", "Lazing",
                    v(14, -12, 6), v(5, 0, 0), v(-4, -20, -10), v(-40, 20, 0), v(-88, 46, 0), v(-88, 71, 0)),
            preset("confused", "Confused",
                    v(0, 30, 0), v(0, 13, 0), v(145, 22, -49), v(-22, 31, 10), v(-6, 0, 0), v(6, -20, 0)),
            preset("formal", "Formal",
                    v(4, 0, 0), v(4, 0, 0), v(30, -20, 21), v(30, 22, -20), v(0, 0, -5), v(0, 0, 5)),
            preset("sad", "Sad",
                    v(63, 0, 0), v(10, 0, 0), v(-5, 0, -5), v(-5, 0, 5), v(-5, 16, -5), v(-5, -10, 5)),
            preset("joyous", "Joyous",
                    v(-11, 0, 0), v(-4, 0, 0), v(0, 0, -100), v(0, 0, 100), v(-8, 0, -60), v(-8, 0, 60)),
            preset("stargazing", "Stargazing",
                    v(-22, 25, 0), v(-4, 10, 0), v(4, 18, 0), v(-153, 34, -3), v(6, 24, 0), v(-4, 17, 2)),
            preset("block", "Block",
                    v(0, 0, 0), v(0, 0, 0), v(0, 0, 0), v(-15, -45, 0), v(0, 0, 0), v(0, 0, 0)),
            preset("item", "Item",
                    v(0, 0, 0), v(0, 0, 0), v(0, 0, 0), v(-90, 0, 0), v(0, 0, 0), v(0, 0, 0)),
            preset("random", "Random",
                    v(25, 0, 0), v(0, 90, 0), v(0, 0, -50), v(0, 0, 50), v(0, 0, -50), v(0, 0, 50))
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Armor stand pose preset id
    private final String id;
    // Display name
    private final Component displayName;
    // Tracked poses
    private final EnumMap<ArmorStandPosePart, float[]> poses;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the armor stand pose preset
    private ArmorStandPosePreset(String id, String displayName, EnumMap<ArmorStandPosePart, float[]> poses) {
        this.id = id;
        this.displayName = Component.literal(displayName);
        this.poses = copyPoses(poses);
    }

    // Initialize the armor stand pose preset
    private ArmorStandPosePreset(String id, Component displayName, EnumMap<ArmorStandPosePart, float[]> poses) {
        this.id = id;
        this.displayName = displayName;
        this.poses = copyPoses(poses);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the armor stand pose preset
    public static ArmorStandPosePreset of(String id, Component displayName,
                                          EnumMap<ArmorStandPosePart, float[]> poses) {
        return new ArmorStandPosePreset(id, displayName, poses);
    }

    // Create the armor stand pose preset from data
    public static ArmorStandPosePreset fromData(String id, Component displayName, ArmorStandPoseData data) {
        EnumMap<ArmorStandPosePart, float[]> poses = new EnumMap<>(ArmorStandPosePart.class);
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            poses.put(part, data.pose(part));
        }
        return of(id, displayName, poses);
    }

    // Get the id
    public String id() {
        return id;
    }

    // Get the armor stand pose preset display name
    public Component displayName() {
        return displayName;
    }

    // Check if the pose preset randomizes
    public boolean randomizes() {
        return "random".equals(id);
    }

    // Get the pose
    public float[] pose(ArmorStandPosePart part) {
        float[] val = poses.get(part);
        return val == null ? v(0, 0, 0) : val;
    }

    // Get the preset
    private static ArmorStandPosePreset preset(String id, String displayName, float[] head, float[] body,
                                               float[] leftArm, float[] rightArm, float[] leftLeg, float[] rightLeg) {
        EnumMap<ArmorStandPosePart, float[]> poses = new EnumMap<>(ArmorStandPosePart.class);
        poses.put(ArmorStandPosePart.HEAD, head);
        poses.put(ArmorStandPosePart.BODY, body);
        poses.put(ArmorStandPosePart.LEFT_ARM, leftArm);
        poses.put(ArmorStandPosePart.RIGHT_ARM, rightArm);
        poses.put(ArmorStandPosePart.LEFT_LEG, leftLeg);
        poses.put(ArmorStandPosePart.RIGHT_LEG, rightLeg);
        return new ArmorStandPosePreset(id, displayName, poses);
    }

    // Get the v
    private static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    // Copy the poses
    private static EnumMap<ArmorStandPosePart, float[]> copyPoses(Map<ArmorStandPosePart, float[]> src) {
        EnumMap<ArmorStandPosePart, float[]> copy = new EnumMap<>(ArmorStandPosePart.class);
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            float[] val = src.get(part);
            copy.put(part, val == null ? v(0, 0, 0) : val.clone());
        }
        return copy;
    }
}
