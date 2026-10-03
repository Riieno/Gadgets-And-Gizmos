package com.rieno.gadgetsandgizmos.config;

import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveApi;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

// Configure paid tablet app limits on the server that owns their world state
public final class TabletAppsServerConfig{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue STORE = BUILDER.define("digisable.allowStorage", true);
    public static final ModConfigSpec.BooleanValue LOCATE = BUILDER.define("digisable.allowLocate", true);
    public static final ModConfigSpec.BooleanValue TELEPORT = BUILDER.define("digisable.allowTeleport", true);
    public static final ModConfigSpec.BooleanValue DELETE = BUILDER.define("digisable.allowDelete", true);
    public static final ModConfigSpec.IntValue ARCHIVES = BUILDER.defineInRange("digisable.archivesPerPlayer", 16, 1, 128);
    public static final ModConfigSpec.IntValue BODIES = BUILDER.defineInRange("digisable.bodiesPerArchive", 32, 1, 128);
    public static final ModConfigSpec.IntValue BLOCKS = BUILDER.defineInRange("digisable.blocksPerArchive", 32768, 1, 262144);
    public static final ModConfigSpec.IntValue STORE_RANGE = BUILDER.defineInRange("digisable.storeRange", 64, 1, 256);
    public static final ModConfigSpec.IntValue LOCATE_RANGE = BUILDER.defineInRange("digisable.locateRange", 2048, 1, 30000000);
    public static final ModConfigSpec.IntValue COOLDOWN = BUILDER.defineInRange("digisable.operationCooldownTicks", 100, 20, 12000);
    public static final ModConfigSpec.IntValue TRANSFER = BUILDER.defineInRange("manifest.itemsPerSecond", 64, 1, 1024);
    public static final ModConfigSpec.IntValue REQUEST = BUILDER.defineInRange("blockmates.maximumRequestItems", 4096, 1, 1000000);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private TabletAppsServerConfig(){}

    public static void register(ModContainer container){
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "createthrusters-tablet-apps-server.toml");
    }

    public static SubLevelArchiveApi.Limits archiveLimits(){
        return new SubLevelArchiveApi.Limits(ARCHIVES.get(), BODIES.get(), BLOCKS.get(), STORE_RANGE.get(), LOCATE_RANGE.get());
    }
}
