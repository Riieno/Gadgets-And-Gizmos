package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

// Drive two independent timed redstone buttons and their link frequency slots
public class DoubleButtonBlock extends CTDirectionalBlock implements IBE<DoubleButtonBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty TOP_POWERED = BooleanProperty.create("top_powered");
    public static final BooleanProperty BOTTOM_POWERED = BooleanProperty.create("bottom_powered");
    public static final BooleanProperty SHOW_LINKS = BooleanProperty.create("show_links");

    private static final double FREQUENCY_SLOT_Y = 1.35D / 16.0D;
    private static final double TOP_SLOT_Z = 5.0D / 16.0D;
    private static final double BOTTOM_SLOT_Z = 11.0D / 16.0D;
    private static final double RED_SLOT_X = 2.0D / 16.0D;
    private static final double BLUE_SLOT_X = 14.0D / 16.0D;
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button block
    public DoubleButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(TOP_POWERED, false)
                .setValue(BOTTOM_POWERED, false)
                .setValue(SHOW_LINKS, true));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOP_POWERED, BOTTOM_POWERED, SHOW_LINKS);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    // Check if this can survive
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).canBeReplaced();
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        if (!level.isClientSide
                && fromPos.equals(pos.relative(state.getValue(FACING).getOpposite()))
                && !canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state);
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // Get the interaction shape
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle double button block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                openModeScreen(doubleButton);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Target target = targetAt(state, pos, hit.getLocation());
        if (!(target instanceof Target.Button buttonTarget)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            startHolding(doubleButton, buttonTarget.button());
            return InteractionResult.SUCCESS;
        }

        doubleButton.activate(buttonTarget.button());
        return InteractionResult.CONSUME;
    }

    // Handle double button block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Target target = targetAt(state, pos, hit.getLocation());
        if (!(target instanceof Target.Frequency frequencyTarget)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            doubleButton.setFrequency(frequencyTarget.button(), frequencyTarget.firstFrequency(), stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    // Update the double button block
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DoubleButtonBlockEntity doubleButton) {
            doubleButton.scheduledReleaseTick();
        }
    }

    // Check if this is a signal source
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    // Get the signal
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(TOP_POWERED) || state.getValue(BOTTOM_POWERED) ? 15 : 0;
    }

    // Get the direct signal
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return dir == state.getValue(FACING) ? getSignal(state, level, pos, dir) : 0;
    }

    // Check if this can connect redstone
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null;
    }

    // Handle the remove event
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DoubleButtonBlockEntity doubleButton) {
                doubleButton.onDestroyed();
            }
            notifyNeighbors(level, pos, state);
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Get the powered property
    public static BooleanProperty poweredProperty(DoubleButtonBlockEntity.ButtonHalf btn) {
        return btn == DoubleButtonBlockEntity.ButtonHalf.TOP ? TOP_POWERED : BOTTOM_POWERED;
    }

    // Check if the link hardware is visible
    public static boolean isLinkHardwareVisible(BlockState state) {
        return !state.hasProperty(SHOW_LINKS) || state.getValue(SHOW_LINKS);
    }

    // Notify the neighbors
    public static void notifyNeighbors(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), block);
        for (Direction dir : Direction.values()) {
            level.updateNeighborsAt(pos.relative(dir), block);
        }
    }

    // Resolve clicks in model space for every block rotation
    public static Target targetAt(BlockState state, BlockPos pos, Vec3 hitLocation) {
        Vec3 local = toModelCoordinates(state, pos, hitLocation).scale(16.0D);
        double x = local.x();
        double y = local.y();
        double z = local.z();
        if (!inside(y, -0.25D, 4.0D)) {
            return Target.NONE;
        }
        if (inside(x, 5.0D, 11.0D) && inside(z, 3.0D, 7.0D)) {
            return new Target.Button(DoubleButtonBlockEntity.ButtonHalf.TOP);
        }
        if (inside(x, 5.0D, 11.0D) && inside(z, 9.0D, 13.0D)) {
            return new Target.Button(DoubleButtonBlockEntity.ButtonHalf.BOTTOM);
        }
        if (inside(x, 0.0D, 4.5D) && inside(z, 2.5D, 7.5D)) {
            return new Target.Frequency(DoubleButtonBlockEntity.ButtonHalf.TOP, true);
        }
        if (inside(x, 11.5D, 16.0D) && inside(z, 2.5D, 7.5D)) {
            return new Target.Frequency(DoubleButtonBlockEntity.ButtonHalf.TOP, false);
        }
        if (inside(x, 0.0D, 4.5D) && inside(z, 8.5D, 13.5D)) {
            return new Target.Frequency(DoubleButtonBlockEntity.ButtonHalf.BOTTOM, true);
        }
        if (inside(x, 11.5D, 16.0D) && inside(z, 8.5D, 13.5D)) {
            return new Target.Frequency(DoubleButtonBlockEntity.ButtonHalf.BOTTOM, false);
        }
        return Target.NONE;
    }

    // Convert the double button block to model coordinates
    public static Vec3 toModelCoordinates(BlockState state, BlockPos pos, Vec3 hitLocation) {
        Vec3 blockLocal = hitLocation.subtract(pos.getX(), pos.getY(), pos.getZ());
        return worldToModel(state.getValue(FACING), blockLocal);
    }

    // Get the frequency slot center
    public static Vec3 frequencySlotCenter(BlockState state, DoubleButtonBlockEntity.ButtonHalf btn,
                                           boolean firstFrequency) {
        double x = firstFrequency ? RED_SLOT_X : BLUE_SLOT_X;
        double z = btn == DoubleButtonBlockEntity.ButtonHalf.TOP ? TOP_SLOT_Z : BOTTOM_SLOT_Z;
        return modelToWorld(state.getValue(FACING), new Vec3(x, FREQUENCY_SLOT_Y, z));
    }

    // Get the shape
    public static VoxelShape shapeFor(BlockState state) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    // Get the button bounds
    public static AABB buttonBounds(BlockState state, DoubleButtonBlockEntity.ButtonHalf btn) {
        boolean powered = state.getValue(poweredProperty(btn));
        double minZ = btn == DoubleButtonBlockEntity.ButtonHalf.TOP ? 3.0D / 16.0D : 9.0D / 16.0D;
        double maxZ = btn == DoubleButtonBlockEntity.ButtonHalf.TOP ? 7.0D / 16.0D : 13.0D / 16.0D;
        AABB modelBounds = new AABB(5.0D / 16.0D, 0.0D, minZ,
                11.0D / 16.0D, (powered ? 1.0D : 2.0D) / 16.0D, maxZ);
        return transformBounds(modelBounds, state.getValue(FACING));
    }

    // Transform the bounds
    private static AABB transformBounds(AABB bounds, Direction facing) {
        double minX = 1.0D;
        double minY = 1.0D;
        double minZ = 1.0D;
        double maxX = 0.0D;
        double maxY = 0.0D;
        double maxZ = 0.0D;
        for (double x : new double[]{bounds.minX, bounds.maxX}) {
            for (double y : new double[]{bounds.minY, bounds.maxY}) {
                for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                    Vec3 corner = modelToWorld(facing, new Vec3(x, y, z));
                    minX = Math.min(minX, corner.x());
                    minY = Math.min(minY, corner.y());
                    minZ = Math.min(minZ, corner.z());
                    maxX = Math.max(maxX, corner.x());
                    maxY = Math.max(maxY, corner.y());
                    maxZ = Math.max(maxZ, corner.z());
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Get the block entity class
    @Override
    public Class<DoubleButtonBlockEntity> getBlockEntityClass() {
        return DoubleButtonBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends DoubleButtonBlockEntity> getBlockEntityType() {
        return CTBlockEntities.DOUBLE_BUTTON.get();
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoubleButtonBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (blockEntityType == CTBlockEntities.DOUBLE_BUTTON.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<DoubleButtonBlockEntity>) DoubleButtonBlockEntity::tick;
        }
        return null;
    }

    // Check if the point is inside the bounds
    private static boolean inside(double val, double min, double max) {
        return val >= min && val <= max;
    }

    // Create the shapes
    private static Map<Direction, VoxelShape> createShapes() {
        VoxelShape base = Shapes.or(
                box(0.0D, 0.0D, 2.9D, 4.0D, 1.0D, 6.9D),
                box(5.0D, 0.0D, 3.0D, 11.0D, 2.0D, 7.0D),
                box(12.0D, 0.0D, 3.0D, 16.0D, 1.0D, 7.0D),
                box(0.0D, 0.0D, 9.0D, 4.0D, 1.0D, 13.0D),
                box(5.0D, 0.0D, 9.0D, 11.0D, 2.0D, 13.0D),
                box(12.0D, 0.0D, 9.0D, 16.0D, 1.0D, 13.0D));
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            shapes.put(dir, transformShape(base, dir));
        }
        return Map.copyOf(shapes);
    }

    // Transform the shape
    private static VoxelShape transformShape(VoxelShape shape, Direction facing) {
        VoxelShape[] res = {Shapes.empty()};
        for (AABB box : shape.toAabbs()) {
            Vec3[] corners = new Vec3[]{
                    transformCorner(box.minX, box.minY, box.minZ, facing),
                    transformCorner(box.minX, box.minY, box.maxZ, facing),
                    transformCorner(box.minX, box.maxY, box.minZ, facing),
                    transformCorner(box.minX, box.maxY, box.maxZ, facing),
                    transformCorner(box.maxX, box.minY, box.minZ, facing),
                    transformCorner(box.maxX, box.minY, box.maxZ, facing),
                    transformCorner(box.maxX, box.maxY, box.minZ, facing),
                    transformCorner(box.maxX, box.maxY, box.maxZ, facing)
            };
            double nextMinX = 16.0D;
            double nextMinY = 16.0D;
            double nextMinZ = 16.0D;
            double nextMaxX = 0.0D;
            double nextMaxY = 0.0D;
            double nextMaxZ = 0.0D;
            for (Vec3 corner : corners) {
                nextMinX = Math.min(nextMinX, corner.x());
                nextMinY = Math.min(nextMinY, corner.y());
                nextMinZ = Math.min(nextMinZ, corner.z());
                nextMaxX = Math.max(nextMaxX, corner.x());
                nextMaxY = Math.max(nextMaxY, corner.y());
                nextMaxZ = Math.max(nextMaxZ, corner.z());
            }
            res[0] = Shapes.or(res[0], box(
                    clampVoxel(nextMinX), clampVoxel(nextMinY), clampVoxel(nextMinZ),
                    clampVoxel(nextMaxX), clampVoxel(nextMaxY), clampVoxel(nextMaxZ)));
        }
        return res[0].optimize();
    }

    // Transform the corner
    private static Vec3 transformCorner(double x, double y, double z, Direction facing) {
        return modelToWorld(facing, new Vec3(x, y, z)).scale(16.0D);
    }

    // Get the model to world
    private static Vec3 modelToWorld(Direction facing, Vec3 modelCoordinates) {
        ModelRotation rotation = rotationFor(facing);
        Vec3 centered = modelCoordinates.subtract(0.5D, 0.5D, 0.5D);
        return rotateY(rotateX(centered, -rotation.xDegrees()), -rotation.yDegrees())
                .add(0.5D, 0.5D, 0.5D);
    }

    // Get the world to model
    private static Vec3 worldToModel(Direction facing, Vec3 worldCoordinates) {
        ModelRotation rotation = rotationFor(facing);
        Vec3 centered = worldCoordinates.subtract(0.5D, 0.5D, 0.5D);
        return rotateX(rotateY(centered, rotation.yDegrees()), rotation.xDegrees())
                .add(0.5D, 0.5D, 0.5D);
    }

    // Clamp the voxel
    private static double clampVoxel(double val) {
        if (val < 1.0E-6D) {
            return 0.0D;
        }
        if (val > 16.0D - 1.0E-6D) {
            return 16.0D;
        }
        return val;
    }

    // Rotate the x
    private static Vec3 rotateX(Vec3 val, float deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(val.x(), val.y() * cos - val.z() * sin, val.y() * sin + val.z() * cos);
    }

    // Rotate the y
    private static Vec3 rotateY(Vec3 val, float deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(val.x() * cos + val.z() * sin, val.y(), -val.x() * sin + val.z() * cos);
    }

    // Get the rotation
    private static ModelRotation rotationFor(Direction facing) {
        return switch (facing) {
            case DOWN -> new ModelRotation(180.0F, 180.0F);
            case EAST -> new ModelRotation(270.0F, 270.0F);
            case NORTH -> new ModelRotation(270.0F, 180.0F);
            case SOUTH -> new ModelRotation(270.0F, 0.0F);
            case UP -> new ModelRotation(0.0F, 180.0F);
            case WEST -> new ModelRotation(270.0F, 90.0F);
        };
    }

    // Store the model rotation
    private record ModelRotation(float xDegrees, float yDegrees) {
    }

    // Open the mode screen
    static void openModeScreen(DoubleButtonBlockEntity doubleButton) {
        try {
            Class<?> screensClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.CTClientScreens");
            Method method = screensClass.getMethod("openDoubleButtonModeScreen", DoubleButtonBlockEntity.class);
            method.invoke(null, doubleButton);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Start the holding
    static void startHolding(DoubleButtonBlockEntity doubleButton, DoubleButtonBlockEntity.ButtonHalf btn) {
        try {
            Class<?> handlerClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.DoubleButtonClientHandler");
            Method method = handlerClass.getMethod("startHolding", DoubleButtonBlockEntity.class,
                    DoubleButtonBlockEntity.ButtonHalf.class);
            method.invoke(null, doubleButton, btn);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Expose the target
    public sealed interface Target permits Target.None, Target.Button, Target.Frequency {
        Target.None NONE = new Target.None();

        // Store the none
        record None() implements Target {
        }

        // Store the button
        record Button(DoubleButtonBlockEntity.ButtonHalf button) implements Target {
        }

        // Store the frequency
        record Frequency(DoubleButtonBlockEntity.ButtonHalf button, boolean firstFrequency) implements Target {
        }
    }
}
