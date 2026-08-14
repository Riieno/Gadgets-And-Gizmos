package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonLinkBlockEntity;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Let Physics Assemblers build and remove Scissor Piston heads
@Mixin(PhysicsAssemblerBlockEntity.class)
public abstract class PhysicsAssemblerScissorPistonMixin extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics assembler scissor piston
    protected PhysicsAssemblerScissorPistonMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the redirect scissor piston assembly
    @Inject(method = "assembleOrDisassemble", at = @At("HEAD"), cancellable = true)
    private void createthrusters$redirectScissorPistonAssembly(CallbackInfo ci) {
        Level level = getLevel();
        if (createthrusters$disassembleScissorHeadSubLevel()) {
            ci.cancel();
            return;
        }
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }
        if (createthrusters$assembleOrDisassembleScissorHead(serverLevel)) {
            ci.cancel();
        }
    }

    // Disassemble the scissor head sublevel
    @Unique
    private boolean createthrusters$disassembleScissorHeadSubLevel() {
        SubLevel subLevel = Sable.HELPER.getContaining((BlockEntity) (Object) this);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            return false;
        }
        ScissorPistonLinkBlockEntity link = createthrusters$findScissorLink(serverSubLevel);
        return link != null && link.disassembleParent();
    }

    // Find the scissor link
    @Unique
    private ScissorPistonLinkBlockEntity createthrusters$findScissorLink(ServerSubLevel subLevel) {
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof ScissorPistonLinkBlockEntity link) {
                return link;
            }
        }
        return null;
    }

    // Assemble the disassemble scissor head
    @Unique
    private boolean createthrusters$assembleOrDisassembleScissorHead(ServerLevel level) {
        Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(getBlockState());
        BlockPos targetPos = getBlockPos().relative(stickyFacing);
        ScissorPistonBlockEntity piston = createthrusters$findTargetPiston(level, targetPos);
        if (piston == null) {
            return false;
        }
        if (piston.isMountedAssemblyPresent()) {
            piston.disassembleMountedBlock();
            return true;
        }
        return piston.tryAssembleMountedBlock();
    }

    // Find the target piston
    @Unique
    private ScissorPistonBlockEntity createthrusters$findTargetPiston(ServerLevel level, BlockPos targetPos) {
        ScissorPistonBlockEntity direct = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
                targetPos, ScissorPistonBlockEntity.class);
        if (direct != null) {
            return direct;
        }
        for (Direction dir : Direction.values()) {
            ScissorPistonBlockEntity nearby = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
                    targetPos.relative(dir), ScissorPistonBlockEntity.class);
            if (nearby != null && !nearby.isMountedAssemblyPresent()
                    && nearby.getMountedBlockPos().equals(targetPos)) {
                return nearby;
            }
        }
        return null;
    }
}
