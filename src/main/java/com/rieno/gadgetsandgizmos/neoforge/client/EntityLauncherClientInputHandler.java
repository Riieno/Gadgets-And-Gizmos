package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.EntityLauncherActions;
import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherAnchorControlPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherInputPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherPowerModePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

// Send configured launcher controls independently of vanilla movement and item use
public final class EntityLauncherClientInputHandler {
    private static int lastActions;
    private static boolean wasActive;
    private static boolean lastMountedFireHeld;
    private static boolean lastMountedDismountHeld;
    private static boolean lastMountedDetachHeld;
    private static int syncTicks;

    private EntityLauncherClientInputHandler() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean ready = player != null && minecraft.level != null && minecraft.screen == null
                && !minecraft.isPaused() && minecraft.isWindowActive();
        BlockPos mountedPos = player == null ? null : getMountedAnchorPos(player);
        InteractionHand hand = player == null ? null : preferredLauncherHand(player);
        boolean active = ready && mountedPos == null && hand != null;
        if (CTActionKeys.LAUNCHER_MODE.sample(active).wasPressed(1)) {
            PacketDistributor.sendToServer(new ServerboundEntityLauncherPowerModePacket(hand));
        }
        if (mountedPos != null) {
            tickMountedControls(player, mountedPos, ready);
        } else {
            lastMountedFireHeld = lastMountedDismountHeld = lastMountedDetachHeld = false;
        }
        int actions = 0;
        if (active) {
            boolean placing = isPlacementUse(minecraft, player);
            boolean placingFire = placing && minecraft.options.keyUse.getKey().equals(CTActionKeys.LAUNCHER_FIRE.mapping().getKey());
            boolean placingReel = placing && minecraft.options.keyUse.getKey().equals(CTActionKeys.LAUNCHER_REEL.mapping().getKey());
            if (!placingFire && CTActionKeys.LAUNCHER_FIRE.isHeld()) actions |= EntityLauncherActions.FIRE;
            if (!placingReel && CTActionKeys.LAUNCHER_REEL.isHeld()) actions |= EntityLauncherActions.REEL;
            if (CTActionKeys.LAUNCHER_LENGTHEN.isHeld()) actions |= EntityLauncherActions.LENGTHEN;
            if (CTActionKeys.LAUNCHER_SHORTEN.isHeld()) actions |= EntityLauncherActions.SHORTEN;
            if (CTActionKeys.LAUNCHER_DETACH.isHeld()) actions |= EntityLauncherActions.DETACH;
        }
        if (player != null && (actions != lastActions || active != wasActive || active && ++syncTicks >= 10)) {
            PacketDistributor.sendToServer(new ServerboundEntityLauncherInputPacket(actions, active));
            syncTicks = 0;
        }
        lastActions = actions;
        wasActive = active;
    }

    // Reserve ordinary Use for placement while action keys drive held and mounted launchers
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.isCanceled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null) return;
        if (getMountedAnchorPos(player) == null
                && (preferredLauncherHand(player) == null || isPlacementUse(minecraft, player))) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static boolean isPlacementUse(Minecraft minecraft, Player player) {
        return player.isShiftKeyDown() && minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static InteractionHand preferredLauncherHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof EntityLauncherItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof EntityLauncherItem) return InteractionHand.OFF_HAND;
        return null;
    }

    private static void tickMountedControls(Player player, BlockPos pos, boolean active) {
        boolean fire = active && CTActionKeys.LAUNCHER_FIRE.isHeld();
        boolean dismount = active && CTActionKeys.LAUNCHER_DISMOUNT.isHeld();
        boolean detach = active && CTActionKeys.LAUNCHER_DETACH.isHeld();
        if (fire != lastMountedFireHeld) {
            sendMountedControl(pos, fire ? ServerboundEntityLauncherAnchorControlPacket.USE_PRESSED
                    : active ? ServerboundEntityLauncherAnchorControlPacket.USE_RELEASED
                    : ServerboundEntityLauncherAnchorControlPacket.CANCEL_USE, player);
        }
        if (dismount && !lastMountedDismountHeld) {
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.DISMOUNT, player);
        } else if (detach && !lastMountedDetachHeld) {
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.DETACH, player);
        }
        if (active && ++syncTicks >= 2) {
            sendMountedControl(pos, ServerboundEntityLauncherAnchorControlPacket.AIM, player);
            syncTicks = 0;
        }
        lastMountedFireHeld = fire;
        lastMountedDismountHeld = dismount;
        lastMountedDetachHeld = detach;
    }

    private static void sendMountedControl(BlockPos pos, int action, Player player) {
        PacketDistributor.sendToServer(new ServerboundEntityLauncherAnchorControlPacket(
                pos, action, player.getXRot(), player.getYRot()));
    }

    private static BlockPos getMountedAnchorPos(Player player) {
        return player.isPassenger() && player.getVehicle() instanceof ArmorStand mount && mount.isInvisible()
                ? mount.blockPosition().immutable() : null;
    }

    // Show mounted actions using the current key assignments
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
                || getMountedAnchorPos(minecraft.player) == null) return;
        List<String> controls = List.of(
                CTActionKeys.LAUNCHER_FIRE.hint("createthrusters.controls.launcher.fire").getString(),
                CTActionKeys.LAUNCHER_DETACH.hint("createthrusters.controls.launcher.detach").getString(),
                CTActionKeys.LAUNCHER_DISMOUNT.hint("createthrusters.controls.launcher.dismount").getString());
        int y = event.getGuiGraphics().guiHeight() - 68;
        for (String control : controls) {
            event.getGuiGraphics().drawCenteredString(minecraft.font, control,
                    event.getGuiGraphics().guiWidth() / 2, y, 0xFFB9EAFB);
            y += 12;
        }
    }
}
