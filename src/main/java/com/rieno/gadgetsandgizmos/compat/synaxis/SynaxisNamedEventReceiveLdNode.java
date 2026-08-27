package com.rieno.gadgetsandgizmos.compat.synaxis;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.verr1.synaxis.foundation.cimulink.game.component.BusConfig;
import com.verr1.synaxis.foundation.cimulink.game.component.BusPortSpec;
import com.verr1.synaxis.foundation.cimulink.game.component.BusScope;
import com.verr1.synaxis.foundation.cimulink.game.component.BusSignalMode;
import net.minecraft.network.chat.Component;

import java.util.List;

// Receive a numeric shared Named Event as a Synaxis data output and one-tick pulse
public final class SynaxisNamedEventReceiveLdNode extends SynaxisNamedEventLdNode {
    private static final BusConfig BUS_CONFIG = new BusConfig(BusScope.SELF_BODY, List.of(),
            List.of(new BusPortSpec("named_event", "value", BusSignalMode.REAL),
                    new BusPortSpec("named_event", "pulse", BusSignalMode.BOOLEAN)));

    @Override
    public Component getDisplayName() {
        return Component.literal("On Named Event");
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        defineOptions(context, BUS_CONFIG);
    }
}
