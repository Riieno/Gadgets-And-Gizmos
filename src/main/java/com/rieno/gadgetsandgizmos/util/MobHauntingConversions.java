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
import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
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

    public static final ResourceLocation RESOURCE_ID = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "mob_haunting_convertions.json");
    public static final ContextAwareReloadListener RELOAD_LISTENER = new ContextAwareReloadListener() {
        // Reload the mob haunting conversions
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor,
                Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> Unit.INSTANCE, backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(ignored -> MobHauntingConversions.reload(resourceManager), gameExecutor);
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

    // Reload the mob haunting conversions
    private static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, ResourceLocation> loaded = new LinkedHashMap<>(DEFAULT_CONVERSIONS);
        int loadedResources = 0;

        for (Resource resource : resourceManager.getResourceStack(RESOURCE_ID)) {
            loadedResources++;
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
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
            } catch (Exception err) {
                LOGGER.warn("Failed to load mob haunting conversions from {}", RESOURCE_ID, err);
            }
        }

        conversions = Map.copyOf(loaded);
        LOGGER.info("Loaded {} mob haunting conversions from {} resource layer(s)",
                conversions.size(), loadedResources);
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
        defaults.put(id("minecraft:husk"), id("minecraft:zombified_piglin"));
        defaults.put(id("minecraft:drowned"), id("minecraft:zombified_piglin"));
        defaults.put(id("minecraft:pig"), id("minecraft:hoglin"));
        defaults.put(id("minecraft:silverfish"), id("minecraft:endermite"));
        defaults.put(id("minecraft:horse"), id("minecraft:skeleton_horse"));
        return Map.copyOf(defaults);
    }

    // Get the id
    private static ResourceLocation id(String id) {
        return ResourceLocation.parse(id);
    }
}
