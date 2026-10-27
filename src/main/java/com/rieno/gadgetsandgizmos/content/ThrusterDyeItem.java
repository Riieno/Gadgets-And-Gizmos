package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectTarget;
import com.rieno.gadgetsandgizmos.lib.tooltip.ColorEffectTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ThrusterDyeItem extends Item{
    public ThrusterDyeItem(Properties properties){ super(properties.stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand){
        if(!player.isShiftKeyDown()) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if(player instanceof ServerPlayer server) ThrusterDyeMenu.open(server, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public InteractionResult useOn(UseOnContext ctx){
        Player player = ctx.getPlayer();
        if(player == null) return InteractionResult.PASS;
        if(player.isShiftKeyDown()){
            if(player instanceof ServerPlayer server) ThrusterDyeMenu.open(server, ctx.getHand());
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        var target = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if(!(target instanceof ColorEffectTarget)) target = SimulatedHelper.findBlockEntityIncludingSubLevels(ctx.getLevel(), ctx.getClickedPos());
        if(!(target instanceof ColorEffectTarget effect)) return InteractionResult.PASS;
        if(player instanceof ServerPlayer server){
            if(!WorldAccessPolicy.canAccessLocal(server, server.serverLevel(), SimulatedHelper.getContainingSubLevelId(target), target.getBlockPos())) return InteractionResult.FAIL;
            var preset = ThrusterDyeData.preset(ctx.getItemInHand());
            if(!player.getAbilities().instabuild && !preset.active() && !preset.additive()
                    && !ThrusterDyeData.storage(ctx.getItemInHand()).loaded()){
                player.displayClientMessage(Component.translatable("createthrusters.thruster_dye.load_all"), true);
                return InteractionResult.FAIL;
            }
            effect.applyColorEffect(preset);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override public Optional<TooltipComponent> getTooltipImage(ItemStack stack){
        return Optional.of(ColorEffectTooltip.fromPalette(ThrusterDyeData.preset(stack), ThrusterDyeData.storage(stack)));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flags){
        lines.add(Component.translatable("createthrusters.thruster_dye.open"));
        lines.add(Component.translatable("createthrusters.thruster_dye.reusable"));
    }
}
