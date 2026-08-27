package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
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

import java.util.ArrayList;
import java.util.List;

// Add the shared Named Event pair before Synaxis's graph model can cache its palette
@Pseudo
@Mixin(targets = "com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraph", remap = false)
public abstract class SynaxisCircuitLdGraphMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow @Final @Mutable
    private static List<Class<? extends Node>> SUPPORT_NODES;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0)
    private static void createthrusters$registerNamedEventNodes(CallbackInfo callback) {
        List<Class<? extends Node>> nodes = new ArrayList<>(SUPPORT_NODES);
        boolean changed = nodes.add(SynaxisNamedEventSendLdNode.class);
        changed |= nodes.add(SynaxisNamedEventReceiveLdNode.class);
        if (changed) {
            SUPPORT_NODES = List.copyOf(nodes);
            LOGGER.info("[G&G][Compat] Registered Synaxis Send/On Named Event circuit nodes");
        }
    }
}
