package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Guard Aero Cam Sync Game Renderer Pick
@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class AeroCamSyncGameRendererPickGuardMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Unique
    private static final Logger CT_LOGGER = LogUtils.getLogger();
    @Unique
    private static final AtomicBoolean CT_LOGGED_AERO_CAM_SYNC_EARLY_PICK = new AtomicBoolean(false);
    @Unique
    private static final AtomicInteger CT_AERO_CAM_PRESENCE_STATE = new AtomicInteger(0);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the guard aero cam sync early pick
    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void ct$guardAeroCamSyncEarlyPick(float partialTick, CallbackInfo ci) {
        if (!ct$isAeroCamSyncPresent()) {
            return;
        }
        if (!ct$wouldAeroCamConfigReadCrash()) {
            return;
        }

        ci.cancel();
        if (CT_LOGGED_AERO_CAM_SYNC_EARLY_PICK.compareAndSet(false, true)) {
            CT_LOGGER.warn("[CT][Compat] Suppressed aero_cam_sync GameRenderer pick before config load to prevent ConfigValue#get IllegalStateException");
        }
    }

    // Check if the aero cam sync is present
    @Unique
    private static boolean ct$isAeroCamSyncPresent() {
        int state = CT_AERO_CAM_PRESENCE_STATE.get();
        if (state == 0) {
            try {
                Class.forName("com.playsi.aero_cam_sync.client.Config", false,
                        Thread.currentThread().getContextClassLoader());
                CT_AERO_CAM_PRESENCE_STATE.compareAndSet(0, 2);
            } catch (ClassNotFoundException ignored) {
                CT_AERO_CAM_PRESENCE_STATE.compareAndSet(0, 1);
            }
            state = CT_AERO_CAM_PRESENCE_STATE.get();
        }
        return state == 2;
    }

    // Check if reading the AeroCam config would crash
    @Unique
    private static boolean ct$wouldAeroCamConfigReadCrash() {
        try {
            Class<?> configClass = Class.forName("com.playsi.aero_cam_sync.client.Config", false,
                    Thread.currentThread().getContextClassLoader());
            Field modEnabledField = configClass.getDeclaredField("MOD_ENABLED");
            modEnabledField.setAccessible(true);
            Object modEnabled = modEnabledField.get(null);
            if (modEnabled == null) {
                return false;
            }

            Method getMethod = modEnabled.getClass().getMethod("get");
            getMethod.invoke(modEnabled);
            return false;
        } catch (InvocationTargetException ex) {
            return ex.getCause() instanceof IllegalStateException;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
