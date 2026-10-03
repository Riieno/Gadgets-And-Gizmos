package com.rieno.gadgetsandgizmos.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// Apply the configured haunting conversions without changing unrelated entity drops
public final class MobHauntingConversions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String SERVER_CONFIG_FILE = "gadgetsandgizmos_mob_haunting.json";
    private static final String DEFAULT_CONFIG_RESOURCE = "/createthrusters/default-mob-haunting.json";
    public static final ContextAwareReloadListener RELOAD_LISTENER = new ContextAwareReloadListener() {
        // Reload the mob haunting conversions
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor,
                Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> Unit.INSTANCE, backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(ignored -> MobHauntingConversions.reload(), gameExecutor);
        }
    };

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ResourceLocation> DEFAULT_CONVERSIONS = createDefaultConversions();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked conversions
    private static volatile Map<ResourceLocation, ResourceLocation> conversions = DEFAULT_CONVERSIONS;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the mob haunting conversions
    private MobHauntingConversions() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the conversion type
    public static @Nullable EntityType<?> getConversionType(EntityType<?> sourceType) {
        ResourceLocation sourceId = BuiltInRegistries.ENTITY_TYPE.getKey(sourceType);
        ResourceLocation targetId = sourceId == null ? null : conversions.get(sourceId);
        return targetId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(targetId).orElse(null);
    }

    // Reload the server-authoritative mob haunting conversions.
    private static void reload() {
        JsonObject root = loadServerConfigRoot();
        if (root == null) {
            conversions = DEFAULT_CONVERSIONS;
            return;
        }

        Map<ResourceLocation, ResourceLocation> loaded = new LinkedHashMap<>();
        try {
            applyConversionDefinitions(root, loaded);
            conversions = Map.copyOf(loaded);
            LOGGER.info("Loaded {} mob haunting conversions from server config {}",
                    conversions.size(), serverConfigPath());
        } catch (Exception err) {
            conversions = DEFAULT_CONVERSIONS;
            LOGGER.warn("Could not apply mob haunting conversions from {}; bundled defaults will be used",
                    serverConfigPath(), err);
        }
    }

    // Load the server config, creating it from the bundled defaults on first run.
    private static @Nullable JsonObject loadServerConfigRoot() {
        Path path = serverConfigPath();
        if (Files.notExists(path)) {
            try (InputStream defaults = MobHauntingConversions.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (defaults == null) {
                    LOGGER.error("Bundled mob haunting defaults are missing from {}", DEFAULT_CONFIG_RESOURCE);
                    return null;
                }
                Files.createDirectories(path.getParent());
                Files.copy(defaults, path);
                LOGGER.info("Created server mob haunting config at {}", path);
            } catch (IOException err) {
                LOGGER.warn("Could not create server mob haunting config at {}; bundled defaults will be used",
                        path, err);
                return loadBundledDefaultRoot();
            }
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("The root value must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (Exception err) {
            LOGGER.warn("Could not read server mob haunting config at {}; bundled defaults will be used",
                    path, err);
            return loadBundledDefaultRoot();
        }
    }

    // Load bundled defaults when the server config cannot be created or read.
    private static @Nullable JsonObject loadBundledDefaultRoot() {
        try (InputStream input = MobHauntingConversions.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (input == null) {
                LOGGER.error("Bundled mob haunting defaults are missing from {}", DEFAULT_CONFIG_RESOURCE);
                return null;
            }
            try (Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception err) {
            LOGGER.error("Could not read bundled mob haunting defaults from {}", DEFAULT_CONFIG_RESOURCE, err);
            return null;
        }
    }

    // Get the server config path.
    static Path serverConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(SERVER_CONFIG_FILE);
    }

    // Apply one complete conversion table.
    private static void applyConversionDefinitions(JsonObject root, Map<ResourceLocation, ResourceLocation> loaded) {
        if (root.has("replace") && root.get("replace").getAsBoolean()) {
            loaded.clear();
        }
        if (root.has("conversions") && root.get("conversions").isJsonObject()) {
            readConversionObject(root.getAsJsonObject("conversions"), loaded);
        } else if (root.has("values") && root.get("values").isJsonArray()) {
            readConversionValues(root.getAsJsonArray("values"), loaded);
        } else {
            readConversionObject(root, loaded);
        }
    }

    // Read the conversion object
    private static void readConversionObject(JsonObject object, Map<ResourceLocation, ResourceLocation> loaded) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            if ("replace".equals(key) || "values".equals(key) || "conversions".equals(key)) {
                continue;
            }

            JsonElement val = entry.getValue();
            String targetId = null;
            boolean enabled = true;
            if (val.isJsonPrimitive()) {
                targetId = val.getAsString();
            } else if (val.isJsonObject()) {
                JsonObject targetObject = val.getAsJsonObject();
                enabled = !targetObject.has("enabled") || targetObject.get("enabled").getAsBoolean();
                targetId = readTargetId(targetObject);
            }

            readConversionEntry(key, targetId, enabled, loaded);
        }
    }

    // Read the conversion values
    private static void readConversionValues(JsonArray values, Map<ResourceLocation, ResourceLocation> loaded) {
        for (JsonElement elm : values) {
            if (!elm.isJsonObject()) {
                LOGGER.warn("Skipping invalid mob haunting conversion entry '{}'", elm);
                continue;
            }

            JsonObject object = elm.getAsJsonObject();
            String sourceId = readSourceId(object);
            String targetId = readTargetId(object);
            boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
            readConversionEntry(sourceId, targetId, enabled, loaded);
        }
    }

    // Read the conversion entry
    private static void readConversionEntry(@Nullable String sourceId, @Nullable String targetId, boolean enabled,
            Map<ResourceLocation, ResourceLocation> loaded) {
        if (sourceId == null || sourceId.isBlank()) {
            LOGGER.warn("Skipping mob haunting conversion without a source entity id");
            return;
        }

        ResourceLocation src = ResourceLocation.tryParse(sourceId);
        if (src == null) {
            LOGGER.warn("Skipping invalid mob haunting conversion source '{}'", sourceId);
            return;
        }

        if (!enabled) {
            loaded.remove(src);
            return;
        }

        if (targetId == null || targetId.isBlank()) {
            LOGGER.warn("Skipping mob haunting conversion {} without a target entity id", src);
            return;
        }

        ResourceLocation target = ResourceLocation.tryParse(targetId);
        if (target == null) {
            LOGGER.warn("Skipping invalid mob haunting conversion target '{}' for {}", targetId, src);
            return;
        }

        if (BuiltInRegistries.ENTITY_TYPE.getOptional(src).isEmpty()) {
            LOGGER.warn("Skipping mob haunting conversion for unknown source entity {}", src);
            return;
        }
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(target).isEmpty()) {
            LOGGER.warn("Skipping mob haunting conversion for unknown target entity {}", target);
            return;
        }

        loaded.put(src, target);
    }

    // Read the source id
    private static @Nullable String readSourceId(JsonObject object) {
        if (object.has("from")) {
            return object.get("from").getAsString();
        }
        if (object.has("source")) {
            return object.get("source").getAsString();
        }
        if (object.has("entity")) {
            return object.get("entity").getAsString();
        }
        return null;
    }

    // Read the target id
    private static @Nullable String readTargetId(JsonObject object) {
        if (object.has("to")) {
            return object.get("to").getAsString();
        }
        if (object.has("target")) {
            return object.get("target").getAsString();
        }
        if (object.has("result")) {
            return object.get("result").getAsString();
        }
        if (object.has("conversion")) {
            return object.get("conversion").getAsString();
        }
        return null;
    }

    // Create the default conversions
    private static Map<ResourceLocation, ResourceLocation> createDefaultConversions() {
        Map<ResourceLocation, ResourceLocation> defaults = new LinkedHashMap<>();
        defaults.put(id("minecraft:skeleton"), id("minecraft:wither_skeleton"));
        defaults.put(id("minecraft:stray"), id("minecraft:wither_skeleton"));
        defaults.put(id("minecraft:bogged"), id("minecraft:wither_skeleton"));
        defaults.put(id("minecraft:slime"), id("minecraft:magma_cube"));
        defaults.put(id("minecraft:zombie"), id("minecraft:zombified_piglin"));
        defaults.put(id("minecraft:husk"), id("minecraft:piglin_brute"));
        defaults.put(id("minecraft:drowned"), id("minecraft:zombified_piglin"));
        defaults.put(id("minecraft:pig"), id("minecraft:hoglin"));
        defaults.put(id("minecraft:silverfish"), id("minecraft:endermite"));
        defaults.put(id("minecraft:horse"), id("minecraft:skeleton_horse"));
        defaults.put(id("minecraft:villager"), id("minecraft:piglin"));
        defaults.put(id("minecraft:piglin"), id("minecraft:enderman"));
        return Map.copyOf(defaults);
    }

    // Get the id
    private static ResourceLocation id(String id) {
        return ResourceLocation.parse(id);
    }
}
