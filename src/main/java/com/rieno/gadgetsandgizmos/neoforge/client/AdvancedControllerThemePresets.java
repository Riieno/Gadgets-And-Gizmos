package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.neoforge.GraphV2ThemeData;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Load and save the client-local Advanced Contraption Controller theme presets.
final class AdvancedControllerThemePresets {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final String DEFAULT_ID = "default";
    private static final String DIRECTORY_NAME = "acc_theme";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC theme preset store.
    private AdvancedControllerThemePresets() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Load Default followed by every readable user theme.
    static List<Preset> load() {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset(DEFAULT_ID, "Default", GraphV2ThemeData.current(), true));
        Path directory = directory();
        if (directory == null || !Files.isDirectory(directory)) return List.copyOf(presets);
        try (var files = Files.list(directory)) {
            files.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(AdvancedControllerThemePresets::read)
                    .filter(preset -> preset != null)
                    .forEach(presets::add);
        } catch (Exception err) {
            LOGGER.warn("Could not list ACC theme presets in {}", directory, err);
        }
        return List.copyOf(presets);
    }

    // Create an unsaved preset with a unique stable file identifier.
    static Preset create(String name, GraphV2ThemeData.Palette palette, List<Preset> existing) {
        Set<String> ids = new LinkedHashSet<>();
        if (existing != null) existing.stream().map(Preset::id).forEach(ids::add);
        String base = idFor(name);
        String id = base;
        int suffix = 2;
        while (ids.contains(id)) id = base + "_" + suffix++;
        return new Preset(id, displayName(name), palette == null ? GraphV2ThemeData.DEFAULT : palette, false);
    }

    // Save one custom preset to its own JSON file.
    static Preset save(Preset preset, String name, GraphV2ThemeData.Palette palette) {
        if (preset == null || preset.locked()) return null;
        Path directory = directory();
        if (directory == null) return null;
        String id = idFor(preset.id());
        String displayName = displayName(name);
        GraphV2ThemeData.Palette resolved = palette == null ? GraphV2ThemeData.DEFAULT : palette;
        Path path = directory.resolve(id + ".json");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(displayName, resolved), writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Preset(id, displayName, resolved, false);
        } catch (Exception err) {
            LOGGER.warn("Could not save ACC theme preset to {}", path, err);
            return null;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception err) {
                LOGGER.debug("Could not remove temporary ACC theme file {}", temporary, err);
            }
        }
    }

    // Get the user-visible directory used by this client.
    static Path directory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) return null;
        return minecraft.gameDirectory.toPath().resolve("config").resolve("createthrusters").resolve(DIRECTORY_NAME);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read one saved theme file.
    private static Preset read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) return null;
            JsonObject root = element.getAsJsonObject();
            String fileName = path.getFileName().toString();
            String id = idFor(fileName.substring(0, fileName.length() - 5));
            if (DEFAULT_ID.equals(id)) return null;
            String name = root.has("name") ? displayName(root.get("name").getAsString()) : displayName(id);
            return new Preset(id, name, readPalette(root), false);
        } catch (Exception err) {
            LOGGER.warn("Could not read ACC theme preset {}", path, err);
            return null;
        }
    }

    // Read every supported Graph V2 theme color from one preset document.
    private static GraphV2ThemeData.Palette readPalette(JsonObject root) {
        JsonObject colors = root.has("colors") && root.get("colors").isJsonObject()
                ? root.getAsJsonObject("colors") : root;
        GraphV2ThemeData.Palette base = GraphV2ThemeData.DEFAULT;
        return new GraphV2ThemeData.Palette(
                color(colors, "canvas_background", base.canvasBackground()),
                color(colors, "canvas_dot", base.canvasDot()),
                color(colors, "title_background", base.titleBackground()),
                color(colors, "panel_background", base.panelBackground()),
                color(colors, "panel_raised", base.panelRaised()),
                color(colors, "panel_hovered", base.panelHovered()),
                color(colors, "panel_selected", base.panelSelected()),
                color(colors, "border", base.border()),
                color(colors, "border_strong", base.borderStrong()),
                color(colors, "border_soft", base.borderSoft()),
                color(colors, "primary", base.primary()),
                color(colors, "secondary", base.secondary()),
                color(colors, "muted", base.muted()),
                color(colors, "accent", base.accent()),
                color(colors, "accent_light", base.accentLight()),
                color(colors, "accent_dark", base.accentDark()),
                color(colors, "accent_overlay", base.accentOverlay()),
                color(colors, "primary_action_text", base.primaryActionText()),
                color(colors, "danger", base.danger()));
    }

    // Create a saved preset document matching the existing Graph V2 theme schema.
    private static JsonObject toJson(String name, GraphV2ThemeData.Palette palette) {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        root.addProperty("color_format", "#RRGGBB or #AARRGGBB");
        JsonObject colors = new JsonObject();
        colors.addProperty("canvas_background", colorHex(palette.canvasBackground()));
        colors.addProperty("canvas_dot", colorHex(palette.canvasDot()));
        colors.addProperty("title_background", colorHex(palette.titleBackground()));
        colors.addProperty("panel_background", colorHex(palette.panelBackground()));
        colors.addProperty("panel_raised", colorHex(palette.panelRaised()));
        colors.addProperty("panel_hovered", colorHex(palette.panelHovered()));
        colors.addProperty("panel_selected", colorHex(palette.panelSelected()));
        colors.addProperty("border", colorHex(palette.border()));
        colors.addProperty("border_strong", colorHex(palette.borderStrong()));
        colors.addProperty("border_soft", colorHex(palette.borderSoft()));
        colors.addProperty("primary", colorHex(palette.primary()));
        colors.addProperty("secondary", colorHex(palette.secondary()));
        colors.addProperty("muted", colorHex(palette.muted()));
        colors.addProperty("accent", colorHex(palette.accent()));
        colors.addProperty("accent_light", colorHex(palette.accentLight()));
        colors.addProperty("accent_dark", colorHex(palette.accentDark()));
        colors.addProperty("accent_overlay", colorHex(palette.accentOverlay()));
        colors.addProperty("primary_action_text", colorHex(palette.primaryActionText()));
        colors.addProperty("danger", colorHex(palette.danger()));
        root.add("colors", colors);
        return root;
    }

    // Read one palette color without allowing malformed values to poison the preset.
    private static int color(JsonObject colors, String key, int fallback) {
        JsonElement element = colors.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsInt();
            String value = element.getAsString().strip();
            if (value.matches("#[0-9a-fA-F]{6}")) return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
            if (value.matches("#[0-9a-fA-F]{8}")) return (int) Long.parseLong(value.substring(1), 16);
            if (value.matches("0[xX][0-9a-fA-F]{1,8}")) return (int) Long.parseLong(value.substring(2), 16);
            return (int) Long.parseLong(value);
        } catch (RuntimeException err) {
            return fallback;
        }
    }

    // Convert a color to the documented JSON hex format.
    private static String colorHex(int color) {
        return (color >>> 24 & 0xFF) == 0xFF ? String.format("#%06X", color & 0x00FFFFFF)
                : String.format("#%08X", color);
    }

    // Normalize one human readable preset name.
    private static String displayName(String name) {
        String value = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (value.isBlank()) return "New Theme";
        if (DEFAULT_ID.equalsIgnoreCase(value)) return "Custom Theme";
        return value.substring(0, Math.min(48, value.length()));
    }

    // Create a safe theme file identifier.
    private static String idFor(String value) {
        String id = value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        return id.isBlank() || DEFAULT_ID.equals(id) ? "theme" : id.substring(0, Math.min(48, id.length()));
    }

    // Store one loadable theme preset.
    record Preset(String id, String name, GraphV2ThemeData.Palette palette, boolean locked) {
    }
}
