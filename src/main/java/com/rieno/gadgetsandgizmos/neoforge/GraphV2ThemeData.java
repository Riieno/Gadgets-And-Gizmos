package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// Store and serialize Graph V2 Theme data
public final class GraphV2ThemeData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation RESOURCE_ID = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "graphv2.json");
    public static final Palette DEFAULT = new Palette(
            0xFF0D1215, 0xFF1B2429, 0xFF192126,
            0xFF11181B, 0xFF202A2F, 0xFF2B353A, 0xFF28271F,
            0xFF39464C, 0xFF59666B, 0xFF2C373D,
            0xFFE8E0CD, 0xFFC7C0B2, 0xFF918C82,
            0xFFC6873F, 0xFFE2AA63, 0xFF704723, 0x66704723,
            0xFFFFF3DD, 0xFFBD6559);
    public static final ContextAwareReloadListener RELOAD_LISTENER = new ContextAwareReloadListener() {
        // Reload the graph V2 theme data
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler,
                                              ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> Unit.INSTANCE, backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(ignored -> GraphV2ThemeData.reload(resourceManager), gameExecutor);
        }
    };

    private static final Logger LOGGER = LogUtils.getLogger();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared current
    private static volatile Palette current = DEFAULT;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph V2 theme data
    private GraphV2ThemeData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the current
    public static Palette current() {
        return current;
    }

    // Reload the graph V2 theme data
    private static void reload(ResourceManager resourceManager) {
        Palette palette = DEFAULT;
        for (Resource resource : resourceManager.getResourceStack(RESOURCE_ID)) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("replace") && root.get("replace").getAsBoolean()) {
                    palette = DEFAULT;
                }
                palette = applyColors(palette, root);
            } catch (Exception err) {
                LOGGER.warn("Failed to load Graph V2 theme data from {}", RESOURCE_ID, err);
            }
        }
        current = palette;
        CTCommonEvents.syncGraphV2ThemeToAllPlayers();
        LOGGER.info("Loaded Graph V2 theme colors from {}", RESOURCE_ID);
    }

    // Apply the colors
    static Palette applyColors(Palette base, JsonObject root) {
        JsonObject colors = root.has("colors") && root.get("colors").isJsonObject()
                ? root.getAsJsonObject("colors") : root;
        return new Palette(
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

    // Get the color
    private static int color(JsonObject colors, String key, int fallback) {
        JsonElement elm = colors.get(key);
        if (elm == null || elm.isJsonNull()) {
            return fallback;
        }
        try {
            if (elm.getAsJsonPrimitive().isNumber()) {
                return elm.getAsInt();
            }
            String val = elm.getAsString().strip();
            if (val.matches("#[0-9a-fA-F]{6}")) {
                return (int) (0xFF000000L | Long.parseLong(val.substring(1), 16));
            }
            if (val.matches("#[0-9a-fA-F]{8}")) {
                return (int) Long.parseLong(val.substring(1), 16);
            }
            if (val.matches("0[xX][0-9a-fA-F]{1,8}")) {
                return (int) Long.parseLong(val.substring(2), 16);
            }
            return (int) Long.parseLong(val);
        } catch (RuntimeException err) {
            LOGGER.warn("Ignoring invalid Graph V2 color '{}' for {}", elm, key);
            return fallback;
        }
    }

    // Store the palette
    public record Palette(
            int canvasBackground,
            int canvasDot,
            int titleBackground,
            int panelBackground,
            int panelRaised,
            int panelHovered,
            int panelSelected,
            int border,
            int borderStrong,
            int borderSoft,
            int primary,
            int secondary,
            int muted,
            int accent,
            int accentLight,
            int accentDark,
            int accentOverlay,
            int primaryActionText,
            int danger) {
    }
}
