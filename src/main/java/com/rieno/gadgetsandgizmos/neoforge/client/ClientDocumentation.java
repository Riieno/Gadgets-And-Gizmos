package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.ui.DocumentationModal;
import com.rieno.gadgetsandgizmos.lib.documentation.BundledDocumentationSource;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.function.Consumer;

// Read only the wiki snapshot packaged with this addon version.
public final class ClientDocumentation{
    private static final BundledDocumentationSource SOURCE = new BundledDocumentationSource(
            CreateThrusters.class, "/assets/createthrusters/documentation");
    private static boolean initialized;

    private ClientDocumentation(){}

    public static void start(){
        if(initialized) return;
        initialized = true;
        SOURCE.pages();
    }

    public static DocumentationModal modal(Consumer<URI> external){
        var labels = new DocumentationModal.Labels(Component.literal("Documentation"), Component.literal("Pages"),
                Component.literal("Reload"), Component.literal("Back"), Component.literal("Loading..."),
                Component.literal("Documentation is missing from this installation."), Component.empty(),
                Component.literal(SOURCE.version() + "  |  Esc: close  |  Backspace: back  |  Shift+Wheel: scroll wide tables"));
        return new DocumentationModal(SOURCE, external, labels).withAppearance(CTHelpModalAppearance.DOCUMENTATION);
    }

    public static BundledDocumentationSource source(){ start(); return SOURCE; }
    public static boolean available(){ return SOURCE.hasPages(); }
}
