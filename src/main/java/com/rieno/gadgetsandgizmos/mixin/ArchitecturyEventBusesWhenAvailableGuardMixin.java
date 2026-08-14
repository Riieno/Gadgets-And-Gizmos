package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.neoforged.bus.api.IEventBus;

// Guard Architectury Event Buses When Available
@Pseudo
@Mixin(targets = "dev.architectury.platform.hooks.EventBusesHooks", remap = false)
public abstract class ArchitecturyEventBusesWhenAvailableGuardMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Unique
    private static final Logger CT_LOGGER = LogUtils.getLogger();

    @Unique
    private static final AtomicBoolean CT_LOGGED = new AtomicBoolean(false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the guard when available
    @Inject(method = "whenAvailable", at = @At("HEAD"), cancellable = true)
    private static void ct$guardWhenAvailable(String modId, Consumer<IEventBus> callback, CallbackInfo ci) {
        try {
            Class<?> hooksClass = Class.forName("dev.architectury.platform.hooks.EventBusesHooks");
            Method getModEventBus = hooksClass.getMethod("getModEventBus", String.class);
            Optional<?> eventBus = (Optional<?>) getModEventBus.invoke(null, modId);
            if (eventBus.isEmpty()) {
                if (CT_LOGGED.compareAndSet(false, true)) {
                    CT_LOGGER.warn(
                            "[CT][Compat] Architectury whenAvailable('{}') called before mod is registered in " +
                            "Architectury's event-bus registry (likely OmegaConfig early-init ordering); skipping to " +
                            "prevent ISE crash. The registration may be retried by Architectury when it finishes initialising.",
                            modId);
                }
                ci.cancel();
            }
        } catch (Throwable e) {

            if (CT_LOGGED.compareAndSet(false, true)) {
                CT_LOGGER.warn(
                        "[CT][Compat] Architectury whenAvailable guard threw while checking mod '{}': {}; " +
                        "cancelling call to avoid crash.",
                        modId, e.getMessage());
            }
            ci.cancel();
        }
    }
}
