package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "advanced_controller_ui.json";
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
        Path currentPath = path();
        if (currentPath == null) {
            return DEFAULTS;
        }
        State current = read(currentPath);
        if (current != null) {
            return current;
        }
        for (Path legacyPath : legacyPaths()) {
            State legacy = read(legacyPath);
            if (legacy != null) {
                save(legacy);
                return legacy;
            }
        }
        return DEFAULTS;
    }

    // Save the advanced controller UI preferences
    static void save(State state) {
        Path path = path();
        if (path == null || state == null) {
            return;
        }
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(state.normalized(), writer);
            }
            try {
                Files.move(tempPath, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception err) {
            LOGGER.warn("Could not save ACC UI preferences to {}", path, err);
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception err) {
                LOGGER.debug("Could not remove temporary ACC UI preferences file {}", tempPath, err);
            }
        }
    }

    // Read one preference file
    private static State read(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            State state = GSON.fromJson(reader, State.class);
            return state == null ? null : state.normalized();
        } catch (Exception err) {
            LOGGER.warn("Could not load ACC UI preferences from {}", path, err);
            return null;
        }
    }

    // Get the current preference path
    private static Path path() {
        Path configRoot = configRoot();
        return configRoot == null ? null : configRoot.resolve(FILE_NAME);
    }

    // Get the legacy preference paths
    private static List<Path> legacyPaths() {
        Path configRoot = configRoot();
        if (configRoot == null) {
            return List.of();
        }
        Path legacyRoot = configRoot.resolve("advanced_controller_ui");
        List<Path> paths = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        UUID playerId = minecraft == null || minecraft.player == null
                ? null : minecraft.player.getUUID();
        if (playerId != null) {
            paths.add(legacyRoot.resolve(playerId + ".json"));
        }
        paths.add(legacyRoot.resolve("local.json"));
        return List.copyOf(paths);
    }

    // Get the addon config root
    private static Path configRoot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath()
                .resolve("config")
                .resolve("createthrusters");
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
