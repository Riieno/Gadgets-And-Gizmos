package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.controller.ExternalBlockEntityDirectControlCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletReopenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Implement the tablet's NFC scanner and nearby-target actions
final class DiagnosticTabletNfcApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet nfc app
    private DiagnosticTabletNfcApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet nfc app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        Target target = target(ctx);
        if ("nfc_signal".equals(action.actionId())) {
            if (target == null || target.blockEntity() == null) return failure("This block has no direct control input");
            int signal;
            try {
                signal = Integer.parseInt(action.arguments().getOrDefault("value", "0"));
            } catch (NumberFormatException err) {
                return failure("Signal strength must be 0-15");
            }
            signal = Math.max(0, Math.min(15, signal));
            if (!ExternalBlockEntityDirectControlCompat.applyDirectSignal(
                    target.blockEntity(), signal / 15.0F)) {
                return failure("This block does not expose direct redstone control");
            }
        } else if (!"refresh".equals(action.actionId()) && !"select".equals(action.actionId())
                && !"nfc_scan".equals(action.actionId())) {
            return failure("Unknown NFC action");
        }
        sendSnapshot(ctx, snapshot(target));
        if ("nfc_scan".equals(action.actionId()) && !ctx.placedSource()) {
            PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletReopenPayload(
                    ctx.player().getOffhandItem() == ctx.tablet()
                            ? net.minecraft.world.InteractionHand.OFF_HAND
                            : net.minecraft.world.InteractionHand.MAIN_HAND));
        }
        return target == null ? failure("Scan a loaded block first") : success("");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the target
    private static Target target(TabletActionContext ctx) {
        if (ctx.placedSource() && ctx.sourceBlockPos() != null) {
            BlockEntity tablet = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                    ctx.sourceSubLevelId(), ctx.sourceBlockPos());
            if (tablet instanceof DiagnosticTabletBlockEntity placed) {
                Direction facing = placed.getBlockState().getValue(DiagnosticTabletBlock.FACING);
                BlockPos support = placed.getBlockPos().relative(facing.getOpposite());
                BlockEntity blockEntity = placed.getLevel().getBlockEntity(support);
                return new Target(placed.getLevel(), placed.getLevel().getBlockState(support),
                        blockEntity, support);
            }
        }
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return null;
        DiagnosticTabletData.Binding binding = DiagnosticTabletAppStorage.selectedBinding(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("nfc"));
        if (binding == null) return null;
        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(
                ctx.player().level(), binding.subLevelId());
        Level targetLevel = subLevel instanceof SubLevel val
                ? val.getLevel() : ctx.player().level();
        if (!targetLevel.isLoaded(binding.pos())) return null;
        BlockState state = targetLevel.getBlockState(binding.pos());
        if (state.isAir()) return null;
        BlockEntity blockEntity = targetLevel.getBlockEntity(binding.pos());
        return new Target(targetLevel, state, blockEntity, binding.pos());
    }

    // Get the snapshot
    private static CompoundTag snapshot(Target target) {
        CompoundTag data = new CompoundTag();
        if (target == null) return data;
        data.putBoolean("Available", true);
        data.putString("Block", BuiltInRegistries.BLOCK.getKey(target.state().getBlock()).toString());
        data.putString("Name", target.state().getBlock().getName().getString());
        data.putLong("Pos", target.pos().asLong());
        data.putFloat("Hardness", target.state().getDestroySpeed(target.level(), target.pos()));
        data.putBoolean("BlockEntity", target.blockEntity() != null);
        Double direct = ExternalBlockEntityDirectControlCompat.sampleDirectSignal(target.blockEntity());
        data.putBoolean("Controllable", direct != null);
        if (direct != null) data.putInt("Signal", Math.max(0, Math.min(15,
                (int) Math.round(direct * 15.0D))));
        ListTag properties = new ListTag();
        CompoundTag readable = AdvancedContraptionControllerBlockEntity.graphBlockDataSnapshot(
                target.level(), target.pos());
        appendProperties(properties, "", readable);
        data.put("Properties", properties);
        return data;
    }

    // Add the properties
    private static void appendProperties(ListTag rows, String prefix, CompoundTag src) {
        List<String> keys = new ArrayList<>(src.getAllKeys());
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            if (rows.size() >= 256) return;
            Tag val = src.get(key);
            if (val == null) continue;
            String name = prefix.isBlank() ? key : prefix + "." + key;
            if (val instanceof CompoundTag nested) {
                appendProperties(rows, name, nested);
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putString("Name", name);
            String text = val.getAsString();
            row.putString("Value", text.length() > 256 ? text.substring(0, 253) + "..." : text);
            rows.add(row);
        }
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx, CompoundTag data) {
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("nfc"), ctx, data));
    }

    // Create a successful diagnostic tablet nfc app
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet nfc app
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }

    // Store the target
    private record Target(Level level, BlockState state, BlockEntity blockEntity, BlockPos pos) {
    }
}
