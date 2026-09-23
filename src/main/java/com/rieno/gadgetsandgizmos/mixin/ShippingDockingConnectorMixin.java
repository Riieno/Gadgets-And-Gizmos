package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.compat.simulated.DockingConnectorBindingAccess;
import com.rieno.gadgetsandgizmos.content.CTTooltipHelper;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Add binding access to Simulated docking connectors
@Mixin(value = DockingConnectorBlockEntity.class, remap = false)
public abstract class ShippingDockingConnectorMixin implements DockingConnectorBindingAccess,
        IHaveGoggleInformation {
    private static final String BINDING_TAG = "CreateThrustersBinding";
    private static final String SHIP_DOCK_ID_TAG = "ShipDockId";
    private static final String SHIP_DOCK_NAME_TAG = "ShipDockName";
    private static final String SHIP_DOCK_CONNECTOR_TAG = "ShipDockConnector";
    private static final String SCM_BOUND_TAG = "ScmBound";
    private static final String SCM_NAME_TAG = "ScmName";
    private static final String SCM_CONNECTOR_TAG = "ScmConnector";

    @Unique private UUID createthrusters$shipDockId;
    @Unique private String createthrusters$shipDockName = "";
    @Unique private int createthrusters$shipDockConnectorIndex = -1;
    @Unique private boolean createthrusters$shipControlModuleBound;
    @Unique private String createthrusters$shipControlModuleName = "";
    @Unique private int createthrusters$shipControlModuleConnectorIndex = -1;

    @Override
    public void createthrusters$setShipDockBinding(UUID dockId, String dockName, int connectorIndex) {
        if (dockId == null) return;
        String nextName = dockName == null ? "Ship Dock" : dockName;
        if (Objects.equals(createthrusters$shipDockId, dockId)
                && createthrusters$shipDockName.equals(nextName)
                && createthrusters$shipDockConnectorIndex == connectorIndex) return;
        createthrusters$shipDockId = dockId;
        createthrusters$shipDockName = nextName;
        createthrusters$shipDockConnectorIndex = connectorIndex;
        createthrusters$bindingChanged();
    }

    @Override
    public void createthrusters$clearShipDockBinding(UUID dockId) {
        if (dockId == null || !dockId.equals(createthrusters$shipDockId)) return;
        createthrusters$shipDockId = null;
        createthrusters$shipDockName = "";
        createthrusters$shipDockConnectorIndex = -1;
        createthrusters$bindingChanged();
    }

    @Override
    public boolean createthrusters$hasShipDockBinding() {
        return createthrusters$shipDockId != null && createthrusters$shipDockConnectorIndex >= 0;
    }

    @Override
    public String createthrusters$getShipDockName() {
        return createthrusters$shipDockName;
    }

    @Override
    public int createthrusters$getShipDockConnectorIndex() {
        return createthrusters$shipDockConnectorIndex;
    }

    @Override
    public void createthrusters$setShipControlModuleBinding(String shipName, int connectorIndex) {
        String nextName = shipName == null ? "Ship Control Module" : shipName;
        if (createthrusters$shipControlModuleBound
                && createthrusters$shipControlModuleName.equals(nextName)
                && createthrusters$shipControlModuleConnectorIndex == connectorIndex) return;
        createthrusters$shipControlModuleBound = true;
        createthrusters$shipControlModuleName = nextName;
        createthrusters$shipControlModuleConnectorIndex = connectorIndex;
        createthrusters$bindingChanged();
    }

    @Override
    public void createthrusters$clearShipControlModuleBinding() {
        if (!createthrusters$shipControlModuleBound) return;
        createthrusters$shipControlModuleBound = false;
        createthrusters$shipControlModuleName = "";
        createthrusters$shipControlModuleConnectorIndex = -1;
        createthrusters$bindingChanged();
    }

    @Override
    public boolean createthrusters$hasShipControlModuleBinding() {
        return createthrusters$shipControlModuleBound
                && createthrusters$shipControlModuleConnectorIndex >= 0;
    }

    @Override
    public boolean createthrusters$usesBoundTexture() {
        return createthrusters$hasShipDockBinding() || createthrusters$hasShipControlModuleBinding();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!createthrusters$hasShipDockBinding()) return false;
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.simulated.docking_connector")));
        tooltip.add(CTTooltipHelper.line(
                Component.translatable("createthrusters.goggle.docking_connector.ship_dock"),
                CTTooltipHelper.value(Component.translatable(
                        "createthrusters.goggle.docking_connector.ship_dock_value",
                        createthrusters$shipDockName,
                        createthrusters$shipDockConnectorIndex + 1).getString(), ChatFormatting.AQUA)));
        return true;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createthrusters$writeBinding(
            CompoundTag tag,
            HolderLookup.Provider provider,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        CompoundTag binding = new CompoundTag();
        if (createthrusters$shipDockId != null) {
            binding.putUUID(SHIP_DOCK_ID_TAG, createthrusters$shipDockId);
            binding.putString(SHIP_DOCK_NAME_TAG, createthrusters$shipDockName);
            binding.putInt(SHIP_DOCK_CONNECTOR_TAG, createthrusters$shipDockConnectorIndex);
        }
        binding.putBoolean(SCM_BOUND_TAG, createthrusters$shipControlModuleBound);
        binding.putString(SCM_NAME_TAG, createthrusters$shipControlModuleName);
        binding.putInt(SCM_CONNECTOR_TAG, createthrusters$shipControlModuleConnectorIndex);
        tag.put(BINDING_TAG, binding);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createthrusters$readBinding(
            CompoundTag tag,
            HolderLookup.Provider provider,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        boolean usedBoundTexture = createthrusters$usesBoundTexture();
        CompoundTag binding = tag.getCompound(BINDING_TAG);
        createthrusters$shipDockId = binding.hasUUID(SHIP_DOCK_ID_TAG)
                ? binding.getUUID(SHIP_DOCK_ID_TAG) : null;
        createthrusters$shipDockName = binding.getString(SHIP_DOCK_NAME_TAG);
        createthrusters$shipDockConnectorIndex = binding.getInt(SHIP_DOCK_CONNECTOR_TAG);
        createthrusters$shipControlModuleBound = binding.getBoolean(SCM_BOUND_TAG);
        createthrusters$shipControlModuleName = binding.getString(SCM_NAME_TAG);
        createthrusters$shipControlModuleConnectorIndex = binding.getInt(SCM_CONNECTOR_TAG);
        if (usedBoundTexture != createthrusters$usesBoundTexture()) {
            createthrusters$refreshClientModel();
        }
    }

    @Unique
    private void createthrusters$bindingChanged() {
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        if (self.getLevel() != null && !self.getLevel().isClientSide) {
            ((SmartBlockEntity) self).sendData();
        }
    }

    // Rebuild the static base model after client binding data changes
    @Unique
    private void createthrusters$refreshClientModel() {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null && self.getLevel().isClientSide) {
            self.requestModelDataUpdate();
            self.getLevel().sendBlockUpdated(
                    self.getBlockPos(), self.getBlockState(), self.getBlockState(), 3);
        }
    }
}
