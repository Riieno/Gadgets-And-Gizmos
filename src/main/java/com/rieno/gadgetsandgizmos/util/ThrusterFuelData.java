package com.rieno.gadgetsandgizmos.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.profiling.ProfilerFiller;

// Resolve item and fluid fuels into the burn time and power values used by every thruster
public final class ThrusterFuelData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation RESOURCE_ID = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "thruster_fuels.json");
    private static final ResourceLocation OXIDIZED_TAG_RESOURCE_ID = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "tags/fluid/oxidized_fuels.json");
    private static final String SERVER_CONFIG_FILE = CreateThrusters.MOD_ID + "-fuels.json";
    private static final String DEFAULT_CONFIG_RESOURCE = "/createthrusters/default-fuels.json";
    public static final ContextAwareReloadListener RELOAD_LISTENER = new ContextAwareReloadListener() {
        // Reload the thruster fuel data
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor,
                Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> Unit.INSTANCE, backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(ignored -> ThrusterFuelData.reload(resourceManager, getContext()), gameExecutor);
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

    // Tracked fuel profiles
    private static volatile Map<ResourceLocation, FuelProfile> fuelProfiles = Map.of();
    // Tracked solid item profiles
    private static volatile Map<ResourceLocation, ItemFuelProfile> solidItemProfiles = Map.of();
    // Tracked configured oxidized fluid ids
    private static volatile Set<ResourceLocation> configuredOxidizedFluidIds = Set.of();

    // Store the fuel profile
    public record FuelProfile(double burnTimeTicksPerBucket, double propulsionMultiplier, double weight,
            String particleStyle) {
    }

    // Store the item fuel profile
    public record ItemFuelProfile(int burnTicks, boolean infiniteBurn, double propulsionMultiplier,
            String particleStyle, String fuelId) {
    }

    // Store the fuel profile patch
    private record FuelProfilePatch(@Nullable Boolean enabled, @Nullable Double burnTime, @Nullable Double propulsion,
            @Nullable Double weight, @Nullable String particleStyle) {
        // Merge the fuel profile patch
        FuelProfilePatch merge(FuelProfilePatch overrides) {
            return new FuelProfilePatch(
                    overrides.enabled != null ? overrides.enabled : enabled,
                    overrides.burnTime != null ? overrides.burnTime : burnTime,
                    overrides.propulsion != null ? overrides.propulsion : propulsion,
                    overrides.weight != null ? overrides.weight : weight,
                    overrides.particleStyle != null ? overrides.particleStyle : particleStyle);
        }
    }

    // Store the item fuel profile patch
    private record ItemFuelProfilePatch(@Nullable Boolean enabled, @Nullable Integer burnTicks,
            @Nullable Boolean infiniteBurn, @Nullable Double propulsion, @Nullable String particleStyle) {
        // Merge the item fuel profile patch
        ItemFuelProfilePatch merge(ItemFuelProfilePatch overrides) {
            return new ItemFuelProfilePatch(
                    overrides.enabled != null ? overrides.enabled : enabled,
                    overrides.burnTicks != null ? overrides.burnTicks : burnTicks,
                    overrides.infiniteBurn != null ? overrides.infiniteBurn : infiniteBurn,
                    overrides.propulsion != null ? overrides.propulsion : propulsion,
                    overrides.particleStyle != null ? overrides.particleStyle : particleStyle);
        }
    }

    // Store the tagged fluid fuel patch
    private record TaggedFluidFuelPatch(TagKey<Fluid> tag, FuelProfilePatch patch) {
    }

    // Store the tagged item fuel patch
    private record TaggedItemFuelPatch(TagKey<Item> tag, ItemFuelProfilePatch patch) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster fuel data
    private ThrusterFuelData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is fuel
    public static boolean isFuel(Fluid fluid) {
        return getProfile(fluid) != null;
    }

    // Get the profile
    public static @Nullable FuelProfile getProfile(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id == null ? null : fuelProfiles.get(id);
    }

    // Get the solid fuel profile
    public static @Nullable ItemFuelProfile getSolidFuelProfile(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = stack.getItem().builtInRegistryHolder().key().location();
        return solidItemProfiles.get(id);
    }

    // Check if the configured fuel is oxidized
    public static boolean isConfiguredOxidized(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id != null && configuredOxidizedFluidIds.contains(id);
    }

    // Reload the thruster fuel data
    private static void reload(ResourceManager resourceManager, ICondition.IContext tagContext) {
        Map<ResourceLocation, FuelProfilePatch> liquidItemPatches = new LinkedHashMap<>();
        List<TaggedFluidFuelPatch> liquidTagPatches = new ArrayList<>();
        Map<ResourceLocation, ItemFuelProfilePatch> solidItemPatches = new LinkedHashMap<>();
        List<TaggedItemFuelPatch> solidTagPatches = new ArrayList<>();
        Set<ResourceLocation> configuredOxidizedItems = new LinkedHashSet<>();
        List<TagKey<Fluid>> configuredOxidizedTags = new ArrayList<>();

        JsonObject serverConfig = loadServerConfigRoot();
        if (serverConfig != null) {
            try {
                applyFuelDefinitions(serverConfig, liquidItemPatches, liquidTagPatches,
                        solidItemPatches, solidTagPatches);
                readConfiguredOxidizedFuels(serverConfig, configuredOxidizedItems, configuredOxidizedTags);
            } catch (Exception err) {
                LOGGER.warn("Failed to apply thruster fuel definitions from {}", serverConfigPath(), err);
            }
        }

        for (Resource resource : resourceManager.getResourceStack(RESOURCE_ID)) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                applyFuelDefinitions(root, liquidItemPatches, liquidTagPatches,
                        solidItemPatches, solidTagPatches);
            } catch (Exception err) {
                LOGGER.warn("Failed to load thruster fuel data from {}", RESOURCE_ID, err);
            }
        }

        fuelProfiles = Map.copyOf(resolveLiquidProfiles(liquidItemPatches, liquidTagPatches, tagContext));
        solidItemProfiles = Map.copyOf(resolveSolidProfiles(solidItemPatches, solidTagPatches, tagContext));
        configuredOxidizedFluidIds = datapackReplacesOxidizedConfig(resourceManager)
                ? Set.of()
                : Set.copyOf(resolveConfiguredOxidizedFuels(
                        configuredOxidizedItems, configuredOxidizedTags, tagContext));
        LOGGER.info("Loaded {} resolved thruster liquid fuels from {} exact definitions and {} tag definitions",
                fuelProfiles.size(), liquidItemPatches.size(), liquidTagPatches.size());
        LOGGER.info("Loaded {} resolved thruster solid fuels from {} exact definitions and {} tag definitions",
                solidItemProfiles.size(), solidItemPatches.size(), solidTagPatches.size());
        LOGGER.info("Loaded {} oxidized fuels from the server fuel config before datapack tag additions",
                configuredOxidizedFluidIds.size());
    }

    // Apply the fuel definitions
    private static void applyFuelDefinitions(JsonObject root,
            Map<ResourceLocation, FuelProfilePatch> liquidItemPatches,
            List<TaggedFluidFuelPatch> liquidTagPatches,
            Map<ResourceLocation, ItemFuelProfilePatch> solidItemPatches,
            List<TaggedItemFuelPatch> solidTagPatches) {
        if (root.has("replace") && root.get("replace").getAsBoolean()) {
            liquidItemPatches.clear();
            liquidTagPatches.clear();
            solidItemPatches.clear();
            solidTagPatches.clear();
        }
        if (root.has("fluid") && root.get("fluid").isJsonObject()) {
            readLiquidFuelData(root.getAsJsonObject("fluid"), liquidItemPatches, liquidTagPatches);
        } else if (root.has("values") && root.get("values").isJsonArray()) {
            readLiquidFuelValues(root.getAsJsonArray("values"), liquidItemPatches, liquidTagPatches);
        } else if (root.has("fuel_data") && root.get("fuel_data").isJsonObject()) {
            readLiquidFuelData(root.getAsJsonObject("fuel_data"), liquidItemPatches, liquidTagPatches);
        }
        if (root.has("solid") && root.get("solid").isJsonObject()) {
            readSolidFuelData(root.getAsJsonObject("solid"), solidItemPatches, solidTagPatches);
        } else if (root.has("solid_fuel_data") && root.get("solid_fuel_data").isJsonObject()) {
            readSolidFuelData(root.getAsJsonObject("solid_fuel_data"), solidItemPatches, solidTagPatches);
        }
    }

    // Load the server config root
    private static @Nullable JsonObject loadServerConfigRoot() {
        Path path = serverConfigPath();
        if (Files.notExists(path)) {
            try (InputStream defaults = ThrusterFuelData.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (defaults == null) {
                    LOGGER.error("Bundled thruster fuel defaults are missing from {}", DEFAULT_CONFIG_RESOURCE);
                    return null;
                }
                Files.createDirectories(path.getParent());
                Files.copy(defaults, path);
                LOGGER.info("Created server thruster fuel config at {}", path);
            } catch (IOException err) {
                LOGGER.warn("Could not create server thruster fuel config at {}; bundled defaults will be used", path,
                        err);
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
            LOGGER.warn("Could not read server thruster fuel config at {}; bundled defaults will be used", path,
                    err);
            return loadBundledDefaultRoot();
        }
    }

    // Load the bundled default root
    private static @Nullable JsonObject loadBundledDefaultRoot() {
        try (InputStream input = ThrusterFuelData.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (input == null) {
                LOGGER.error("Bundled thruster fuel defaults are missing from {}", DEFAULT_CONFIG_RESOURCE);
                return null;
            }
            try (Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception err) {
            LOGGER.error("Could not read bundled thruster fuel defaults from {}", DEFAULT_CONFIG_RESOURCE, err);
            return null;
        }
    }

    // Get the server config path
    static Path serverConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(SERVER_CONFIG_FILE);
    }

    // Read the configured oxidized fuels
    private static void readConfiguredOxidizedFuels(JsonObject root, Set<ResourceLocation> loadedItems,
            List<TagKey<Fluid>> loadedTags) {
        if (!root.has("oxidized_fuels")) {
            return;
        }
        JsonElement section = root.get("oxidized_fuels");
        JsonArray values;
        if (section.isJsonArray()) {
            values = section.getAsJsonArray();
        } else if (section.isJsonObject()) {
            JsonObject object = section.getAsJsonObject();
            if (object.has("replace") && object.get("replace").getAsBoolean()) {
                loadedItems.clear();
                loadedTags.clear();
            }
            if (!object.has("values") || !object.get("values").isJsonArray()) {
                return;
            }
            values = object.getAsJsonArray("values");
        } else {
            LOGGER.warn("Ignoring oxidized_fuels because it must be an object or array");
            return;
        }

        for (JsonElement elm : values) {
            String id = oxidizedFuelId(elm);
            if (id == null || id.isBlank()) {
                LOGGER.warn("Skipping invalid oxidized fuel entry '{}'", elm);
                continue;
            }
            if (id.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
                if (tagId == null) {
                    LOGGER.warn("Skipping invalid oxidized fuel tag '{}'", id);
                    continue;
                }
                loadedTags.add(TagKey.create(Registries.FLUID, tagId));
                continue;
            }
            ResourceLocation fluidId = ResourceLocation.tryParse(id);
            if (fluidId == null) {
                LOGGER.warn("Skipping invalid oxidized fuel id '{}'", id);
                continue;
            }
            loadedItems.add(fluidId);
        }
    }

    // Get the oxidized fuel id
    private static @Nullable String oxidizedFuelId(JsonElement elm) {
        if (elm.isJsonPrimitive() && elm.getAsJsonPrimitive().isString()) {
            return elm.getAsString();
        }
        if (elm.isJsonObject() && elm.getAsJsonObject().has("id")) {
            return elm.getAsJsonObject().get("id").getAsString();
        }
        return null;
    }

    // Resolve the configured oxidized fuels
    private static Set<ResourceLocation> resolveConfiguredOxidizedFuels(Set<ResourceLocation> exactItems,
            List<TagKey<Fluid>> tags, ICondition.IContext tagContext) {
        Set<ResourceLocation> resolved = new LinkedHashSet<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Fluid>, Fluid> entry : BuiltInRegistries.FLUID.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (exactItems.contains(id)) {
                resolved.add(id);
                continue;
            }
            for (TagKey<Fluid> tag : tags) {
                if (tagContext.getTag(tag).contains(entry.getValue().builtInRegistryHolder())) {
                    resolved.add(id);
                    break;
                }
            }
        }
        return resolved;
    }

    // Check if the datapack replaces the oxidized config
    private static boolean datapackReplacesOxidizedConfig(ResourceManager resourceManager) {
        boolean replaced = false;
        for (Resource resource : resourceManager.getResourceStack(OXIDIZED_TAG_RESOURCE_ID)) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                replaced |= root.has("replace") && root.get("replace").getAsBoolean();
            } catch (Exception err) {
                LOGGER.warn("Failed to inspect oxidized fuel datapack overrides from {}", OXIDIZED_TAG_RESOURCE_ID,
                        err);
            }
        }
        return replaced;
    }

    // Resolve the liquid profiles
    private static Map<ResourceLocation, FuelProfile> resolveLiquidProfiles(
            Map<ResourceLocation, FuelProfilePatch> exactPatches, List<TaggedFluidFuelPatch> tagPatches,
            ICondition.IContext tagContext) {
        Map<ResourceLocation, FuelProfile> resolvedProfiles = new LinkedHashMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Fluid>, Fluid> entry : BuiltInRegistries.FLUID.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            FluidProfileResolution resolution = resolveLiquidPatch(entry.getValue(), exactPatches.get(id), tagPatches,
                    tagContext);
            if (resolution.patch() == null) {
                continue;
            }
            FuelProfile profile = finishLiquidProfile(id, resolution.patch(), resolution.exactDefinition());
            if (profile != null) {
                resolvedProfiles.put(id, profile);
            }
        }
        return resolvedProfiles;
    }

    // Resolve the liquid patch
    private static FluidProfileResolution resolveLiquidPatch(Fluid fluid, @Nullable FuelProfilePatch exactPatch,
            List<TaggedFluidFuelPatch> tagPatches, ICondition.IContext tagContext) {
        FuelProfilePatch resolved = null;
        for (TaggedFluidFuelPatch taggedPatch : tagPatches) {
            if (tagContext.getTag(taggedPatch.tag()).contains(fluid.builtInRegistryHolder())) {
                resolved = resolved == null ? taggedPatch.patch() : resolved.merge(taggedPatch.patch());
            }
        }
        if (exactPatch != null) {
            resolved = resolved == null ? exactPatch : resolved.merge(exactPatch);
        }
        return new FluidProfileResolution(resolved, exactPatch != null);
    }

    // Resolve the solid profiles
    private static Map<ResourceLocation, ItemFuelProfile> resolveSolidProfiles(
            Map<ResourceLocation, ItemFuelProfilePatch> exactPatches, List<TaggedItemFuelPatch> tagPatches,
            ICondition.IContext tagContext) {
        Map<ResourceLocation, ItemFuelProfile> resolvedProfiles = new LinkedHashMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            ItemFuelProfilePatch resolved = null;
            for (TaggedItemFuelPatch taggedPatch : tagPatches) {
                if (tagContext.getTag(taggedPatch.tag()).contains(entry.getValue().builtInRegistryHolder())) {
                    resolved = resolved == null ? taggedPatch.patch() : resolved.merge(taggedPatch.patch());
                }
            }
            ItemFuelProfilePatch exactPatch = exactPatches.get(id);
            if (exactPatch != null) {
                resolved = resolved == null ? exactPatch : resolved.merge(exactPatch);
            }
            if (resolved == null) {
                continue;
            }
            ItemFuelProfile profile = finishItemProfile(id, resolved, exactPatch != null);
            if (profile != null) {
                resolvedProfiles.put(id, profile);
            }
        }
        return resolvedProfiles;
    }

    // Read the liquid fuel values
    private static void readLiquidFuelValues(JsonArray values, Map<ResourceLocation, FuelProfilePatch> loadedItems,
            List<TaggedFluidFuelPatch> loadedTags) {
        for (JsonElement elm : values) {
            if (!elm.isJsonObject()) {
                LOGGER.warn("Skipping invalid thruster liquid fuel entry '{}'", elm);
                continue;
            }
            JsonObject object = elm.getAsJsonObject();
            if (!object.has("id")) {
                LOGGER.warn("Skipping thruster liquid fuel entry without an id");
                continue;
            }
            readLiquidFuelEntry(object.get("id").getAsString(), object, loadedItems, loadedTags);
        }
    }

    // Read the liquid fuel data
    private static void readLiquidFuelData(JsonObject fuelData, Map<ResourceLocation, FuelProfilePatch> loadedItems,
            List<TaggedFluidFuelPatch> loadedTags) {
        for (Map.Entry<String, JsonElement> entry : fuelData.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("Skipping invalid thruster liquid fuel entry '{}'", entry.getKey());
                continue;
            }
            readLiquidFuelEntry(entry.getKey(), entry.getValue().getAsJsonObject(), loadedItems, loadedTags);
        }
    }

    // Read the liquid fuel entry
    private static void readLiquidFuelEntry(String id, JsonObject object,
            Map<ResourceLocation, FuelProfilePatch> loadedItems, List<TaggedFluidFuelPatch> loadedTags) {
        FuelProfilePatch patch = readFuelProfilePatch(id, object);
        if (id.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
            if (tagId == null) {
                LOGGER.warn("Skipping invalid thruster liquid fuel tag '{}'", id);
                return;
            }
            loadedTags.add(new TaggedFluidFuelPatch(TagKey.create(Registries.FLUID, tagId), patch));
            return;
        }

        ResourceLocation fluidId = ResourceLocation.tryParse(id);
        if (fluidId == null) {
            LOGGER.warn("Skipping invalid thruster liquid fuel id '{}'", id);
            return;
        }
        mergeFuelPatch(loadedItems, fluidId, patch);
    }

    // Read the solid fuel data
    private static void readSolidFuelData(JsonObject fuelData, Map<ResourceLocation, ItemFuelProfilePatch> loadedItems,
            List<TaggedItemFuelPatch> loadedTags) {
        for (Map.Entry<String, JsonElement> entry : fuelData.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                LOGGER.warn("Skipping invalid thruster solid fuel entry '{}'", entry.getKey());
                continue;
            }
            String id = entry.getKey();
            ItemFuelProfilePatch patch = readItemFuelProfilePatch(id, entry.getValue().getAsJsonObject());
            if (id.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
                if (tagId == null) {
                    LOGGER.warn("Skipping invalid thruster solid fuel tag '{}'", id);
                    continue;
                }
                loadedTags.add(new TaggedItemFuelPatch(TagKey.create(Registries.ITEM, tagId), patch));
                continue;
            }

            ResourceLocation itemId = ResourceLocation.tryParse(id);
            if (itemId == null) {
                LOGGER.warn("Skipping invalid thruster solid fuel id '{}'", id);
                continue;
            }
            mergeItemFuelPatch(loadedItems, itemId, patch);
        }
    }

    // Read the fuel profile patch
    private static FuelProfilePatch readFuelProfilePatch(String id, JsonObject object) {
        return new FuelProfilePatch(
                object.has("enabled") ? object.get("enabled").getAsBoolean() : null,
                readPositiveDouble(object, "burn_time", id),
                readPositiveDouble(object, "propulsion", id),
                readPositiveDouble(object, "weight", id),
                readString(object, "particle"));
    }

    // Read the item fuel profile patch
    private static ItemFuelProfilePatch readItemFuelProfilePatch(String id, JsonObject object) {
        return new ItemFuelProfilePatch(
                object.has("enabled") ? object.get("enabled").getAsBoolean() : null,
                readPositiveInt(object, "burn_time", id),
                object.has("infinite_burn") ? object.get("infinite_burn").getAsBoolean() : null,
                readPositiveDouble(object, "propulsion", id),
                readString(object, "particle"));
    }

    // Finish the liquid profile
    private static @Nullable FuelProfile finishLiquidProfile(ResourceLocation id, FuelProfilePatch patch,
            boolean exactDefinition) {
        if (Boolean.FALSE.equals(patch.enabled())) {
            return null;
        }
        if (patch.burnTime() == null) {
            if (exactDefinition) {
                LOGGER.warn("Skipping thruster liquid fuel {} because its resolved profile has no valid burn_time", id);
            }
            return null;
        }
        return new FuelProfile(
                patch.burnTime(),
                patch.propulsion() != null ? patch.propulsion() : 1.0D,
                patch.weight() != null ? patch.weight() : 1.0D,
                patch.particleStyle() != null ? patch.particleStyle() : "default");
    }

    // Finish the item profile
    private static @Nullable ItemFuelProfile finishItemProfile(ResourceLocation id, ItemFuelProfilePatch patch,
            boolean exactDefinition) {
        if (Boolean.FALSE.equals(patch.enabled())) {
            return null;
        }
        boolean infiniteBurn = Boolean.TRUE.equals(patch.infiniteBurn());
        if (!infiniteBurn && patch.burnTicks() == null) {
            if (exactDefinition) {
                LOGGER.warn("Skipping thruster solid fuel {} because its resolved profile has no valid burn_time", id);
            }
            return null;
        }
        return new ItemFuelProfile(
                infiniteBurn ? 0 : patch.burnTicks(),
                infiniteBurn,
                patch.propulsion() != null ? patch.propulsion() : 1.0D,
                patch.particleStyle() != null ? patch.particleStyle() : "default",
                id.toString());
    }

    // Read the positive double
    private static @Nullable Double readPositiveDouble(JsonObject object, String key, String id) {
        if (!object.has(key)) {
            return null;
        }
        double val = object.get(key).getAsDouble();
        if (val > 0.0D) {
            return val;
        }
        LOGGER.warn("Ignoring {}.{} because it must be greater than zero", id, key);
        return null;
    }

    // Read the positive int
    private static @Nullable Integer readPositiveInt(JsonObject object, String key, String id) {
        if (!object.has(key)) {
            return null;
        }
        int val = object.get(key).getAsInt();
        if (val > 0) {
            return val;
        }
        LOGGER.warn("Ignoring {}.{} because it must be greater than zero", id, key);
        return null;
    }

    // Read the string
    private static @Nullable String readString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : null;
    }

    // Merge the fuel patch
    private static void mergeFuelPatch(Map<ResourceLocation, FuelProfilePatch> patches, ResourceLocation id,
            FuelProfilePatch patch) {
        FuelProfilePatch existing = patches.get(id);
        patches.put(id, existing == null ? patch : existing.merge(patch));
    }

    // Merge the item fuel patch
    private static void mergeItemFuelPatch(Map<ResourceLocation, ItemFuelProfilePatch> patches, ResourceLocation id,
            ItemFuelProfilePatch patch) {
        ItemFuelProfilePatch existing = patches.get(id);
        patches.put(id, existing == null ? patch : existing.merge(patch));
    }

    // Store the fluid profile resolution
    private record FluidProfileResolution(@Nullable FuelProfilePatch patch, boolean exactDefinition) {
    }
}
