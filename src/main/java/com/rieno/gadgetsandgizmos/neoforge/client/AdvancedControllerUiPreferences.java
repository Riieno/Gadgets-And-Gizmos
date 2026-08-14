package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Store client-only graph editor layout and display preferences
final class AdvancedControllerUiPreferences {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final State DEFAULTS = new State(
            false, false, false, false, false, 150, 130, false, Set.of());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller UI preferences
    private AdvancedControllerUiPreferences() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Load the advanced controller UI preferences
    static State load() {
        Path path = path();
        if (path == null || !Files.isRegularFile(path)) {
            return DEFAULTS;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            State state = GSON.fromJson(reader, State.class);
            return state == null ? DEFAULTS : state.normalized();
        } catch (Exception ignored) {
            return DEFAULTS;
        }
    }

    // Save the advanced controller UI preferences
    static void save(State state) {
        Path path = path();
        if (path == null || state == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(state.normalized(), writer);
            }
        } catch (Exception ignored) {
        }
    }

    // Get the path
    private static Path path() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath()
                .resolve("config")
                .resolve("createthrusters")
                .resolve("advanced_controller_ui")
                .resolve(playerId(minecraft) + ".json");
    }

    // Get the player id
    private static String playerId(Minecraft minecraft) {
        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        return playerId == null ? "local" : playerId.toString();
    }

    // Store the current state
    record State(boolean leftSidebarCollapsed, boolean rightSidebarCollapsed,
                 boolean optionsCollapsed, boolean targetsCollapsed, boolean variablesCollapsed,
                 int optionsHeight, int targetsHeight, boolean saveOnClose,
                 Set<String> collapsedNodeIds) {
        // Get the normalized
        private State normalized() {
            Set<String> normalizedCollapsedNodes = new LinkedHashSet<>();
            if (collapsedNodeIds != null) {
                collapsedNodeIds.stream()
                        .filter(id -> id != null && !id.isBlank() && id.length() <= 128)
                        .limit(4096)
                        .forEach(normalizedCollapsedNodes::add);
            }
            return new State(leftSidebarCollapsed, rightSidebarCollapsed,
                    optionsCollapsed, targetsCollapsed, variablesCollapsed,
                    Mth.clamp(optionsHeight, 24, 800), Mth.clamp(targetsHeight, 24, 800), saveOnClose,
                    Set.copyOf(normalizedCollapsedNodes));
        }
    }
}
