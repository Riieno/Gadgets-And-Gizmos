package com.rieno.gadgetsandgizmos.compat.letitwalk;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.minecraft.core.Direction;

// Keep optional native walker classes behind the loaded-mod boundary
public final class LetItWalkSupport{
    // Retain the loaded-mod decision before exposing optional controls
    private static boolean loaded;
    // Prevent support facade instances
    private LetItWalkSupport(){}
    // Register the native bridge only when Let It Walk is installed
    public static void register(){
        loaded = ModList.get().isLoaded("let_it_walk");
        if(loaded) LetItWalkCompatibility.register();
    }
    // Require a core and pilot controls on the host's sublevel
    public static boolean isWalker(BlockEntity host){ return loaded && LetItWalkCompatibility.isWalker(host); }
    // Expose native graph nodes whenever the host's sublevel contains a walker core
    public static boolean hasCore(BlockEntity host){ return loaded && LetItWalkCompatibility.findCore(host) != null; }
    // Resolve the native walker orientation without exposing its block entity to consumers
    public static Direction forward(BlockEntity host){ return loaded ? LetItWalkCompatibility.forward(host) : Direction.NORTH; }
}
