package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPoseData;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPosePart;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPosePreset;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

// Save and restore Armor Stand Pose Preset data
final class ArmorStandPosePresetStore {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY_NAME = "armor_stand_pose_presets";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the armor stand pose preset store
    private ArmorStandPosePresetStore() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Load the custom presets
    static LoadResult loadCustomPresets() {
        Path dir = directory();
        List<ArmorStandPosePreset> presets = new ArrayList<>();
        int failed = 0;
        try {
            Files.createDirectories(dir);
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> presetFiles = files
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
                for (Path file : presetFiles) {
                    try {
                        presets.add(loadPreset(file));
                    } catch (IOException | JsonParseException | IllegalStateException | ClassCastException ignored) {
                        failed++;
                    }
                }
            }
        } catch (IOException ignored) {
            failed++;
        }
        return new LoadResult(List.copyOf(presets), failed, dir);
    }

    // Save the custom preset
    static Path saveCustomPreset(String requestedName, ArmorStandPoseData data) throws IOException {
        Path dir = directory();
        Files.createDirectories(dir);
        String displayName = requestedName == null || requestedName.isBlank() ? "Custom Pose" : requestedName.strip();
        String fileName = uniqueFileName(dir, sanitizeFileName(displayName));
        Path file = dir.resolve(fileName);
        JsonObject root = new JsonObject();
        root.addProperty("name", displayName);
        JsonObject pose = new JsonObject();
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            pose.add(part.tagKey(), rotationArray(data.pose(part)));
        }
        root.add("pose", pose);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        return file;
    }

    // Load the preset
    private static ArmorStandPosePreset loadPreset(Path file) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }
        if (root == null) {
            throw new JsonParseException("Empty preset file");
        }

        String name = root.has("name") ? root.get("name").getAsString() : fileNameWithoutExtension(file);
        JsonObject pose = root.has("pose") && root.get("pose").isJsonObject()
                ? root.getAsJsonObject("pose")
                : root;
        EnumMap<ArmorStandPosePart, float[]> poses = new EnumMap<>(ArmorStandPosePart.class);
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            poses.put(part, readRotation(pose, part));
        }
        return ArmorStandPosePreset.of("custom/" + fileNameWithoutExtension(file), Component.literal(name), poses);
    }

    // Get the rotation array
    private static JsonArray rotationArray(float[] values) {
        JsonArray array = new JsonArray();
        array.add(values[0]);
        array.add(values[1]);
        array.add(values[2]);
        return array;
    }

    // Read the rotation
    private static float[] readRotation(JsonObject pose, ArmorStandPosePart part) {
        if (!pose.has(part.tagKey()) || !pose.get(part.tagKey()).isJsonArray()) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        JsonArray array = pose.getAsJsonArray(part.tagKey());
        float[] values = new float[]{0.0F, 0.0F, 0.0F};
        for (int i = 0; i < Math.min(3, array.size()); i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    // Get the directory
    private static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("createthrusters")
                .resolve(DIRECTORY_NAME);
    }

    // Get the unique file name
    private static String uniqueFileName(Path dir, String baseName) throws IOException {
        String normalizedBase = baseName.isBlank() ? "custom_pose" : baseName;
        String fileName = normalizedBase + ".json";
        int suffix = 2;
        while (Files.exists(dir.resolve(fileName))) {
            fileName = normalizedBase + "-" + suffix + ".json";
            suffix++;
        }
        return fileName;
    }

    // Sanitize the preset file name
    private static String sanitizeFileName(String val) {
        return val.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    // Get the file name without extension
    private static String fileNameWithoutExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    // Store load results
    record LoadResult(List<ArmorStandPosePreset> presets, int failed, Path directory) {
    }
}
