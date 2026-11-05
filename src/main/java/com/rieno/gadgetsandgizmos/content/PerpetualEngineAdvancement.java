package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.control.AlternatingValueSequence;
import com.rieno.gadgetsandgizmos.lib.control.ControlDataWriteEvent;
import com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedBlockAccess;
import com.rieno.gadgetsandgizmos.lib.probe.RelativeBlockLayout;
import com.rieno.gadgetsandgizmos.registry.CTAdvancements;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Award a working four-alternator engine after two full cycles of controller-driven face targets
@EventBusSubscriber(modid = CreateThrusters.MOD_ID)
public final class PerpetualEngineAdvancement{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long PERIOD = 32;
    private static final String AUTHOR_TAG = "PerpetualEngineAuthor";
    private static final Set<String> ANGLES = Set.of("north_angle", "south_angle", "east_angle", "west_angle");
    private static final ResourceLocation SET_DATA = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "set_block_data");
    private static final RelativeBlockLayout LAYOUT = layout();
    private static final Map<AdvancedContraptionControllerBlockEntity, Map<TargetKey, Progress>> PROGRESS = new WeakHashMap<>();

    private PerpetualEngineAdvancement(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the player who successfully applied the graph without changing physical input ownership
    public static void recordEditor(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player){
        controller.getPersistentData().putUUID(AUTHOR_TAG, player.getUUID());
        controller.setChanged();
        PROGRESS.remove(controller);
    }

    // Observe accepted face targets rather than requiring a particular arrangement of graph nodes
    @SubscribeEvent
    public static void written(ControlDataWriteEvent evt){
        if(!SET_DATA.equals(evt.operation()) || !(evt.source() instanceof AdvancedContraptionControllerBlockEntity controller)
                || !(evt.target() instanceof BiDirectionalGearboxBlockEntity gearbox)
                || controller.getLevel() == null || controller.getLevel().isClientSide || gearbox.getLevel() == null) return;
        Map<TargetKey, Progress> targets = PROGRESS.computeIfAbsent(controller, ignored -> new HashMap<>());
        TargetKey key = new TargetKey(gearbox.getLevel().dimension().location(),
                SableLevelApi.containingId(gearbox), gearbox.getBlockPos().immutable());
        if(!valid(gearbox)){ targets.remove(key); return; }
        Progress progress = targets.get(key);
        if(progress == null || progress.target.get() != gearbox || progress.revision != evt.revision()){
            progress = new Progress(gearbox, evt.revision());
            targets.put(key, progress);
        }
        Map<String, Double> angles = new HashMap<>();
        for(Direction dir : Direction.Plane.HORIZONTAL){
            String field = dir.getName() + "_angle";
            var val = evt.values().get(field);
            if(val == null) continue;
            double angle = val.asNumber();
            angles.put(field, "number".equals(val.type()) && angle == gearbox.getManualAngle(dir) ? angle : Double.NaN);
        }
        if(angles.isEmpty()) return;
        progress.lastWrite = controller.getLevel().getGameTime();
        if(!progress.sequence.observe(progress.lastWrite, angles)) return;
        UUID playerId = evt.origin() == null ? null : evt.origin().playerId();
        if(playerId == null && controller.getPersistentData().hasUUID(AUTHOR_TAG)){
            playerId = controller.getPersistentData().getUUID(AUTHOR_TAG);
        }
        if(playerId == null){
            var origin = BlockInteractionTracker.last(controller.getLevel(), controller.getBlockPos());
            if(origin != null) playerId = origin.playerId();
        }
        var server = controller.getLevel().getServer();
        ServerPlayer player = server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if(player != null) CTAdvancements.PERPETUAL_ENGINE.get().trigger(player);
    }

    // Breaking the layout, unloading a target or pausing the sequence discards partial progress
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post evt){
        PROGRESS.entrySet().removeIf(entry -> {
            var controller = entry.getKey();
            if(controller.isRemoved() || controller.getLevel() == null) return true;
            long tick = controller.getLevel().getGameTime();
            entry.getValue().values().removeIf(progress -> {
                var target = progress.target.get();
                return target == null || progress.revision != controller.getActiveGraph().revision()
                        || tick < progress.lastWrite || tick - progress.lastWrite > PERIOD || !valid(target);
            });
            return entry.getValue().isEmpty();
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static RelativeBlockLayout layout(){
        Map<BlockPos, Predicate<BlockState>> blocks = new LinkedHashMap<>();
        blocks.put(BlockPos.ZERO, state -> state.is(CTBlocks.BIDIRECTIONAL_GEARBOX.get()));
        for(Direction dir : Direction.Plane.HORIZONTAL){
            blocks.put(BlockPos.ZERO.relative(dir), state -> state.is(CTBlocks.ALTERNATOR.get())
                    && state.getValue(AlternatorBlock.FACING) == dir.getOpposite());
        }
        return new RelativeBlockLayout(blocks);
    }

    private static boolean valid(BiDirectionalGearboxBlockEntity gearbox){
        if(gearbox.isRemoved() || gearbox.getLevel() == null || !gearbox.isServoModeActive()) return false;
        for(Direction dir : Direction.Plane.HORIZONTAL){
            if(gearbox.getFaceMaxAngle(dir) < 360) return false;
        }
        UUID subLevelId = SableLevelApi.containingId(gearbox);
        return LAYOUT.matches(gearbox.getBlockPos(),
                pos -> LoadedBlockAccess.blockState(gearbox.getLevel(), subLevelId, pos));
    }

    private record TargetKey(ResourceLocation dimension, UUID subLevelId, BlockPos pos){}

    private static final class Progress{
        private final WeakReference<BiDirectionalGearboxBlockEntity> target;
        private final long revision;
        private final AlternatingValueSequence sequence = new AlternatingValueSequence(ANGLES, -360, 360, PERIOD, 4);
        private long lastWrite;

        private Progress(BiDirectionalGearboxBlockEntity target, long revision){
            this.target = new WeakReference<>(target);
            this.revision = revision;
        }
    }
}
