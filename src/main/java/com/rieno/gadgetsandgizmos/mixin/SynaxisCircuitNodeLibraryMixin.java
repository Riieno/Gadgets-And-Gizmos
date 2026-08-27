package com.rieno.gadgetsandgizmos.mixin;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventReceiveLdNode;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventSendLdNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Synaxis deliberately exposes only the entries in its private palette catalogue. Adding a node
 * to {@code CircuitLdGraph.SUPPORT_NODES} alone therefore permits deserialisation but does not make
 * it discoverable in the Add Node window. Append our two supported nodes to that exact catalogue.
 */
@Pseudo
@Mixin(targets = "com.verr1.synaxis.content.blocks.circuit.ldgraph.ui.CircuitNodeLibrary", remap = false)
public abstract class SynaxisCircuitNodeLibraryMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTRY =
            "com.verr1.synaxis.content.blocks.circuit.ldgraph.ui.CircuitNodeLibrary$Entry";
    private static final String CATEGORY =
            "com.verr1.synaxis.content.blocks.circuit.ldgraph.ui.CircuitNodeLibrary$Category";

    @Shadow @Final @Mutable
    private static List<?> CATEGORIES;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0)
    private static void createthrusters$addNamedEventPaletteEntries(CallbackInfo callback) {
        try {
            List<Object> entries = List.of(
                    createEntry("named_event_send", "Send Named Event", SynaxisNamedEventSendLdNode.class),
                    createEntry("named_event_receive", "On Named Event", SynaxisNamedEventReceiveLdNode.class));
            Object category = createCategory("gadgets_and_gizmos", "Gadgets & Gizmos", entries);

            List<Object> categories = new ArrayList<>(CATEGORIES);
            categories.add(category);
            CATEGORIES = List.copyOf(categories);
            LOGGER.info("[G&G][Compat] Added Synaxis Send/On Named Event entries to the circuit Add Node palette");
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("[G&G][Compat] Could not add Synaxis Named Event entries to the circuit Add Node palette", exception);
        }
    }

    private static Object createEntry(String key, String searchName, Class<?> nodeType)
            throws ReflectiveOperationException {
        Class<?> entryClass = Class.forName(ENTRY);
        Constructor<?> constructor = entryClass.getDeclaredConstructor(
                String.class, String.class, Class.class, IGuiTexture.class);
        constructor.setAccessible(true);
        return constructor.newInstance(key, searchName, nodeType, Icons.NODE);
    }

    private static Object createCategory(String key, String searchName, List<Object> entries)
            throws ReflectiveOperationException {
        Class<?> categoryClass = Class.forName(CATEGORY);
        Constructor<?> constructor = categoryClass.getDeclaredConstructor(String.class, String.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(key, searchName, entries);
    }
}
