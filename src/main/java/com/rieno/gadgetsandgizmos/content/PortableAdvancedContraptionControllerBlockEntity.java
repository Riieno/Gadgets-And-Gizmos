package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

// Handle saved state for the Portable Advanced Contraption Controller
public class PortableAdvancedContraptionControllerBlockEntity extends AdvancedContraptionControllerBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current changed callback
    private Runnable changedCallback = () -> {
    };
    // Current tracking
    private TrackingSnapshot tracking = TrackingSnapshot.unavailable();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable advanced contraption controller
    public PortableAdvancedContraptionControllerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the changed callback
    public void setChangedCallback(Runnable changedCallback) {
        this.changedCallback = changedCallback == null ? () -> {
        } : changedCallback;
    }

    // Set the changed
    @Override
    public void setChanged() {
        changedCallback.run();
    }

    // Send the data
    @Override
    public void sendData() {
        changedCallback.run();
    }

    // Track the holding player
    public void trackHoldingPlayer(ServerPlayer player) {
        if (player == null) {
            tracking = TrackingSnapshot.unavailable();
            return;
        }
        Vector3d feet = Sable.HELPER.getFeetPos(player, 0.0F);
        UUID subLevelId = SimulatedHelper.getSubLevelId(
                SimulatedHelper.getEntityTrackingSubLevel(player));
        tracking = new TrackingSnapshot(true, true, false,
                new Vec3(feet.x, feet.y, feet.z),
                player.level().dimension().location().toString(),
                subLevelId == null ? "" : subLevelId.toString(),
                player.getGameProfile().getName(), player.getUUID().toString());
    }

    // Track the lectern
    public void trackLectern(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            tracking = TrackingSnapshot.unavailable();
            return;
        }
        Vec3 globalPosition = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
        Object subLevel = Sable.HELPER.getContaining(level, pos);
        UUID subLevelId = SimulatedHelper.getSubLevelId(subLevel);
        tracking = new TrackingSnapshot(true, false, true, globalPosition,
                level.dimension().location().toString(),
                subLevelId == null ? "" : subLevelId.toString(), "", "");
    }

    // Clear the tracking
    public void clearTracking() {
        tracking = TrackingSnapshot.unavailable();
    }

    // Get the named controller event position
    @Override
    public Vec3 namedControllerEventPosition() {
        return tracking.available() ? tracking.position() : super.namedControllerEventPosition();
    }

    // Check if the controller tracker is available
    @Override
    public boolean isControllerTrackerAvailable() {
        return true;
    }

    // Get the portable tracking value
    @Override
    public AdvancedGraphDocument.Value getPortableTrackingValue(String port) {
        return switch (port == null ? "" : port) {
            case "available" -> AdvancedGraphDocument.Value.bool(tracking.available());
            case "holding_player", "is_player" -> AdvancedGraphDocument.Value.bool(tracking.holdingPlayer());
            case "on_lectern" -> AdvancedGraphDocument.Value.bool(tracking.onLectern());
            case "is_mannequin", "is_armor_stand" -> AdvancedGraphDocument.Value.bool(false);
            case "x" -> AdvancedGraphDocument.Value.number(tracking.position().x);
            case "y" -> AdvancedGraphDocument.Value.number(tracking.position().y);
            case "z" -> AdvancedGraphDocument.Value.number(tracking.position().z);
            case "dimension" -> AdvancedGraphDocument.Value.string(tracking.dimension());
            case "sub_level" -> AdvancedGraphDocument.Value.string(tracking.subLevel());
            case "wearer_type" -> AdvancedGraphDocument.Value.string(
                    tracking.holdingPlayer() ? "player" : tracking.onLectern() ? "lectern" : "");
            case "player_name", "wearer_name" -> AdvancedGraphDocument.Value.string(tracking.playerName());
            case "player_uuid", "wearer_uuid" -> AdvancedGraphDocument.Value.string(tracking.playerUuid());
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Check if the controller runtime is loaded
    @Override
    protected boolean isControllerRuntimeLoaded() {
        return getLevel() != null && !getLevel().isClientSide && !isRemoved();
    }

    // Check if this should notify output neighbors
    @Override
    protected boolean shouldNotifyOutputNeighbors() {
        return false;
    }

    // Check if the player can use this
    @Override
    public boolean canPlayerUse(Player player) {
        return true;
    }

    // Store the tracking snapshot
    private record TrackingSnapshot(boolean available, boolean holdingPlayer, boolean onLectern,
                                    Vec3 position, String dimension, String subLevel,
                                    String playerName, String playerUuid) {
        // Create an unavailable tracking snapshot
        private static TrackingSnapshot unavailable() {
            return new TrackingSnapshot(false, false, false, Vec3.ZERO, "", "", "", "");
        }
    }
}
