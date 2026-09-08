package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.compat.simulated.DockingConnectorBindingAccess;
import com.rieno.gadgetsandgizmos.neoforge.client.DockingConnectorRetexturedModel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

// Publish a bound connector's texture state to the baked block model
@Mixin(value = DockingConnectorBlockEntity.class, remap = false)
public abstract class DockingConnectorModelDataMixin {
    public ModelData getModelData() {
        DockingConnectorBindingAccess access = (DockingConnectorBindingAccess) (Object) this;
        return access.createthrusters$usesBoundTexture()
                ? ModelData.of(DockingConnectorRetexturedModel.BOUND_TEXTURE, true)
                : ModelData.EMPTY;
    }
}
