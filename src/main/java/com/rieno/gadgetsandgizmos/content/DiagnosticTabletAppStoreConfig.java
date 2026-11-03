package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPrice;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppIconsPayload;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;
import org.slf4j.Logger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/
public final class DiagnosticTabletAppStoreConfig{

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "CT_tablet_apps.json";
    private static final String DEFAULT_RESOURCE = "/createthrusters/default-tablet-apps.json";


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletAppPrice FALLBACK = new TabletAppPrice(ResourceLocation.withDefaultNamespace("emerald"), 8);
    private static volatile Snapshot current = new Snapshot(FALLBACK, Map.of(), Map.of());

    public static final ContextAwareReloadListener RELOAD_LISTENER = new ContextAwareReloadListener(){
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager, ProfilerFiller prepProfiler, ProfilerFiller reloadProfiler, Executor backgroundExec, Executor gameExec){
            return CompletableFuture.supplyAsync(()->Unit.INSTANCE, backgroundExec).thenCompose(barrier::wait).thenRunAsync(DiagnosticTabletAppStoreConfig::reloadNow, gameExec);
        }
    };

    // Initialize
    private DiagnosticTabletAppStoreConfig(){}

    // Get the App Price
    public static TabletAppPrice price(TabletAppDefinition app){
        if(app == null || app.builtIn()) return TabletAppPrice.FREE;
        Snapshot snapshot = current;
<<<<<<< Updated upstream
        return snapshot.apps().getOrDefault(app.id(), snapshot.fallback());
=======
        int paidDefault = switch(app.id().toString()){
            case "createthrusters:media" -> 0;
            case "createthrusters:digisable" -> 10;
            case "createthrusters:manifest" -> 8;
            case "createthrusters:blockmates" -> 7;
            default -> -1;
        };
        TabletAppPrice fallback = paidDefault < 0 ? snapshot.fallback()
                : new TabletAppPrice(ResourceLocation.withDefaultNamespace("emerald"), paidDefault);
        return snapshot.apps().getOrDefault(app.id(), fallback);
>>>>>>> Stashed changes
    }

    // Get Config Path
    public static Path path(){
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    // Publish the same icons to handheld, placed and store views on each client
    public static void syncIcons(ServerPlayer player){
        PacketDistributor.sendToPlayer(player, new DiagnosticTabletAppIconsPayload(current.icons()));
    }

    private static void reloadNow(){
        JsonObject root = readRoot();
        if(root == null){
            current = new Snapshot(FALLBACK, Map.of(), Map.of());
            return;
        }

        DiagnosticTabletApps.register();
        JsonObject defaults = readDefaults();
        TabletAppPrice fallback = readPrice(root.getAsJsonObject("defaults"), FALLBACK, "defaults");
        Map<ResourceLocation, TabletAppPrice> prices = new LinkedHashMap<>();
        Map<ResourceLocation, TabletAppIconStyle> icons = new LinkedHashMap<>();
        JsonObject apps = root.getAsJsonObject("apps");
        if(apps != null){
            for(Map.Entry<String, JsonElement> entry : apps.entrySet()){
                ResourceLocation appId = ResourceLocation.tryParse(entry.getKey());
                if (appId == null || !entry.getValue().isJsonObject()){
                    LOGGER.warn("[CT][TABLET] - Ingoring Invalid tablet app price entry {}", entry.getKey());
                    continue;
                }
                JsonObject app = entry.getValue().getAsJsonObject();
                if(app.has("item") || app.has("count")) prices.put(appId, readPrice(app, fallback, entry.getKey()));
            }
        }
        for(TabletAppDefinition app : TabletAppRegistry.apps()){
            JsonObject bundled = defaults == null ? null : defaults.getAsJsonObject("apps");
            JsonObject entry = bundled == null || !bundled.has(app.id().toString()) ? null : bundled.getAsJsonObject(app.id().toString());
            TabletAppIconStyle style = readIcon(entry, new TabletAppIconStyle(app.accentColor(),
                    ResourceLocation.withDefaultNamespace("paper"), true), app.id().toString());
            JsonElement configured = apps == null ? null : apps.get(app.id().toString());
            icons.put(app.id(), readIcon(configured != null && configured.isJsonObject() ? configured.getAsJsonObject() : null,
                    style, app.id().toString()));
        }
        current = new Snapshot(fallback, Map.copyOf(prices), Map.copyOf(icons));
        var server = ServerLifecycleHooks.getCurrentServer();
        if(server != null) for(ServerPlayer player : server.getPlayerList().getPlayers()) syncIcons(player);
        LOGGER.info("[CT][TABLET] - Loaded {} tablet app price override(s) from {}", prices.size(), path());
    }

    private static TabletAppIconStyle readIcon(JsonObject entry, TabletAppIconStyle fallback, String label){
        if(entry == null || !entry.has("icon")) return fallback;
        try{
            TabletAppIconStyle res = TabletAppIconStyle.fromJson(entry.getAsJsonObject("icon"), fallback);
            if(BuiltInRegistries.ITEM.getOptional(res.item()).orElse(Items.AIR) == Items.AIR) throw new IllegalArgumentException("Unknown icon item");
            return res;
        }catch(RuntimeException error){
            LOGGER.warn("[CT][TABLET] - Invalid tablet app icon for {}; using fallback", label, error);
            return fallback;
        }
    }

    // Get the App Price and use fallback if invalid/null
    private static TabletAppPrice readPrice(JsonObject object, TabletAppPrice fallback, String label){
        if(object == null) return fallback;
        try{
            ResourceLocation itemId = object.has("item") ? ResourceLocation.tryParse(object.get("item").getAsString()) : fallback.itemId();
            int count = object.has("count") ? object.get("count").getAsInt() : fallback.count();
            if(itemId == null || count < 0 || BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR) == Items.AIR){
                throw new IllegalArgumentException("Invalid Item or Count");
            }
            return new TabletAppPrice(itemId, count);
        }catch(RuntimeException error){
            LOGGER.warn("[CT][TABLET] - Inavlid tablet app price for {}; using fallback", label, error);
            return fallback;
        }
    }

    private static JsonObject readRoot(){
        Path path = path();
        try{
            if(Files.notExists(path)){
                Files.createDirectories(path.getParent());
                try(InputStream src = DiagnosticTabletAppStoreConfig.class.getResourceAsStream(DEFAULT_RESOURCE)){
                    if(src == null) throw new IllegalStateException("[CT][TABLET] - Missing Bundled App Defaults");
                    Files.copy(src, path);
                }
            }
            JsonObject root;
            try(Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)){
                JsonElement parsed = JsonParser.parseReader(reader);
                if(!parsed.isJsonObject()) throw new IllegalArgumentException("[CT][TABLET] - Root must be a JSON Object");
                root = parsed.getAsJsonObject();
            }
            if(addMissingIcons(root)){
                Path updated = Files.createTempFile(path.getParent(), "CT_tablet_apps-", ".json");
                try{
                    try(var writer = Files.newBufferedWriter(updated, StandardCharsets.UTF_8)){
                        new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
                    }
                    Files.move(updated, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }catch(Exception error){
                    LOGGER.warn("[CT][TABLET] - Could not save app icon fields to {}; retaining loaded settings", path, error);
                }finally{ Files.deleteIfExists(updated); }
            }
            return root;
        }catch(Exception error){
            LOGGER.error("[CT][TABLET] - Could not load {}; using default values", path, error);
            try(InputStream src = DiagnosticTabletAppStoreConfig.class.getResourceAsStream(DEFAULT_RESOURCE)){
                if(src == null) return null;
                try (Reader reader = new InputStreamReader(src, StandardCharsets.UTF_8)){
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }catch(Exception fallbackError){
                LOGGER.error("[CT][TABLET] - Could not load app default values", fallbackError);
                return null;
            }
        }
    }

    // Add editable icon fields to older configs while preserving prices and existing overrides
    private static boolean addMissingIcons(JsonObject root){
        JsonObject defaults = readDefaults();
        if(defaults == null) return false;
        JsonObject apps = root.getAsJsonObject("apps");
        if(apps == null){ apps = new JsonObject(); root.add("apps", apps); }
        boolean changed = false;
        for(var entry : defaults.getAsJsonObject("apps").entrySet()){
            JsonElement val = apps.get(entry.getKey());
            if(val != null && !val.isJsonObject()) continue;
            JsonObject app = val == null ? new JsonObject() : val.getAsJsonObject();
            if(app.has("icon")) continue;
            app.add("icon", entry.getValue().getAsJsonObject().get("icon").deepCopy());
            apps.add(entry.getKey(), app);
            changed = true;
        }
        return changed;
    }

    private static JsonObject readDefaults(){
        try(InputStream src = DiagnosticTabletAppStoreConfig.class.getResourceAsStream(DEFAULT_RESOURCE)){
            if(src == null) return null;
            try(Reader reader = new InputStreamReader(src, StandardCharsets.UTF_8)){
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }catch(Exception error){
            LOGGER.error("[CT][TABLET] - Could not load app icon defaults", error);
            return null;
        }
    }

    private record Snapshot(TabletAppPrice fallback, Map<ResourceLocation, TabletAppPrice> apps,
                            Map<ResourceLocation, TabletAppIconStyle> icons){}

}
