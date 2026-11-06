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
        var labels = new DocumentationModal.Labels(Component.translatable("createthrusters.advanced_controller.documentation.title"), Component.translatable("createthrusters.text.pages"),
                Component.translatable("createthrusters.text.reload"), Component.translatable("createthrusters.pose_screen.back"), Component.translatable("createthrusters.text.loading"),
                Component.translatable("createthrusters.text.documentation_is_missing_from_this_installation"), Component.empty(),
                Component.translatable("createthrusters.text.1_s_esc_close_backspace_back_shift_wheel_scroll_wide_tables", SOURCE.version()));
        return new DocumentationModal(SOURCE, external, labels).withAppearance(CTHelpModalAppearance.DOCUMENTATION);
    }

    public static BundledDocumentationSource source(){ start(); return SOURCE; }
    public static boolean available(){ return SOURCE.hasPages(); }
}
