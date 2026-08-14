package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Convert positions and directions between a mounted assembly and its parent level
final class NestedAssemblyFrame {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Parent nested assembly frame
    private final @Nullable SubLevel parent;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the nested assembly frame
    private NestedAssemblyFrame(@Nullable SubLevel parent) {
        this.parent = parent;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the nested assembly frame
    static NestedAssemblyFrame resolve(BlockEntity anchor) {
        return new NestedAssemblyFrame(anchor == null ? null : Sable.HELPER.getContaining(anchor));
    }

    // Get the parent
    @Nullable SubLevel parent() {
        return parent;
    }

    // Get the parent body
    @Nullable ServerSubLevel parentBody() {
        return parent instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Check if this is removed
    boolean isRemoved() {
        return parent != null && parent.isRemoved();
    }

    // Convert the nested assembly frame to world position
    Vector3d toWorldPosition(Vector3dc parentPosition) {
        return toWorldPosition(parent == null ? null : parent.logicalPose(), parentPosition);
    }

    // Convert the nested assembly frame to world orientation
    Quaterniond toWorldOrientation(Quaterniondc parentOrientation) {
        return toWorldOrientation(parent == null ? null : parent.logicalPose(), parentOrientation);
    }

    // Convert the nested assembly frame to world direction
    Vector3d toWorldDirection(Vector3dc parentDirection) {
        Vector3d res = new Vector3d(parentDirection);
        if (parent != null) {
            parent.logicalPose().orientation().transform(res);
        }
        return res;
    }

    // Convert the nested assembly frame to world position
    static Vector3d toWorldPosition(@Nullable Pose3dc parentPose, Vector3dc parentPosition) {
        Vector3d res = new Vector3d(parentPosition);
        if (parentPose != null) {
            parentPose.transformPosition(res);
        }
        return res;
    }

    // Convert the nested assembly frame to world orientation
    static Quaterniond toWorldOrientation(@Nullable Pose3dc parentPose, Quaterniondc parentOrientation) {
        Quaterniond res = new Quaterniond(parentOrientation);
        if (parentPose != null) {
            res.set(parentPose.orientation()).mul(parentOrientation);
        }
        return res.normalize();
    }

    // Convert the nested assembly frame to parent position
    static Vector3d toParentPosition(@Nullable Pose3dc parentPose, Vector3dc worldPosition) {
        Vector3d res = new Vector3d(worldPosition);
        if (parentPose != null) {
            parentPose.transformPositionInverse(res);
        }
        return res;
    }

    // Convert the nested assembly frame to parent orientation
    static Quaterniond toParentOrientation(@Nullable Pose3dc parentPose, Quaterniondc worldOrientation) {
        Quaterniond res = new Quaterniond(worldOrientation);
        if (parentPose != null) {
            res.set(parentPose.orientation()).invert().mul(worldOrientation);
        }
        return res.normalize();
    }
}
