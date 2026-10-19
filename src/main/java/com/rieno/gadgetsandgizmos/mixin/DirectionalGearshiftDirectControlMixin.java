package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.control.BlockStateControlReceiver;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticBlockStateControl;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider;
import java.util.Map;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.TickPriority;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Locale;

// Route Directional Gearshift Direct Control through the addon's direct-control API
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlockEntity")
public abstract class DirectionalGearshiftDirectControlMixin extends SplitShaftBlockEntity
    implements IDirectControlReceiver, BlockEntityDataProvider, BlockStateControlReceiver {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the directional gearshift direct control
    protected DirectionalGearshiftDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        if (level == null || level.isClientSide) {
            return;
        }

        String channel = channelId == null ? "" : channelId.toLowerCase(Locale.ROOT);
        boolean powered = Mth.clamp(val, 0.0f, 1.0f) > 0.5f;
        BlockState state = getBlockState();
        boolean changed = false;
        if (channel.contains("left")) {
            if (state.hasProperty(DirectionalGearshiftBlock.LEFT_POWERED) && state.getValue(DirectionalGearshiftBlock.LEFT_POWERED) != powered) {
                state = state.setValue(DirectionalGearshiftBlock.LEFT_POWERED, powered);
                changed = true;
            }
        } else if (channel.contains("right")) {
            if (state.hasProperty(DirectionalGearshiftBlock.RIGHT_POWERED) && state.getValue(DirectionalGearshiftBlock.RIGHT_POWERED) != powered) {
                state = state.setValue(DirectionalGearshiftBlock.RIGHT_POWERED, powered);
                changed = true;
            }
        } else {
            return;
        }

        if(changed) applyControlledBlockState(state);
    }


    // Select one gearshift input and clear its opposite in one block-state and
    // kinetic-network update. A directional gearshift cannot represent both
    // requested directions simultaneously.
    @Override
    public void applyExclusiveDirectControllerSignal(
            String activeChannelId,
            String inactiveChannelId,
            float val
    ) {
        if (level == null || level.isClientSide) {
            return;
        }
        String activeChannel = activeChannelId == null
                ? "" : activeChannelId.toLowerCase(Locale.ROOT);
        String inactiveChannel = inactiveChannelId == null
                ? "" : inactiveChannelId.toLowerCase(Locale.ROOT);
        boolean powered = Mth.clamp(val, 0.0F, 1.0F) > 0.5F;
        BlockState state = getBlockState();
        BlockState updated = setDirectionalInput(state, inactiveChannel, false);
        updated = setDirectionalInput(updated, activeChannel, powered);
        if (updated.equals(state)) {
            return;
        }
        applyControlledBlockState(updated);
    }


    // Set one named directional input without publishing an intermediate state.
    private static BlockState setDirectionalInput(
            BlockState state,
            String channel,
            boolean powered
    ) {
        if (channel.contains("left")
                && state.hasProperty(DirectionalGearshiftBlock.LEFT_POWERED)) {
            return state.setValue(DirectionalGearshiftBlock.LEFT_POWERED, powered);
        }
        if (channel.contains("right")
                && state.hasProperty(DirectionalGearshiftBlock.RIGHT_POWERED)) {
            return state.setValue(DirectionalGearshiftBlock.RIGHT_POWERED, powered);
        }
        return state;
    }

    // Expose each redstone input independently, including the stopped state with both inputs powered
    @Override
    public Map<String, String> graphReadableData(){
        return Map.of("left_powered", "boolean", "right_powered", "boolean");
    }

    @Override
    public Map<String, String> graphWritableData(){ return graphReadableData(); }

    @Override
    public GraphValue readGraphValue(String port){
        return switch(port){
            case "left_powered" -> GraphValue.bool(getBlockState().getValue(DirectionalGearshiftBlock.LEFT_POWERED));
            case "right_powered" -> GraphValue.bool(getBlockState().getValue(DirectionalGearshiftBlock.RIGHT_POWERED));
            default -> null;
        };
    }

    @Override
    public boolean writeGraphValue(String port, GraphValue value){
        return value != null && port != null && writeGraphValues(Map.of(port, value));
    }

    @Override
    public boolean writeGraphValues(Map<String, GraphValue> values){
        if(values == null || values.isEmpty()) return false;
        BlockState state = getBlockState();
        GraphValue left = values.get("left_powered");
        GraphValue right = values.get("right_powered");
        if(left != null) state = state.setValue(DirectionalGearshiftBlock.LEFT_POWERED, left.asBoolean());
        if(right != null) state = state.setValue(DirectionalGearshiftBlock.RIGHT_POWERED, right.asBoolean());
        return applyControlledBlockState(state);
    }

    @Override
    public boolean applyControlledBlockState(BlockState state){
        return KineticBlockStateControl.apply(this, state);
    }

}
