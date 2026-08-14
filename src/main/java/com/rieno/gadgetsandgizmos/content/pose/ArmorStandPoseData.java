package com.rieno.gadgetsandgizmos.content.pose;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;

// Store and serialize Armor Stand Pose data
public final class ArmorStandPoseData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int LOCKED_DISABLED_SLOTS = 0x3F3F3F;
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "Invisible",
            "NoBasePlate",
            "NoGravity",
            "ShowArms",
            "Small",
            "CustomNameVisible",
            "Invulnerable",
            "DisabledSlots",
            "Rotation",
            "Pose");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether invisible is set
    public boolean invisible;
    // Tracks whether no base plate is set
    public boolean noBasePlate;
    // Tracks whether no gravity is set
    public boolean noGravity;
    // Controls whether to show arms
    public boolean showArms;
    // Tracks whether small is set
    public boolean small;
    // Tracks whether name is visible
    public boolean nameVisible;
    // Tracks whether armor stand pose is locked
    public boolean locked;
    // Current rotation
    public float rotation;

    // Tracked poses
    private final EnumMap<ArmorStandPosePart, float[]> poses = new EnumMap<>(ArmorStandPosePart.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the armor stand pose data
    public ArmorStandPoseData() {
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            poses.put(part, new float[]{0.0F, 0.0F, 0.0F});
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the armor stand pose data from entity
    public static ArmorStandPoseData fromEntity(ArmorStand armorStand) {
        ArmorStandPoseData data = new ArmorStandPoseData();
        data.invisible = armorStand.isInvisible();
        data.noBasePlate = armorStand.isNoBasePlate();
        data.noGravity = armorStand.isNoGravity();
        data.showArms = armorStand.isShowArms();
        data.small = armorStand.isSmall();
        data.nameVisible = armorStand.isCustomNameVisible();
        data.locked = armorStand.isInvulnerable();
        data.rotation = armorStand.getYRot();
        data.setPose(ArmorStandPosePart.HEAD, armorStand.getHeadPose());
        data.setPose(ArmorStandPosePart.BODY, armorStand.getBodyPose());
        data.setPose(ArmorStandPosePart.LEFT_ARM, armorStand.getLeftArmPose());
        data.setPose(ArmorStandPosePart.RIGHT_ARM, armorStand.getRightArmPose());
        data.setPose(ArmorStandPosePart.LEFT_LEG, armorStand.getLeftLegPose());
        data.setPose(ArmorStandPosePart.RIGHT_LEG, armorStand.getRightLegPose());
        return data;
    }

    // Set the pose
    public void setPose(ArmorStandPosePart part, Rotations rotations) {
        setPose(part, rotations.getX(), rotations.getY(), rotations.getZ());
    }

    // Set the pose
    public void setPose(ArmorStandPosePart part, float x, float y, float z) {
        poses.put(part, new float[]{x, y, z});
    }

    // Get the pose
    public float[] pose(ArmorStandPosePart part) {
        float[] val = poses.get(part);
        return val == null ? new float[]{0.0F, 0.0F, 0.0F} : val;
    }

    // Apply the preset
    public void applyPreset(ArmorStandPosePreset preset) {
        if (preset == null) {
            return;
        }
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            float[] pose = preset.pose(part);
            setPose(part, pose[0], pose[1], pose[2]);
        }
        showArms = true;
    }

    // Write the armor stand pose data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Invisible", invisible);
        tag.putBoolean("NoBasePlate", noBasePlate);
        tag.putBoolean("NoGravity", noGravity);
        tag.putBoolean("ShowArms", showArms);
        tag.putBoolean("Small", small);
        tag.putBoolean("CustomNameVisible", nameVisible);
        tag.putBoolean("Invulnerable", locked);
        tag.putInt("DisabledSlots", locked ? LOCKED_DISABLED_SLOTS : 0);

        ListTag rotationTag = new ListTag();
        rotationTag.add(FloatTag.valueOf(rotation));
        rotationTag.add(FloatTag.valueOf(0.0F));
        tag.put("Rotation", rotationTag);

        CompoundTag poseTag = new CompoundTag();
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            poseTag.put(part.tagKey(), rotationList(pose(part)));
        }
        tag.put("Pose", poseTag);
        return tag;
    }

    // Apply the allowed tag
    public static void applyAllowedTag(ArmorStand armorStand, CompoundTag incoming) {
        if (armorStand == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        CompoundTag sanitized = new CompoundTag();
        for (String key : incoming.getAllKeys()) {
            if (ALLOWED_KEYS.contains(key)) {
                sanitized.put(key, incoming.get(key).copy());
            }
        }
        if (sanitized.isEmpty()) {
            return;
        }

        UUID uuid = armorStand.getUUID();
        CompoundTag merged = armorStand.saveWithoutId(new CompoundTag());
        merged.merge(sanitized);
        armorStand.load(merged);
        armorStand.setUUID(uuid);
    }

    // Get the rotation list
    private static ListTag rotationList(float[] values) {
        ListTag list = new ListTag();
        list.add(FloatTag.valueOf(values[0]));
        list.add(FloatTag.valueOf(values[1]));
        list.add(FloatTag.valueOf(values[2]));
        return list;
    }
}
