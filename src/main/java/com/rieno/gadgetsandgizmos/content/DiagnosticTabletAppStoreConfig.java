package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPrice;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;
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
    private static final String DEFAULT_RESOURCE = "/createthusters/default-tablet-apps.json";


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletAppPrice FALLBACK = new TabletAppPrice(ResourceLocation.withDefaultNamespace("emerald"), 8);
    private static volatile Snapshot current = new Snapshot(FALLBACK, Map.of());

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
        return snapshot.apps().getOrDefault(app.id(), snapshot.fallback());
    }

    // Get Config Path
    public static Path path(){
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static void reloadNow(){
        JsonObject root = readRoot();
        if(root == null){
            current = new Snapshot(FALLBACK, Map.of());
            return;
        }

        TabletAppPrice fallback = readPrice(root.getAsJsonObject("defaults"), FALLBACK, "defaults");
        Map<ResourceLocation, TabletAppPrice> prices = new LinkedHashMap<>();
        JsonObject apps = root.getAsJsonObject("apps");
        if(apps != null){
            for(Map.Entry<String, JsonElement> entry : apps.entrySet()){
                ResourceLocation appId = ResourceLocation.tryParse(entry.getKey());
                if (appId == null || !entry.getValue().isJsonObject()){
                    LOGGER.warn("[CT][TABLET] - Ingoring Invalid tablet app price entry {}", entry.getKey());
                    continue;
                }
                prices.put(appId, readPrice(entry.getValue().getAsJsonObject(), fallback, entry.getKey()));
            }
        }
        current = new Snapshot(fallback, Map.copyOf(prices));
        LOGGER.info("[CT][TABLET] - Loaded {} tablet app price override(s) from {}", prices.size(), path());
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
        try {
            if(Files.notExists(path)){
                Files.createDirectories(path.getParent());
                try(InputStream src = DiagnosticTabletAppStoreConfig.class.getResourceAsStream(DEFAULT_RESOURCE)){
                    if(src == null) throw new IllegalStateException("[CT][TABLET] - Missing Bundled App Defaults");
                    Files.copy(src, path);
                }
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)){
                JsonElement parsed = JsonParser.parseReader(reader);
                if(!parsed.isJsonObject()) throw new IllegalArgumentException("[CT][TABLET] - Root must be a JSON Object");
                return parsed.getAsJsonObject();
            }
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

    private record Snapshot(TabletAppPrice fallback, Map<ResourceLocation, TabletAppPrice> apps){}

}
