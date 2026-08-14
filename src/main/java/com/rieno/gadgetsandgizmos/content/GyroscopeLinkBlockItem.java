package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.lib.control.LinkedOrientationSource;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

import java.util.List;

// Handle Gyroscope Link Block
public class GyroscopeLinkBlockItem extends ClickToLinkBlockItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyroscope link block item
    public GyroscopeLinkBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle gyroscope link block item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        boolean placingLinkedBlock = ctx.getItemInHand().has(AllDataComponents.CLICK_TO_LINK_DATA);
        BlockPos clickedPos = ctx.getClickedPos();
        BlockState clickedState = ctx.getLevel().getBlockState(clickedPos);
        BlockPos placedPos = clickedPos.relative(ctx.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);
        InteractionResult res = super.useOn(ctx);
        if (!ctx.getLevel().isClientSide && placingLinkedBlock && res.consumesAction()) {
            BlockEntity blockEntity = ctx.getLevel().getBlockEntity(placedPos);
            if (blockEntity instanceof GyroscopeLinkBlockEntity link) {
                link.refreshSourceRangesFromLinkedTarget();
            }
        }
        return res;
    }

    // Check if the target is valid
    @Override
    public boolean isValidTarget(LevelAccessor level, BlockPos pos) {
        if (!CTConfigs.SERVER.enableGyroscopeLinking.get()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return SimulatedHelper.isGimbalSensor(blockEntity)
                || SimulatedHelper.isSteeringWheel(blockEntity)
                || AeroworksControllerCompat.isAdvancedDataLinkSource(blockEntity)
                || blockEntity instanceof LinkedOrientationSource src && src.isOrientationSourceActive()
                || blockEntity instanceof AnalogueContraptionControllerBlockEntity
                || blockEntity instanceof NavigationTableExtensionAccess
                || blockEntity instanceof UniversalDisplayAdapterBlockEntity;
    }

    // Get the max distance from selection
    @Override
    public int getMaxDistanceFromSelection() {
        return CTConfigs.SERVER.gyroscopeLinkRange.get();
    }

    // Get the message translation key
    @Override
    public String getMessageTranslationKey() {
        return "gyroscope_link";
    }

    // Check if this is foil
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(com.simibubi.create.AllDataComponents.CLICK_TO_LINK_DATA) || super.isFoil(stack);
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);

        CTTooltipHelper.addCreateDescription(this, tooltip);
        if (stack.has(com.simibubi.create.AllDataComponents.CLICK_TO_LINK_DATA)) {

            tooltip.add(Component.translatable("gyroscope_link.set").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("gyroscope_link.place_within_range").withStyle(ChatFormatting.GRAY));
        }
    }
}
