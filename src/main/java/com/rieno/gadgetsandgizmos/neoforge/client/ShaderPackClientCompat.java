package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import net.neoforged.fml.ModList;

// Report whether an optional Iris or Oculus shader pack is rendering
public final class ShaderPackClientCompat {
    // Cache the optional shader API lookup
    private static final BooleanSupplier ACTIVE = findActiveProbe();
    private static final BooleanSupplier WORLD_TARGET = findWorldTargetBinder();

    // Initialize the shader pack adapter
    private ShaderPackClientCompat() {
    }

    // Check whether a shader pack is active
    public static boolean isActive() {
        return ACTIVE.getAsBoolean();
    }

    // Check whether the shader pipeline exposes its active world framebuffer
    public static boolean canBindWorldTarget() {
        return WORLD_TARGET != null;
    }

    // Bind the shader pipeline's active world framebuffer
    public static boolean bindWorldTarget() {
        return WORLD_TARGET != null && WORLD_TARGET.getAsBoolean();
    }

    // Find the supported optional shader API
    private static BooleanSupplier findActiveProbe() {
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) {
            return () -> false;
        }

        for (String className : new String[] {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi"
        }) {
            try {
                Class<?> apiClass = Class.forName(className);
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Method isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
                return () -> {
                    try {
                        return Boolean.TRUE.equals(isShaderPackInUse.invoke(api));
                    } catch (ReflectiveOperationException err) {
                        return true;
                    }
                };
            } catch (ReflectiveOperationException | LinkageError err) {
                // Fall through to the next optional API variant
            }
        }

        // Use the compatible particle path when the optional API is unavailable
        return () -> true;
    }

    // Find the framebuffer that Iris uses after translucent world rendering
    private static BooleanSupplier findWorldTargetBinder() {
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) return null;
        for (String packageName : new String[] {"net.irisshaders.iris", "net.coderbot.iris"}) {
            try {
                Class<?> irisClass = Class.forName(packageName + ".Iris");
                Class<?> pipelineClass = Class.forName(packageName + ".pipeline.IrisRenderingPipeline");
                Method manager = irisClass.getMethod("getPipelineManager");
                Method getPipeline = manager.getReturnType().getMethod("getPipeline");
                Method bindDefault = pipelineClass.getMethod("bindDefault");
                return () -> {
                    try {
                        Object pipelineManager = manager.invoke(null);
                        Object current = getPipeline.invoke(pipelineManager);
                        if (!(current instanceof Optional<?> optional) || optional.isEmpty()
                                || !pipelineClass.isInstance(optional.get())) return false;
                        bindDefault.invoke(optional.get());
                        return true;
                    } catch (ReflectiveOperationException | LinkageError err) {
                        return false;
                    }
                };
            } catch (ReflectiveOperationException | LinkageError err) {
                // Try the next supported shader pipeline
            }
        }
        return null;
    }
}
