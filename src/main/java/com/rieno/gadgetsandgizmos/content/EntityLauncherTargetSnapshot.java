package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Capture the Entity Launcher Target state needed after its source unloads
final class EntityLauncherTargetSnapshot {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Entity launcher target snapshot type
    private final Type type;
    // Entity id
    @Nullable
    private final UUID entityId;
    // Entity attach offset
    private final Vec3 entityAttachOffset;
    // Connector pos
    @Nullable
    private final BlockPos connectorPos;
    // Connector sub-level id
    @Nullable
    private final UUID connectorSubLevelId;
    // Anchor sub-level id
    @Nullable
    private final UUID anchorSubLevelId;
    // Local anchor
    @Nullable
    private final Vec3 localAnchor;
    // World anchor
    @Nullable
    private final Vec3 worldAnchor;
    // Attached block
    @Nullable
    private final BlockPos attachedBlock;
    // Attached face
    @Nullable
    private final Direction attachedFace;
    // Rope length
    private final double ropeLength;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher target snapshot
    private EntityLauncherTargetSnapshot(Type type,
                                         @Nullable UUID entityId,
                                         Vec3 entityAttachOffset,
                                         @Nullable BlockPos connectorPos,
                                         @Nullable UUID connectorSubLevelId,
                                         @Nullable UUID anchorSubLevelId,
                                         @Nullable Vec3 localAnchor,
                                         @Nullable Vec3 worldAnchor,
                                         @Nullable BlockPos attachedBlock,
                                         @Nullable Direction attachedFace,
                                         double ropeLength) {
        this.type = type;
        this.entityId = entityId;
        this.entityAttachOffset = entityAttachOffset;
        this.connectorPos = connectorPos;
        this.connectorSubLevelId = connectorSubLevelId;
        this.anchorSubLevelId = anchorSubLevelId;
        this.localAnchor = localAnchor;
        this.worldAnchor = worldAnchor;
        this.attachedBlock = attachedBlock;
        this.attachedFace = attachedFace;
        this.ropeLength = Math.max(1.0D, ropeLength);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Capture the entity launcher target snapshot
    static @Nullable EntityLauncherTargetSnapshot capture(@Nullable EntityLauncherClawEntity claw) {
        if (claw == null || claw.isRemoved() || (!claw.hasGrabbedEntity() && !claw.hasBlockAnchor())) {
            return null;
        }

        double transferredLength = Math.max(1.0D, claw.getRenderRopeLength());
        if (claw.hasGrabbedEntity()) {
            UUID targetEntityId = claw.getGrabbedEntityId();
            if (targetEntityId == null) {
                return null;
            }
            return new EntityLauncherTargetSnapshot(Type.ENTITY, targetEntityId,
                    claw.getGrabbedEntityAttachOffset(), null, null, null, null, null, null, null,
                    transferredLength);
        }

        BlockPos connectorPos = claw.getAttachedConnectorPosForTransfer();
        if (connectorPos != null) {
            return new EntityLauncherTargetSnapshot(Type.CONNECTOR, null, Vec3.ZERO, connectorPos.immutable(),
                    claw.getAttachedConnectorSubLevelIdForTransfer(), null, null, null, null, null,
                    transferredLength);
        }

        Vec3 localAnchor = claw.getAttachedAnchorLocalPositionForTransfer();
        if (localAnchor == null) {
            return null;
        }

        UUID subLevelId = claw.getAttachedSubLevelId();
        BlockPos attachedBlock = claw.getAttachedBlockPosForTransfer();
        Direction attachedFace = claw.getAttachedBlockFaceForTransfer();
        if (attachedBlock != null && attachedFace != null) {
            return new EntityLauncherTargetSnapshot(Type.BLOCK_ANCHOR, null, Vec3.ZERO, null, null,
                    subLevelId, localAnchor, null, attachedBlock.immutable(), attachedFace, transferredLength);
        }

        Vec3 worldAnchor = claw.getAttachedAnchorWorldPositionForTransfer();
        if (worldAnchor == null) {
            return null;
        }
        return new EntityLauncherTargetSnapshot(Type.WORLD_ANCHOR, null, Vec3.ZERO, null, null,
                subLevelId, localAnchor, worldAnchor, null, null, transferredLength);
    }

    // Bind the entity launcher target snapshot
    boolean bindTo(EntityLauncherAnchorBlockEntity anchor) {
        UUID knownTargetSubLevelId = switch (type) {
            case CONNECTOR -> connectorSubLevelId;
            case BLOCK_ANCHOR, WORLD_ANCHOR -> anchorSubLevelId;
            case ENTITY -> null;
        };
        if (!anchor.canAcceptTargetSubLevel(knownTargetSubLevelId)) {
            return false;
        }
        return switch (type) {
            case ENTITY -> bindEntity(anchor);
            case CONNECTOR -> bindConnector(anchor);
            case BLOCK_ANCHOR -> bindBlockAnchor(anchor);
            case WORLD_ANCHOR -> bindWorldAnchor(anchor);
        };
    }

    // Prepare the source removal
    void prepareSourceRemoval(EntityLauncherClawEntity claw) {
        if (type == Type.CONNECTOR) {
            claw.preserveAttachedConnectorForTransfer();
        }
    }

    // Bind the entity
    private boolean bindEntity(EntityLauncherAnchorBlockEntity anchor) {
        if (entityId == null) {
            return false;
        }
        anchor.bindTargetEntity(entityId, ropeLength, entityAttachOffset == null ? Vec3.ZERO : entityAttachOffset);
        return anchor.hasEntityTarget();
    }

    // Bind the connector
    private boolean bindConnector(EntityLauncherAnchorBlockEntity anchor) {
        if (connectorPos == null) {
            return false;
        }
        anchor.bindTargetConnector(connectorPos, connectorSubLevelId, ropeLength);
        return anchor.hasAnchorTarget();
    }

    // Bind the block anchor
    private boolean bindBlockAnchor(EntityLauncherAnchorBlockEntity anchor) {
        if (localAnchor == null || attachedBlock == null || attachedFace == null) {
            return false;
        }
        anchor.bindDeferredBlockAnchor(anchorSubLevelId, localAnchor, attachedBlock.relative(attachedFace), ropeLength);
        return anchor.hasAnchorTarget();
    }

    // Bind the world anchor
    private boolean bindWorldAnchor(EntityLauncherAnchorBlockEntity anchor) {
        if (worldAnchor == null) {
            return false;
        }
        anchor.bindTargetAnchor(worldAnchor, anchorSubLevelId, ropeLength);
        return anchor.hasAnchorTarget();
    }

    // Define the type values
    private enum Type {
        ENTITY,
        CONNECTOR,
        BLOCK_ANCHOR,
        WORLD_ANCHOR
    }
}
