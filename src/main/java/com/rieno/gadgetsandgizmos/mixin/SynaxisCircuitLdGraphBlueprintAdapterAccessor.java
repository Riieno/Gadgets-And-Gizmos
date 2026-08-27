package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraphBlueprintAdapter;
import com.verr1.synaxis.foundation.cimulink.core.circuit.NodeDef;
import com.verr1.synaxis.foundation.cimulink.game.circuit.GraphLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

// Invoke Synaxis's private stock node factory when a bridge node does not need custom handling
@Pseudo
@Mixin(targets = "com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraphBlueprintAdapter", remap = false)
public interface SynaxisCircuitLdGraphBlueprintAdapterAccessor {
    @Invoker("createComponentNode")
    static NodeModel createthrusters$createComponentNode(CustomGraphModelImpl graph,
                                                          GraphLayout layout, NodeDef node, int[] grid) {
        throw new AssertionError();
    }
}
