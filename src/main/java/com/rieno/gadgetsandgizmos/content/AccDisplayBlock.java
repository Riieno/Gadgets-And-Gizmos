package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Place, align and join the panels which make up an ACC display surface
public class AccDisplayBlock extends HorizontalDirectionalBlock
        implements IBE<AccDisplayBlockEntity>, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final EnumProperty<DisplaySide> SIDE = EnumProperty.create("side", DisplaySide.class);
    public static final EnumProperty<Alignment> Y_ALIGNMENT = EnumProperty.create("y_alignment", Alignment.class);
    public static final EnumProperty<Alignment> Z_ALIGNMENT = EnumProperty.create("z_alignment", Alignment.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Display type
    private final DisplayType displayType;
    // Codec
    private final MapCodec<AccDisplayBlock> codec;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display block
    public AccDisplayBlock(DisplayType displayType, BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        this.displayType = displayType;
        this.codec = simpleCodec(val -> new AccDisplayBlock(displayType, val));
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SIDE, DisplaySide.FRONT)
                .setValue(Y_ALIGNMENT, Alignment.CENTER)
                .setValue(Z_ALIGNMENT, displayType == DisplayType.PANEL
                        ? Alignment.POSITIVE : Alignment.CENTER));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the display type
    public DisplayType displayType() {
        return displayType;
    }

    // Get the codec
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIDE, Y_ALIGNMENT, Z_ALIGNMENT);
    }

    // Get the state for placement
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        Alignment y = Alignment.CENTER;
        Alignment z = displayType == DisplayType.PANEL ? Alignment.POSITIVE : Alignment.CENTER;
        double localY = ctx.getClickLocation().y - ctx.getClickedPos().getY();
        if (displayType == DisplayType.SLAB || displayType == DisplayType.HALF_PANEL) {
            y = ctx.getClickedFace() == Direction.UP || localY < 1.0D / 3.0D
                    ? Alignment.NEGATIVE
                    : ctx.getClickedFace() == Direction.DOWN || localY > 2.0D / 3.0D
                    ? Alignment.POSITIVE : Alignment.CENTER;
        }
        if (displayType == DisplayType.BOARD || displayType == DisplayType.PANEL
                || displayType == DisplayType.HALF_PANEL) {
            double axisPosition = facing.getAxis() == Direction.Axis.X
                    ? ctx.getClickLocation().x - ctx.getClickedPos().getX()
                    : ctx.getClickLocation().z - ctx.getClickedPos().getZ();
            double depthFromFacing = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    ? axisPosition : 1.0D - axisPosition;
            z = ctx.getClickedFace() == facing || depthFromFacing < 1.0D / 3.0D
                    ? Alignment.NEGATIVE
                    : ctx.getClickedFace() == facing.getOpposite() || depthFromFacing > 2.0D / 3.0D
                    ? Alignment.POSITIVE : Alignment.CENTER;
        }
        BlockState placement = defaultBlockState().setValue(FACING, facing)
                .setValue(Y_ALIGNMENT, y).setValue(Z_ALIGNMENT, z);
        Player player = ctx.getPlayer();
        BlockPos placedAgainst = ctx.getClickedPos().relative(
                ctx.getClickedFace().getOpposite());
        BlockState neighbour = ctx.getLevel().getBlockState(placedAgainst);
        if ((player == null || !player.isShiftKeyDown()) && neighbour.getBlock() == this) {
            placement = placement.setValue(FACING, neighbour.getValue(FACING))
                    .setValue(SIDE, neighbour.getValue(SIDE))
                    .setValue(Y_ALIGNMENT, neighbour.getValue(Y_ALIGNMENT))
                    .setValue(Z_ALIGNMENT, neighbour.getValue(Z_ALIGNMENT));
        }
        return placement;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle ACC display block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        AccDisplayBlockEntity display = findDisplay(level, pos);
        if (display == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            try {
                Class<?> screens = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayClientScreens");
                screens.getMethod("interactProjection", AccDisplayBlockEntity.class,
                                BlockHitResult.class, int.class)
                        .invoke(null, display, hit, 1);
            } catch (ReflectiveOperationException ignored) {
            }
            return InteractionResult.SUCCESS;
        }
        if (display.isComputerCraftDisplaySource()) {
            return InteractionResult.CONSUME;
        }
        return display.interact(player, hit, 1) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        AccDisplayBlockEntity display = findDisplay(level, ctx.getClickedPos());
        if (display == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            try {
                Class<?> screens = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayClientScreens");
                screens.getMethod("openModeSelection", AccDisplayBlockEntity.class)
                        .invoke(null, display);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Get the destroy progress
    @Override
    protected float getDestroyProgress(BlockState state, Player player,
                                       BlockGetter level, BlockPos pos) {
        return player.getMainHandItem().isEmpty()
                ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    // Find the display
    public static @Nullable AccDisplayBlockEntity findDisplay(Level level, BlockPos pos) {
        BlockEntity direct = level == null ? null : level.getBlockEntity(pos);
        if (direct instanceof AccDisplayBlockEntity display) {
            return display;
        }
        return SimulatedHelper.findBlockEntityIncludingSubLevels(
                level, pos, AccDisplayBlockEntity.class);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving){
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if(level.isClientSide) return;
        AccDisplayBlockEntity display = findDisplay(level, pos);
        if(display != null) display.queueDisplayRefresh();
    }   

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return switch (displayType) {
            case BLOCK -> Block.box(0, 0, 0, 16, 16, 16);
            case SLAB -> verticalShape(state.getValue(Y_ALIGNMENT), 8.0D, 16.0D);
            case HALF_PANEL -> panelShape(state, 8.0D);
            case BOARD -> panelShape(state,
                    state.getValue(Z_ALIGNMENT) == Alignment.CENTER ? 10.0D : 8.0D);
            case PANEL -> panelShape(state, 3.0D);
        };
    }

    // Get the vertical shape
    private static VoxelShape verticalShape(Alignment alignment, double height, double depth) {
        double minimum = alignment == Alignment.NEGATIVE ? 0.0D
                : alignment == Alignment.POSITIVE ? 16.0D - height : (16.0D - height) / 2.0D;
        return Block.box(0, minimum, 0, 16, minimum + height, depth);
    }

    // Get the panel shape
    private static VoxelShape panelShape(BlockState state, double thickness) {
        Direction facing = state.getValue(FACING);
        Alignment alignment = state.getValue(Z_ALIGNMENT);
        double towardFacing = alignment == Alignment.NEGATIVE ? 0.0D
                : alignment == Alignment.POSITIVE ? 16.0D - thickness : (16.0D - thickness) / 2.0D;
        double minimum = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? towardFacing : 16.0D - thickness - towardFacing;
        double yMinimum = 0.0D;
        double yMaximum = 16.0D;
        if (state.getBlock() instanceof AccDisplayBlock block
                && block.displayType == DisplayType.HALF_PANEL) {
            Alignment y = state.getValue(Y_ALIGNMENT);
            yMinimum = y == Alignment.NEGATIVE ? 0.0D : y == Alignment.POSITIVE ? 8.0D : 4.0D;
            yMaximum = yMinimum + 8.0D;
        }
        return facing.getAxis() == Direction.Axis.Z
                ? Block.box(0, yMinimum, minimum, 16, yMaximum, minimum + thickness)
                : Block.box(minimum, yMinimum, 0, minimum + thickness, yMaximum, 16);
    }

    // Get the block entity class
    @Override
    public Class<AccDisplayBlockEntity> getBlockEntityClass() {
        return AccDisplayBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends AccDisplayBlockEntity> getBlockEntityType() {
        return CTBlockEntities.ACC_DISPLAY.get();
    }

    // Create the block entity
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccDisplayBlockEntity(pos, state);
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        AccDisplayBlockEntity.displayTopologyChanged(level, pos);
    }

    // Handle the remove event
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean isMoving) {
        boolean removed = state.getBlock() != newState.getBlock();
        super.onRemove(state, level, pos, newState, isMoving);
        if (removed) {
            AccDisplayBlockEntity.displayTopologyChanged(level, pos);
        }
    }

    // Get the ticker
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide && type == CTBlockEntities.ACC_DISPLAY.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<AccDisplayBlockEntity>)
                    AccDisplayBlockEntity::tickServer;
        }
        return null;
    }

    // Define the display type values
    public enum DisplayType {
        BOARD,
        BLOCK,
        PANEL,
        HALF_PANEL,
        SLAB
    }

    // Define the display side values
    public enum DisplaySide implements StringRepresentable {
        FRONT("front"),
        BOTH("both");

        // Serialized name
        private final String serializedName;

        // Initialize the display side
        DisplaySide(String serializedName) {
            this.serializedName = serializedName;
        }

        // Get the serialized name
        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    // Define the alignment values
    public enum Alignment implements StringRepresentable {
        NEGATIVE("negative"),
        CENTER("center"),
        POSITIVE("positive");

        // Serialized name
        private final String serializedName;

        // Initialize the alignment
        Alignment(String serializedName) {
            this.serializedName = serializedName;
        }

        // Get the serialized name
        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
