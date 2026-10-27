package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

// Resolve peripheral coordinates through the shared library API
final class ComputerCraftPositionHelper{
    private ComputerCraftPositionHelper(){}
    static Map<String, Object> blockPosition(BlockEntity be){
        return BlockEntityPosition.blockPosition(be);
    }
    static Map<String, Object> blockPosition(BlockEntity be, BlockPos pos){
        return BlockEntityPosition.blockPosition(be, pos);
    }
    static Map<String, Object> blockPosition(Level level, @Nullable UUID id, BlockPos pos){
        return BlockEntityPosition.blockPosition(level, id, pos);
    }
    static Map<String, Object> worldPosition(Level level, Vec3 pos){
        return BlockEntityPosition.worldPosition(level, pos);
    }
    static Map<String, Object> worldDirection(BlockEntity be, Direction dir){
        return BlockEntityPosition.worldDirection(be, dir);
    }
}
