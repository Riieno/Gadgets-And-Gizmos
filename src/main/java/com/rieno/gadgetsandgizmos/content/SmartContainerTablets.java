package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.multiblock.MultiblockPanelLayout;
import com.rieno.gadgetsandgizmos.lib.geometry.VoxelShapeCache;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

// Adapt smart storage geometry to the library's decorative panel layout
public final class SmartContainerTablets{
    private static final VoxelShapeCache SELECTION_SHAPES = new VoxelShapeCache(2048);
    private SmartContainerTablets(){}

    public static boolean supports(BlockEntity target){
        return target instanceof SmartTankBlockEntity || target instanceof SmartVaultBlockEntity
                || target instanceof SmartBatteryBlockEntity;
    }

    public static BlockEntity controller(BlockEntity target){
        if(target instanceof FluidTankBlockEntity tank){ var root = tank.getControllerBE(); return root == null ? tank : root; }
        if(target instanceof ItemVaultBlockEntity vault){ var root = vault.getControllerBE(); return root == null ? vault : root; }
        return target;
    }

    public static String tab(BlockEntity target){
        return controller(target) instanceof MountedTabletState.Host host ? host.mountedTabletState().tab() : "contents";
    }

    public static MountedTabletState.Presentation presentation(BlockEntity target){
        return controller(target) instanceof MountedTabletState.Host host ? host.mountedTabletState().presentation()
                : MountedTabletState.Presentation.CONTENTS;
    }

    public static boolean tabletVisible(BlockEntity target){ return supports(target) && presentation(target).tablet(); }
    public static boolean contentsVisible(BlockEntity target){ return presentation(target).contents(); }

    public static boolean ownsPanel(BlockEntity target){
        for(var panel : panels(target)) if(panel.owner().equals(target.getBlockPos())) return true;
        return false;
    }

    // Apply one wrench choice to every loaded member so it survives controller changes and reloads
    public static boolean cyclePresentation(BlockEntity target){
        if(!supports(target) || target.getLevel() == null || target.getLevel().isClientSide) return false;
        BlockEntity root = controller(target);
        if(!(root instanceof MountedTabletState.Host host)) return false;
        var next = host.mountedTabletState().presentation().next(root instanceof FluidTankBlockEntity);
        int x = 1, y = 1, z = 1;
        if(root instanceof FluidTankBlockEntity tank){ x = z = tank.getWidth(); y = tank.getHeight(); }
        else if(root instanceof ItemVaultBlockEntity vault){
            x = vault.getMainConnectionAxis() == Direction.Axis.X ? vault.getHeight() : vault.getWidth();
            y = vault.getWidth();
            z = vault.getMainConnectionAxis() == Direction.Axis.Z ? vault.getHeight() : vault.getWidth();
        }
        for(BlockPos pos : BlockPos.betweenClosed(root.getBlockPos(), root.getBlockPos().offset(Math.max(1, x) - 1, Math.max(1, y) - 1, Math.max(1, z) - 1))){
            if(!root.getLevel().hasChunkAt(pos)) continue;
            var member = root.getLevel().getBlockEntity(pos);
            if(member == null || member.getType() != root.getType() || controller(member) != root
                    || !(member instanceof MountedTabletState.Host mounted)) continue;
            mounted.mountedTabletState().setPresentation(next);
            member.setChanged();
            if(member instanceof SmartBlockEntity smart) smart.sendData();
        }
        if(root instanceof SmartTankBlockEntity tank) tank.refreshPresentation();
        return true;
    }

    // Newly joined members inherit the controller's saved choice
    public static void inheritPresentation(BlockEntity member){
        if(member.getLevel() == null || member.getLevel().isClientSide) return;
        var root = controller(member);
        if(root == member || !(member instanceof MountedTabletState.Host mounted)
                || !(root instanceof MountedTabletState.Host host)) return;
        if(mounted.mountedTabletState().setPresentation(host.mountedTabletState().presentation())){
            member.setChanged();
            if(member instanceof SmartBlockEntity smart) smart.sendData();
        }
    }

    public static net.minecraft.world.InteractionResult wrench(net.minecraft.world.item.context.UseOnContext ctx){
        var target = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if(!supports(target)) return net.minecraft.world.InteractionResult.PASS;
        if(!ctx.getLevel().isClientSide && cyclePresentation(target) && ctx.getPlayer() != null){
            ctx.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "createthrusters.smart_container.presentation." + presentation(target).id()), true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    public static List<MultiblockPanelLayout.Panel> panels(BlockEntity target){
        if(!tabletVisible(target)) return List.of();
        BlockEntity root = controller(target);
        if(root instanceof FluidTankBlockEntity tank){
            return MultiblockPanelLayout.panels(root.getBlockPos(), tank.getWidth(), tank.getHeight(),
                    tank.getWidth(), Direction.Axis.Y, true, true);
        }
        if(root instanceof ItemVaultBlockEntity vault){
            Direction.Axis axis = vault.getMainConnectionAxis();
            return MultiblockPanelLayout.panels(root.getBlockPos(),
                    axis == Direction.Axis.X ? vault.getHeight() : vault.getWidth(), vault.getWidth(),
                    axis == Direction.Axis.Z ? vault.getHeight() : vault.getWidth(), axis, false, false);
        }
        return List.of();
    }

    public static void selectTab(BlockEntity target, String tab){
        if(SmartContainerManifest.APP.tabs().stream().noneMatch(entry -> entry.id().equals(tab))) return;
        BlockEntity root = controller(target);
        if(root instanceof MountedTabletState.Host host
                && host.mountedTabletState().select(tab)){
            root.setChanged();
            if(root instanceof SmartBlockEntity smart) smart.sendData();
        }
    }

    public static AABB panelBounds(MultiblockPanelLayout.Panel panel){
        return panel.bounds(.75D, .609375D, .10D).inflate(.004D);
    }

    public static boolean isPanelHit(BlockEntity target, BlockHitResult hit){
        if(hit == null) return false;
        for(var panel : panels(target)) if(panelBounds(panel).inflate(.0001D).contains(hit.getLocation())) return true;
        return false;
    }

    public static VoxelShape selectionShape(BlockGetter level,
            BlockPos pos, VoxelShape base){
        var target = level.getBlockEntity(pos);
        var cube = new AABB(pos).inflate(.001D);
        List<AABB> boxes = new ArrayList<>();
        for(var panel : panels(target)){
            var bounds = panelBounds(panel);
            if(bounds.intersects(cube)) boxes.add(bounds.move(-pos.getX(), -pos.getY(), -pos.getZ()));
        }
        return SELECTION_SHAPES.append(base, boxes);
    }

    public static boolean interact(Level level, BlockPos pos,
            Player player, BlockHitResult hit){
        return interact(level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    public static boolean interact(Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit){
        var interaction = resolveInteraction(level, hit);
        if(interaction == null) return false;
        if(!player.getMainHandItem().isEmpty() || !player.getItemInHand(hand).isEmpty()) return false;
        if(player instanceof ServerPlayer server) SmartContainerMenu.open(server, interaction.target());
        return true;
    }

    public record PanelInteraction(BlockEntity target, BlockHitResult hit){}

    public static PanelInteraction resolveInteraction(Level level, BlockHitResult hit){
        if(level == null || hit == null) return null;
        var target = level.getBlockEntity(hit.getBlockPos());
        boolean local = supports(target);
        if(!local) target = SimulatedHelper.findBlockEntityIncludingSubLevels(level, hit.getBlockPos());
        if(!supports(target)) return null;
        var location = local ? hit.getLocation() : SimulatedHelper.toContainingLocalPosition(target, hit.getLocation());
        var localHit = new BlockHitResult(location, hit.getDirection(), target.getBlockPos(), hit.isInside());
        boolean containerHit = !tabletVisible(target)
                && new AABB(target.getBlockPos()).inflate(.001D).contains(location);
        return containerHit || isPanelHit(target, localHit) ? new PanelInteraction(target, localHit) : null;
    }

    // Derive a stable screen identity without placing or saving a second block entity
    public static UUID tabletId(BlockEntity target){
        BlockEntity root = controller(target);
        return UUID.nameUUIDFromBytes((root.getLevel().dimension().location() + "|"
                + SimulatedHelper.getContainingSubLevelId(root) + "|" + root.getBlockPos().asLong()
                + "|smart_container_manifest").getBytes(StandardCharsets.UTF_8));
    }

    public static DiagnosticTabletData.State state(BlockEntity target){
        return DiagnosticTabletData.State.DEFAULT.withTabletId(tabletId(target))
                .withApp(SmartContainerManifest.ID, tab(target));
    }
}
