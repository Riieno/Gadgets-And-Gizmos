package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Guard Architectury Platform Get Mods
@Pseudo
@Mixin(targets = "dev.architectury.platform.forge.PlatformImpl", remap = false)
public abstract class ArchitecturyPlatformGetModsGuardMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Unique
    private static final Logger CT_LOGGER = LogUtils.getLogger();
    @Unique
    private static final AtomicBoolean CT_LOGGED_ARCHITECTURY_GUARD = new AtomicBoolean(false);
    @Unique
    private static final AtomicBoolean CT_LOGGED_ARCHITECTURY_SKIP = new AtomicBoolean(false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the guard architectury get mods
    @Inject(method = "getMods", at = @At("HEAD"), cancellable = true)
    private static void ct$guardArchitecturyGetMods(CallbackInfoReturnable<Collection<?>> cir) {
        if (!ct$isDynamicSurroundingsResourceReload()) {
            return;
        }

        cir.setReturnValue(ct$collectArchitecturyModsSafely());
        if (CT_LOGGED_ARCHITECTURY_GUARD.compareAndSet(false, true)) {
            CT_LOGGER.warn("[CT][Compat] Guarded Architectury getMods() during Dynamic Surroundings reload; unresolved containers will be skipped");
        }
    }

    // Collect the architectury mods safely
    @Unique
    private static Collection<?> ct$collectArchitecturyModsSafely() {
        Method getModMethod = ct$findArchitecturyGetModMethod();
        if (getModMethod == null) {
            return Collections.emptyList();
        }

        List<Object> resolvedMods = new ArrayList<>();
        for (IModInfo modInfo : ModList.get().getMods()) {
            try {
                Object mod = getModMethod.invoke(null, modInfo.getModId());
                if (mod != null) {
                    resolvedMods.add(mod);
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                if (CT_LOGGED_ARCHITECTURY_SKIP.compareAndSet(false, true)) {
                    CT_LOGGER.warn("[CT][Compat] Skipping unresolved Architectury mod container while rebuilding mod list");
                }
            }
        }

        return resolvedMods;
    }

    // Find the architectury get mod method
    @Unique
    private static Method ct$findArchitecturyGetModMethod() {
        try {
            Class<?> platformImplClass = Class.forName("dev.architectury.platform.forge.PlatformImpl");
            Method method = platformImplClass.getDeclaredMethod("getMod", String.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Check if this is dynamic surroundings resource reload
    @Unique
    private static boolean ct$isDynamicSurroundingsResourceReload() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("org.orecruncher.dsurround.lib.resources.")) {
                return true;
            }
        }
        return false;
    }
}
