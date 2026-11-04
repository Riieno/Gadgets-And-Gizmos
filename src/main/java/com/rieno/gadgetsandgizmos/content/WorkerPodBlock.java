package com.rieno.gadgetsandgizmos.content;

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Host one mannequin worker as an Advanced Contraption Controller extension
public class WorkerPodBlock extends HorizontalDirectionalBlock implements IBE<WorkerPodBlockEntity> {
    public static final MapCodec<WorkerPodBlock> CODEC = simpleCodec(WorkerPodBlock::new);
    private static final VoxelShape FLOOR_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(FLOOR_SHAPE,
            Block.box(0.0D, 1.0D, 0.0D, 1.0D, 28.0D, 16.0D),
            Block.box(15.0D, 1.0D, 0.0D, 16.0D, 28.0D, 16.0D),
            Block.box(1.0D, 1.0D, 15.0D, 15.0D, 28.0D, 16.0D),
            Block.box(1.0D, 28.0D, 1.0D, 15.0D, 32.0D, 15.0D),
            Block.box(5.0D, 11.0D, 13.0D, 11.0D, 18.0D, 15.0D));
    private static final VoxelShape EAST_SHAPE = Shapes.or(FLOOR_SHAPE,
            Block.box(0.0D, 1.0D, 0.0D, 16.0D, 28.0D, 1.0D),
            Block.box(0.0D, 1.0D, 15.0D, 16.0D, 28.0D, 16.0D),
            Block.box(0.0D, 1.0D, 1.0D, 1.0D, 28.0D, 15.0D),
            Block.box(1.0D, 28.0D, 1.0D, 15.0D, 32.0D, 15.0D),
            Block.box(1.0D, 11.0D, 5.0D, 3.0D, 18.0D, 11.0D));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(FLOOR_SHAPE,
            Block.box(0.0D, 1.0D, 0.0D, 1.0D, 28.0D, 16.0D),
            Block.box(15.0D, 1.0D, 0.0D, 16.0D, 28.0D, 16.0D),
            Block.box(1.0D, 1.0D, 0.0D, 15.0D, 28.0D, 1.0D),
            Block.box(1.0D, 28.0D, 1.0D, 15.0D, 32.0D, 15.0D),
            Block.box(5.0D, 11.0D, 1.0D, 11.0D, 18.0D, 3.0D));
    private static final VoxelShape WEST_SHAPE = Shapes.or(FLOOR_SHAPE,
            Block.box(0.0D, 1.0D, 0.0D, 16.0D, 28.0D, 1.0D),
            Block.box(0.0D, 1.0D, 15.0D, 16.0D, 28.0D, 16.0D),
            Block.box(15.0D, 1.0D, 1.0D, 16.0D, 28.0D, 15.0D),
            Block.box(1.0D, 28.0D, 1.0D, 15.0D, 32.0D, 15.0D),
            Block.box(13.0D, 11.0D, 5.0D, 15.0D, 18.0D, 11.0D));

    // Initialize the worker pod block
    public WorkerPodBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    // Get the block codec
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Get the placement state
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // Get the open-front worker pod outline shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return shape(state.getValue(FACING));
    }

    // Keep the pod walls and roof clear of its housed mannequin
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof PlayerMannequinEntity) {
            return FLOOR_SHAPE;
        }
        return shape(state.getValue(FACING));
    }

    // Get the shape matching the placed model rotation
    private static VoxelShape shape(Direction facing) {
        return switch (facing) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    // Get the block entity class
    @Override
    public Class<WorkerPodBlockEntity> getBlockEntityClass() {
        return WorkerPodBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends WorkerPodBlockEntity> getBlockEntityType() {
        return CTBlockEntities.WORKER_POD.get();
    }
}
