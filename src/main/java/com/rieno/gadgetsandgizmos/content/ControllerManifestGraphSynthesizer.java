package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

// Build ACC graphs from saved controller data
public final class ControllerManifestGraphSynthesizer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller manifest graph synthesizer
    private ControllerManifestGraphSynthesizer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Synthesize a controller graph
    public static AdvancedGraphDocument synthesize(CompoundTag controllerData, HolderLookup.Provider provider) {
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        if (AdvancedContraptionControllerBlockEntity.importControllerDataIntoGraph(graph, controllerData, provider)) {
            graph.setRevision(1);
        }
        return graph;
    }
}
