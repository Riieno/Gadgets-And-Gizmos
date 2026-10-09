package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.lib.geometry.VoxelShapeTransforms;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewSessions;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;

import java.util.EnumMap;
import java.util.Map;

// Mount a camera on any block face and expose its live view
public final class CameraBlock extends DirectionalBlock implements IBE<CameraBlockEntity>{
    private static final MapCodec<CameraBlock> CODEC = simpleCodec(CameraBlock::new);
    private static final VoxelShape BASE = box(2, 0, 2, 14, 2, 14);
    private static final VoxelShape STAND = Shapes.or(box(12, 2, 6.5, 13, 10, 9.5),
            box(3, 2, 6.5, 4, 10, 9.5));
    private static final VoxelShape BODY = Shapes.or(box(4, 4, 5, 12, 12, 11), box(5, 5, 3, 11, 11, 5));
    private static final Vec3 PIVOT = new Vec3(0.5, 0.5, 0.5);
    private static final Map<Direction, VoxelShape> DEFAULT_SHAPES = defaultShapes();

    // Build the locked outline for contexts without a loaded camera block entity
    private static Map<Direction, VoxelShape> defaultShapes(){
        Map<Direction, VoxelShape> res = new EnumMap<>(Direction.class);
        ViewRig.Joints joints = ViewRig.joints(new ViewRig.Angles(0, 0));
        for(Direction facing : Direction.values()){
            Quaterniond mount = new Quaterniond().rotationTo(0, 1, 0,
                    facing.getStepX(), facing.getStepY(), facing.getStepZ());
            res.put(facing, shape(mount, new Quaterniond(mount).mul(joints.stand()),
                    new Quaterniond(mount).mul(joints.stand()).mul(joints.camera())));
        }
        return res;
    }

    // Apply the same nested joint rotations as the stand and camera partial renderer
    static VoxelShape shape(Quaterniond mount, Quaterniond stand, Quaterniond camera){
        return Shapes.or(VoxelShapeTransforms.rotate(BASE, mount, PIVOT),
                VoxelShapeTransforms.rotate(STAND, stand, PIVOT),
                VoxelShapeTransforms.rotate(BODY, camera, PIVOT));
    }

    // Select only the base, stand and lens boxes in their current pose
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx){
        return level.getBlockEntity(pos) instanceof CameraBlockEntity camera
                ? camera.shape() : DEFAULT_SHAPES.get(state.getValue(FACING));
    }

    // Keep physical collisions consistent with the visible selection outline
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx){
        return getShape(state, level, pos, ctx);
    }

    // Set the default floor-mounted camera state
    public CameraBlock(Properties props){
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }
    // Supply the block codec
    @Override
    protected MapCodec<? extends DirectionalBlock> codec(){ return CODEC; }
    // Place the mounting normal against the selected face
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx){
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }
    // Persist the mounting direction
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder){
        builder.add(FACING);
    }
    // Resolve the camera block entity class
    @Override
    public Class<CameraBlockEntity> getBlockEntityClass(){ return CameraBlockEntity.class; }
    // Resolve the camera block entity registration
    @Override
    public BlockEntityType<? extends CameraBlockEntity> getBlockEntityType(){ return CTBlockEntities.CAMERA.get(); }
    // Enter the view through direct physical interaction
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit){
        if(player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CameraBlockEntity camera){
            RemoteViewSessions.open(serverPlayer, ViewReference.of(camera));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
