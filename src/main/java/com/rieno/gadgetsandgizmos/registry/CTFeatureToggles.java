package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Apply server feature flags with the block, item and dependency rules kept in one place
public final class CTFeatureToggles {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String SERVER_CONFIG_FILE = CreateThrusters.MOD_ID + "-server.toml";
    private static final String COMMON_CONFIG_FILE = CreateThrusters.MOD_ID + "-common.toml";
    private static final Pattern BOOLEAN_ENTRY = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(true|false)\\s*(?:#.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^]]+)]\\s*(?:#.*)?$");
    private static final String ACC_DISPLAY_FEATURE = "acc_display";
    private static final String MUSIC_DISCS_FEATURE = "music_discs";
    private static final Set<String> ACC_DISPLAY_IDS = Set.of(
            "acc_display",
            "acc_display_block",
            "acc_display_panel",
            "acc_display_half_panel",
            "acc_display_slab");
    private static final Set<String> MUSIC_DISC_IDS = Set.of(
            "music_disc_kinetic_currency",
            "music_disc_twisted_alive",
            "music_disc_unplug_the_earth");
    private static final Map<String, Boolean> BLOCK_DEFAULTS;
    private static final Map<String, Boolean> ITEM_DEFAULTS;
    private static final Map<String, Boolean> ENTITY_DEFAULTS;
    private static final Map<String, String> BLOCK_DEPENDENCIES;
    private static final Map<String, String> ITEM_BLOCK_DEPENDENCIES;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked server block values
    private static volatile Map<String, Boolean> serverBlockValues;
    // Tracked server item values
    private static volatile Map<String, Boolean> serverItemValues;
    // Tracked server entity values
    private static volatile Map<String, Boolean> serverEntityValues;
    // Tracks whether dedicated server registration is prepared
    private static volatile boolean dedicatedServerRegistrationPrepared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        LinkedHashMap<String, Boolean> blocks = new LinkedHashMap<>();
        define(blocks, "thruster", true);
        define(blocks, "rcs_thruster", true);
        define(blocks, "blackstone_alloy_block", true);
        define(blocks, "fuel_oxidizer", true);
        define(blocks, "thruster_bearing", true);
        define(blocks, "thruster_bearing_link", true);
        define(blocks, "aileron_bearing", true);
        define(blocks, "aileron_bearing_link", true);
        define(blocks, "vector_bearing", true);
        define(blocks, "vector_bearing_link", true);
        define(blocks, "scissor_piston", true);
        define(blocks, "scissor_piston_link", true);
        define(blocks, "scissor_piston_arm", true);
        define(blocks, "gyroscope_link", true);
        define(blocks, "double_button", true);
        define(blocks, "copycat_double_button", true);
        define(blocks, "virtual_orientation_source", false);
        define(blocks, "gyro_redstone_bridge", false);
        define(blocks, "bidirectional_gearbox", true);
        define(blocks, "bi_directional_gearshift", true);
        define(blocks, "analogue_joystick", true);
        define(blocks, "analogue_contraption_controller", true);
        define(blocks, "contraption_network_linker_plane", true);
        define(blocks, "advanced_contraption_controller", true);
        define(blocks, "ship_control_module", true);
        define(blocks, "ship_coupler", true);
        define(blocks, ACC_DISPLAY_FEATURE, true); // Disable ACC Displays in Release
        define(blocks, "universal_display_adapter", true);// Disable ACC Displays adaptor in Release
        define(blocks, "ship_dock", true);
        define(blocks, "advanced_navigation_table", true);
        define(blocks, "diagnostic_tablet", false); // Disable Smart Tablet in Release
        define(blocks, "shipping_manifest", true);
        define(blocks, "alternator", true);
        define(blocks, "claw", true);
        define(blocks, "entity_launcher_anchor", true);
        define(blocks, "powered_zipline", true);
        define(blocks, "rope_knot", true);
        define(blocks, "launcher_endpoint", true);
        define(blocks, "andesite_cable", true);
        define(blocks, "industrial_motor", true);
        define(blocks, "variable_transmission", true);
        define(blocks, "physics_gantry_carriage", true);
        define(blocks, "physics_gantry_shaft", true);
        define(blocks, "physics_gantry_belt_wheel", true);
        define(blocks, "physics_staff_anchor", true);
        BLOCK_DEFAULTS = immutableCopy(blocks);

        LinkedHashMap<String, Boolean> items = new LinkedHashMap<>();
        define(items, "thruster", true);
        define(items, "rcs_thruster", true);
        define(items, "blackstone_alloy", true);
        define(items, "blackstone_sheet", true);
        define(items, "computation_mechanism", true);
        define(items, "incomplete_computation_mechanism", true);
        define(items, "blackstone_alloy_block", true);
        define(items, "small_thruster", true);
        define(items, "fuel_oxidizer", true);
        define(items, "thruster_bearing", true);
        define(items, "aileron_bearing", true);
        define(items, "vector_bearing", true);
        define(items, "scissor_piston", true);
        define(items, "scissor_arms", true);
        define(items, "gyroscope_link", true);
        define(items, "double_button", true);
        define(items, "copycat_double_button", true);
        define(items, "virtual_orientation_source", false);
        define(items, "gyro_redstone_bridge", false);
        define(items, "bidirectional_gearbox", true);
        define(items, "bi_directional_gearshift", true);
        define(items, "vertical_bidirectional_gearbox", true);
        define(items, "analogue_joystick", true);
        define(items, "analogue_contraption_controller", true);
        define(items, "advanced_contraption_controller", true);
        define(items, "ship_control_module", true);
        define(items, "ship_coupler", true);
        define(items, "universal_display_adapter", true);
        define(items, "ship_dock", true);
        define(items, "advanced_navigation_table", true);
        define(items, "diagnostic_tablet", false);
        define(items, "portable_contraption_controller", true);
        define(items, "advanced_portable_contraption_controller", true);
        define(items, "alternator", true);
        define(items, "claw", true);
        define(items, "powered_zipline", true);
        define(items, "rope_knot", true);
        define(items, "andesite_cable", true);
        define(items, "industrial_motor", true);
        define(items, "variable_transmission", true);
        define(items, "vertical_variable_transmission", true);
        define(items, "physics_gantry_carriage", true);
        define(items, "physics_gantry_shaft", true);
        define(items, "physics_gantry_belt_wheel", true);
        define(items, "thruster_lense", true);
        define(items, "processing_upgrade_smoking_t1", true);
        define(items, "processing_upgrade_smoking_t2", true);
        define(items, "processing_upgrade_smoking_t3", true);
        define(items, "processing_upgrade_smoking_t4", true);
        define(items, "processing_upgrade_smelting_t1", true);
        define(items, "processing_upgrade_smelting_t2", true);
        define(items, "processing_upgrade_smelting_t3", true);
        define(items, "processing_upgrade_smelting_t4", true);
        define(items, "processing_upgrade_haunting_t1", true);
        define(items, "processing_upgrade_haunting_t2", true);
        define(items, "processing_upgrade_haunting_t3", true);
        define(items, "processing_upgrade_haunting_t4", true);
        define(items, "propulsion_upgrade_t1", true);
        define(items, "propulsion_upgrade_t2", true);
        define(items, "propulsion_upgrade_t3", true);
        define(items, "propulsion_upgrade_t4", true);
        define(items, "physics_staff", true);
        define(items, "contraption_network_linker", true);
        define(items, "configuration_clipboard", true);
        define(items, "shipping_manifest", true);
        define(items, "shipping_schedule", true);
        define(items, "entity_launcher", true);
        define(items, "player_mannequin", true);
        define(items, "physics_goggles", true);
        define(items, "oxidized_creative_blaze_cake", true);
        define(items, MUSIC_DISCS_FEATURE, true);
        define(items, "music_disc_kinetic_currency", true);
        define(items, "music_disc_twisted_alive", true);
        define(items, "music_disc_unplug_the_earth", true);
        ITEM_DEFAULTS = immutableCopy(items);

        LinkedHashMap<String, Boolean> entities = new LinkedHashMap<>();
        define(entities, "launched_claw", true);
        define(entities, "player_mannequin", true);
        ENTITY_DEFAULTS = immutableCopy(entities);

        LinkedHashMap<String, String> blockDependencies = new LinkedHashMap<>();
        blockDependencies.put("thruster_bearing_link", "thruster_bearing");
        blockDependencies.put("aileron_bearing_link", "aileron_bearing");
        blockDependencies.put("vector_bearing_link", "vector_bearing");
        blockDependencies.put("scissor_piston_link", "scissor_piston");
        blockDependencies.put("scissor_piston_arm", "scissor_piston");
        blockDependencies.put("universal_display_adapter", ACC_DISPLAY_FEATURE);
        BLOCK_DEPENDENCIES = immutableCopy(blockDependencies);

        LinkedHashMap<String, String> itemBlockDependencies = new LinkedHashMap<>();
        itemBlockDependencies.put("thruster", "thruster");
        itemBlockDependencies.put("rcs_thruster", "rcs_thruster");
        itemBlockDependencies.put("blackstone_alloy_block", "blackstone_alloy_block");
        itemBlockDependencies.put("small_thruster", "thruster");
        itemBlockDependencies.put("fuel_oxidizer", "fuel_oxidizer");
        itemBlockDependencies.put("thruster_bearing", "thruster_bearing");
        itemBlockDependencies.put("aileron_bearing", "aileron_bearing");
        itemBlockDependencies.put("vector_bearing", "vector_bearing");
        itemBlockDependencies.put("scissor_piston", "scissor_piston");
        itemBlockDependencies.put("scissor_arms", "scissor_piston");
        itemBlockDependencies.put("gyroscope_link", "gyroscope_link");
        itemBlockDependencies.put("double_button", "double_button");
        itemBlockDependencies.put("copycat_double_button", "copycat_double_button");
        itemBlockDependencies.put("virtual_orientation_source", "virtual_orientation_source");
        itemBlockDependencies.put("gyro_redstone_bridge", "gyro_redstone_bridge");
        itemBlockDependencies.put("bidirectional_gearbox", "bidirectional_gearbox");
        itemBlockDependencies.put("bi_directional_gearshift", "bi_directional_gearshift");
        itemBlockDependencies.put("vertical_bidirectional_gearbox", "bidirectional_gearbox");
        itemBlockDependencies.put("analogue_joystick", "analogue_joystick");
        itemBlockDependencies.put("analogue_contraption_controller", "analogue_contraption_controller");
        itemBlockDependencies.put("advanced_contraption_controller", "advanced_contraption_controller");
        itemBlockDependencies.put("ship_control_module", "ship_control_module");
        itemBlockDependencies.put("ship_coupler", "ship_coupler");
        itemBlockDependencies.put("advanced_navigation_table", "advanced_navigation_table");
        itemBlockDependencies.put("acc_display", ACC_DISPLAY_FEATURE);
        itemBlockDependencies.put("acc_display_block", ACC_DISPLAY_FEATURE);
        itemBlockDependencies.put("acc_display_panel", ACC_DISPLAY_FEATURE);
        itemBlockDependencies.put("acc_display_half_panel", ACC_DISPLAY_FEATURE);
        itemBlockDependencies.put("acc_display_slab", ACC_DISPLAY_FEATURE);
        itemBlockDependencies.put("universal_display_adapter", "universal_display_adapter");
        itemBlockDependencies.put("ship_dock", "ship_dock");
        itemBlockDependencies.put("diagnostic_tablet", "diagnostic_tablet");
        itemBlockDependencies.put("alternator", "alternator");
        itemBlockDependencies.put("claw", "claw");
        itemBlockDependencies.put("powered_zipline", "powered_zipline");
        itemBlockDependencies.put("rope_knot", "rope_knot");
        itemBlockDependencies.put("andesite_cable", "andesite_cable");
        itemBlockDependencies.put("industrial_motor", "industrial_motor");
        itemBlockDependencies.put("variable_transmission", "variable_transmission");
        itemBlockDependencies.put("vertical_variable_transmission", "variable_transmission");
        itemBlockDependencies.put("physics_gantry_carriage", "physics_gantry_carriage");
        itemBlockDependencies.put("physics_gantry_shaft", "physics_gantry_shaft");
        itemBlockDependencies.put("physics_gantry_belt_wheel", "physics_gantry_belt_wheel");
        itemBlockDependencies.put("shipping_manifest", "shipping_manifest");
        itemBlockDependencies.put("oxidized_creative_blaze_cake", "fuel_oxidizer");
        ITEM_BLOCK_DEPENDENCIES = immutableCopy(itemBlockDependencies);
    }

    // Initialize the CT feature toggles
    private CTFeatureToggles() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the block defaults
    public static Map<String, Boolean> blockDefaults() {
        return BLOCK_DEFAULTS;
    }

    // Get the item defaults
    public static Map<String, Boolean> itemDefaults() {
        return ITEM_DEFAULTS;
    }

    // Get the entity defaults
    public static Map<String, Boolean> entityDefaults() {
        return ENTITY_DEFAULTS;
    }

    // Check if this is enabled
    public static boolean isEnabled(String id) {
        String key = normalize(id);
        if (BLOCK_DEFAULTS.containsKey(key) || ACC_DISPLAY_IDS.contains(key)) {
            return isBlockEnabled(key);
        }
        if (ITEM_DEFAULTS.containsKey(key)) {
            return isItemEnabled(key);
        }
        return !ENTITY_DEFAULTS.containsKey(key) || isEntityEnabled(key);
    }

    // Check if the block is enabled
    public static boolean isBlockEnabled(String id) {
        String key = normalize(id);
        if (ACC_DISPLAY_IDS.contains(key)
                && !currentBlockValues().getOrDefault(ACC_DISPLAY_FEATURE, true)) {
            return false;
        }
        if (!currentBlockValues().getOrDefault(key, true)) {
            return false;
        }

        String dependency = BLOCK_DEPENDENCIES.get(key);
        if (dependency != null && !isBlockEnabled(dependency)) {
            return false;
        }

        return switch (key) {
            case "contraption_network_linker_plane" -> rawItemEnabled("contraption_network_linker");
            case "entity_launcher_anchor", "launcher_endpoint" -> rawItemEnabled("entity_launcher")
                    && isEntityEnabled("launched_claw");
            default -> true;
        };
    }

    // Check if the item is enabled
    public static boolean isItemEnabled(String id) {
        String key = normalize(id);
        if (!currentItemValues().getOrDefault(key, true)) {
            return false;
        }
        if (MUSIC_DISC_IDS.contains(key) && !rawItemEnabled(MUSIC_DISCS_FEATURE)) {
            return false;
        }

        String blockDependency = ITEM_BLOCK_DEPENDENCIES.get(key);
        if (blockDependency != null && !isBlockEnabled(blockDependency)) {
            return false;
        }

        return switch (key) {
            case "entity_launcher" -> isBlockEnabled("entity_launcher_anchor")
                    && isBlockEnabled("launcher_endpoint")
                    && isEntityEnabled("launched_claw");
            case "player_mannequin" -> isEntityEnabled("player_mannequin");
            default -> true;
        };
    }

    // Check if the entity is enabled
    public static boolean isEntityEnabled(String id) {
        String key = normalize(id);
        if (!currentEntityValues().getOrDefault(key, true)) {
            return false;
        }
        return switch (key) {
            case "launched_claw" -> rawItemEnabled("entity_launcher")
                    && currentBlockValues().getOrDefault("entity_launcher_anchor", true)
                    && currentBlockValues().getOrDefault("launcher_endpoint", true);
            case "player_mannequin" -> rawItemEnabled("player_mannequin");
            default -> true;
        };
    }

    // Check if the entity launcher is enabled
    public static boolean isEntityLauncherEnabled() {
        return isItemEnabled("entity_launcher");
    }

    // Prepare the dedicated server registration
    public static synchronized void prepareDedicatedServerRegistration() {
        if (dedicatedServerRegistrationPrepared
                || !FMLEnvironment.dist.isDedicatedServer()) {
            return;
        }

        StartupFeatureValues values = readStartupFeatureValues();
        applyServerOverrides(values.blocks(), values.items(), values.entities());
        dedicatedServerRegistrationPrepared = true;
    }

    // Apply the server overrides
    public static void applyServerOverrides(Map<String, Boolean> blockValues,
                                            Map<String, Boolean> itemValues,
                                            Map<String, Boolean> entityValues) {
        serverBlockValues = normalizeFeatureValues(blockValues, BLOCK_DEFAULTS);
        serverItemValues = normalizeFeatureValues(itemValues, ITEM_DEFAULTS);
        serverEntityValues = normalizeFeatureValues(entityValues, ENTITY_DEFAULTS);
    }

    // Clear the server overrides
    public static void clearServerOverrides() {
        serverBlockValues = null;
        serverItemValues = null;
        serverEntityValues = null;
    }

    // Get the feature label
    public static String featureLabel(String key) {
        String normalized = normalize(key);
        return switch (normalized) {
            case ACC_DISPLAY_FEATURE -> "ACC Displays";
            case MUSIC_DISCS_FEATURE -> "Music Discs";
            case "player_mannequin" -> "Supporter Mannequins";
            default -> normalized.replace('_', ' ');
        };
    }

    // Check if the raw item is enabled
    private static boolean rawItemEnabled(String key) {
        return currentItemValues().getOrDefault(key, true);
    }

    // Get the current block values
    private static Map<String, Boolean> currentBlockValues() {
        Map<String, Boolean> values = serverBlockValues;
        return values != null ? values : BLOCK_DEFAULTS;
    }

    // Get the current item values
    private static Map<String, Boolean> currentItemValues() {
        Map<String, Boolean> values = serverItemValues;
        return values != null ? values : ITEM_DEFAULTS;
    }

    // Get the current entity values
    private static Map<String, Boolean> currentEntityValues() {
        Map<String, Boolean> values = serverEntityValues;
        return values != null ? values : ENTITY_DEFAULTS;
    }

    // Normalize the feature values
    private static Map<String, Boolean> normalizeFeatureValues(Map<String, Boolean> src,
                                                               Map<String, Boolean> defaults) {
        LinkedHashMap<String, Boolean> normalized = new LinkedHashMap<>(defaults);
        if (src != null) {
            for (Map.Entry<String, Boolean> entry : src.entrySet()) {
                String key = normalize(entry.getKey());
                if (normalized.containsKey(key)) {
                    normalized.put(key, Boolean.TRUE.equals(entry.getValue()));
                }
            }
        }
        return immutableCopy(normalized);
    }

    // Define the CT feature toggles
    private static void define(Map<String, Boolean> values, String key, boolean defaultValue) {
        values.put(key, defaultValue);
    }

    // Normalize the CT feature toggles
    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    // Get the immutable copy
    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    // Read the startup feature values
    private static StartupFeatureValues readStartupFeatureValues() {
        Map<String, Boolean> blocks = new LinkedHashMap<>(BLOCK_DEFAULTS);
        Map<String, Boolean> items = new LinkedHashMap<>(ITEM_DEFAULTS);
        Map<String, Boolean> entities = new LinkedHashMap<>(ENTITY_DEFAULTS);
        Path configDirectory = FMLPaths.CONFIGDIR.get();

        readFeatureConfig(configDirectory.resolve(COMMON_CONFIG_FILE), blocks, items, entities);
        readFeatureConfig(configDirectory.resolve(SERVER_CONFIG_FILE), blocks, items, entities);
        readDiagnosticTabletConfig(configDirectory.resolve(COMMON_CONFIG_FILE), blocks, items);
        return new StartupFeatureValues(blocks, items, entities);
    }

    // Read the feature config
    private static void readFeatureConfig(Path path,
                                          Map<String, Boolean> blocks,
                                          Map<String, Boolean> items,
                                          Map<String, Boolean> entities) {
        if (!Files.exists(path)) {
            return;
        }

        try {
            String section = "";
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                Matcher sectionMatcher = SECTION.matcher(line);
                if (sectionMatcher.matches()) {
                    section = sectionMatcher.group(1).trim().toLowerCase(Locale.ROOT);
                    continue;
                }

                Matcher entryMatcher = BOOLEAN_ENTRY.matcher(line);
                if (!entryMatcher.matches()) {
                    continue;
                }

                boolean val = Boolean.parseBoolean(entryMatcher.group(2));
                String key = normalize(entryMatcher.group(1));
                if (section.endsWith(".features.blocks")) {
                    setKnownValue(blocks, key, val);
                } else if (section.endsWith(".features.items")) {
                    setKnownValue(items, key, val);
                } else if (section.endsWith(".features.entities")) {
                    setKnownValue(entities, key, val);
                }
            }
        } catch (IOException ignored) {
        }
    }

    // Read the diagnostic tablet config
    private static void readDiagnosticTabletConfig(Path path,
                                                   Map<String, Boolean> blocks,
                                                   Map<String, Boolean> items) {
        if (!Files.exists(path)) {
            return;
        }

        try {
            String section = "";
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                Matcher sectionMatcher = SECTION.matcher(line);
                if (sectionMatcher.matches()) {
                    section = sectionMatcher.group(1).trim().toLowerCase(Locale.ROOT);
                    continue;
                }

                Matcher entryMatcher = BOOLEAN_ENTRY.matcher(line);
                if (entryMatcher.matches()
                        && "common.diagnostic_tablet".equals(section)
                        && "enabled".equalsIgnoreCase(entryMatcher.group(1))) {
                    boolean val = Boolean.parseBoolean(entryMatcher.group(2));
                    blocks.put("diagnostic_tablet", val);
                    items.put("diagnostic_tablet", val);
                }
            }
        } catch (IOException ignored) {
        }
    }

    // Set the known value
    private static void setKnownValue(Map<String, Boolean> values, String key, boolean val) {
        if (values.containsKey(key)) {
            values.put(key, val);
        }
    }

    // Store the startup feature values
    private record StartupFeatureValues(Map<String, Boolean> blocks,
                                        Map<String, Boolean> items,
                                        Map<String, Boolean> entities) {
    }
}
