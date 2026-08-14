package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// Load the claw depth highlight shaders
public final class ClawDepthHighlightShaders {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LoggerFactory.getLogger("CreateThrusters");
    private static final ResourceLocation DEPTH_HIGHLIGHT_SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID,
            "depth_radius_highlight"
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared depth highlight shader
    private static ShaderInstance depthHighlightShader;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw depth highlight shaders
    private ClawDepthHighlightShaders() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the register shaders event
    public static void onRegisterShaders(RegisterShadersEvent evt) throws IOException {
        ResourceProvider resourceProvider = evt.getResourceProvider();

        try {
            ShaderInstance shaderInstance = new ShaderInstance(resourceProvider, DEPTH_HIGHLIGHT_SHADER_ID, DefaultVertexFormat.POSITION);
            evt.registerShader(shaderInstance, shader -> {
                depthHighlightShader = shader;
                LOGGER.info("Successfully loaded depth_radius_highlight shader: {}", DEPTH_HIGHLIGHT_SHADER_ID);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to load depth_radius_highlight shader: {}", DEPTH_HIGHLIGHT_SHADER_ID, e);
            throw e;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the depth highlight shader
    public static ShaderInstance getDepthHighlightShader() {
        return depthHighlightShader;
    }
}
