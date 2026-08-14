package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

// Send Thruster Fuel Tank
public record ThrusterFuelTankPayload(BlockPos pos) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int BUCKET_VOLUME = 1000;

    public static final Type<ThrusterFuelTankPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "thruster_fuel_tank"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrusterFuelTankPayload> STREAM_CODEC = StreamCodec.of(
            ThrusterFuelTankPayload::encode,
            ThrusterFuelTankPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the thruster fuel tank
    public static void handle(ThrusterFuelTankPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Level level = player.level();
            if (!(level.getBlockEntity(payload.pos()) instanceof ThrusterBlockEntity thruster)) {
                return;
            }
            if (!(player.containerMenu instanceof ThrusterMenu menu) || menu.contentHolder != thruster) {
                return;
            }

            if (tryEmptyCarriedContainer(player, menu, thruster)
                    || tryFillCarriedContainer(player, menu, thruster)) {
                thruster.setChanged();
                level.sendBlockUpdated(thruster.getBlockPos(), thruster.getBlockState(), thruster.getBlockState(), 3);
                menu.broadcastChanges();
            }
        });
    }

    // Try to empty carried container
    private static boolean tryEmptyCarriedContainer(Player player, AbstractContainerMenu menu,
            ThrusterBlockEntity thruster) {
        if (!thruster.canAcceptFuel()) {
            return false;
        }

        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }

        ItemStack singleContainer = carried.copyWithCount(1);
        Optional<IFluidHandlerItem> maybeHandler = FluidUtil.getFluidHandler(singleContainer);
        if (maybeHandler.isEmpty()) {
            return false;
        }

        IFluidHandlerItem handler = maybeHandler.get();
        FluidStack contained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (contained.isEmpty()) {
            return false;
        }

        int fillable = thruster.getFuelTank().fill(contained.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (fillable < contained.getAmount()) {
            return false;
        }

        FluidStack drained = handler.drain(contained.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < contained.getAmount()) {
            return false;
        }

        int filled = thruster.getFuelTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled < drained.getAmount()) {
            return false;
        }

        replaceOneCarriedContainer(player, menu, carried, handler.getContainer());
        return true;
    }

    // Try to fill carried container
    private static boolean tryFillCarriedContainer(Player player, AbstractContainerMenu menu,
            ThrusterBlockEntity thruster) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }

        FluidStack stored = thruster.getFuelTank().getFluid();
        if (stored.isEmpty()) {
            return false;
        }

        ItemStack singleContainer = carried.copyWithCount(1);
        Optional<IFluidHandlerItem> maybeHandler = FluidUtil.getFluidHandler(singleContainer);
        if (maybeHandler.isEmpty()) {
            return false;
        }

        IFluidHandlerItem handler = maybeHandler.get();
        FluidStack req = stored.copyWithAmount(Math.min(BUCKET_VOLUME, stored.getAmount()));
        int accepted = handler.fill(req, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return false;
        }

        FluidStack drainPreview = thruster.getFuelTank().drain(stored.copyWithAmount(accepted),
                IFluidHandler.FluidAction.SIMULATE);
        if (drainPreview.isEmpty() || drainPreview.getAmount() < accepted) {
            return false;
        }

        int filled = handler.fill(drainPreview, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return false;
        }

        FluidStack drained = thruster.getFuelTank().drain(stored.copyWithAmount(filled),
                IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false;
        }

        replaceOneCarriedContainer(player, menu, carried, handler.getContainer());
        return true;
    }

    // Replace the one carried container
    private static void replaceOneCarriedContainer(Player player, AbstractContainerMenu menu,
            ItemStack carried, ItemStack res) {
        if (carried.getCount() <= 1) {
            menu.setCarried(res);
            return;
        }

        ItemStack remaining = carried.copy();
        remaining.shrink(1);
        menu.setCarried(remaining);
        if (!res.isEmpty() && !player.getInventory().add(res)) {
            player.drop(res, false);
        }
    }

    // Encode the thruster fuel tank
    private static void encode(RegistryFriendlyByteBuf buffer, ThrusterFuelTankPayload payload) {
        buffer.writeBlockPos(payload.pos());
    }

    // Decode the thruster fuel tank
    private static ThrusterFuelTankPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ThrusterFuelTankPayload(buffer.readBlockPos());
    }
}
