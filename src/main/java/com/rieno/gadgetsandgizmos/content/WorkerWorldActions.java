package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlan;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// Execute item transformations through block and tool interactions in a temporary nearby work spot.
final class WorkerWorldActions{
    private WorkerWorldActions(){}

    static boolean handles(WorkerRecipePlan plan){
        return plan != null && (WorkerRecipeCatalog.WORLD_AXE_STRIP.equals(plan.processorType())
                || WorkerRecipeCatalog.WORLD_ITEM_APPLICATION.equals(plan.processorType()));
    }

    static WorkerCraftingGrid.Result execute(ServerLevel level, PlayerMannequinEntity worker,
                                            WorkerRecipePlan plan, Map<WorkerResourceKey, ItemStack> tools,
                                            Predicate<BlockPos> forbidden){
        if(!handles(plan) || plan.inputs().size() != 2) return WorkerCraftingGrid.Result.EMPTY;
        BlockPos site = findSite(level, worker, forbidden);
        if(site == null) return WorkerCraftingGrid.Result.EMPTY;
        ItemStack base = new ItemStack(BuiltInRegistries.ITEM.get(plan.inputs().getFirst().resource().id()));
        if(!(base.getItem() instanceof BlockItem)) return WorkerCraftingGrid.Result.EMPTY;
        boolean stripping = WorkerRecipeCatalog.WORLD_AXE_STRIP.equals(plan.processorType());
        ItemStack hand = stripping ? tools.getOrDefault(plan.inputs().get(1).resource(), ItemStack.EMPTY).copy()
                : new ItemStack(BuiltInRegistries.ITEM.get(plan.inputs().get(1).resource().id()));
        if(hand.isEmpty() || stripping && !hand.getItem().canPerformAction(hand, ItemAbilities.AXE_STRIP))
            return WorkerCraftingGrid.Result.EMPTY;

        var player = FakePlayerFactory.getMinecraft(level);
        ItemStack previous = player.getMainHandItem().copy();
        Vec3 previousPosition = player.position();
        try{
            player.setPos(worker.getX(), worker.getY(), worker.getZ());
            player.setItemInHand(InteractionHand.MAIN_HAND, base);
            BlockHitResult placement = hit(site.below(), Direction.UP);
            if(!base.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, placement)).consumesAction()
                    || level.isEmptyBlock(site)) return WorkerCraftingGrid.Result.EMPTY;
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            List<ItemStack> byproducts = List.of();
            if(stripping){
                if(!hand.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        hit(site, Direction.UP))).consumesAction()) return WorkerCraftingGrid.Result.EMPTY;
            }else{
                // Create applies manual item-application recipes in a RightClickBlock event.
                // Calling the held item's useOn skips that event, so run the selected recipe's
                // own block transformation after checking the same two ingredients.
                var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
                if(holder == null || !(holder.value() instanceof ManualApplicationRecipe application)
                        || !application.testBlock(level.getBlockState(site))
                        || !application.getRequiredHeldItem().test(hand))
                    return WorkerCraftingGrid.Result.EMPTY;
                var transformed = application.transformBlock(level.getBlockState(site), level.random);
                if(!BuiltInRegistries.ITEM.getKey(transformed.getBlock().asItem()).equals(plan.result().id()))
                    return WorkerCraftingGrid.Result.EMPTY;
                level.destroyBlock(site, false, player);
                if(!level.setBlock(site, transformed, 3)) return WorkerCraftingGrid.Result.EMPTY;
                byproducts = application.rollResults(level.random);
            }
            Block result = level.getBlockState(site).getBlock();
            if(!BuiltInRegistries.ITEM.getKey(result.asItem()).equals(plan.result().id()))
                return WorkerCraftingGrid.Result.EMPTY;
            List<ItemStack> drops = Block.getDrops(level.getBlockState(site), level, site,
                    level.getBlockEntity(site), player, ItemStack.EMPTY);
            ItemStack output = drops.stream().filter(stack -> stack.is(result.asItem()))
                    .findFirst().map(ItemStack::copy).orElse(ItemStack.EMPTY);
            if(output.isEmpty() || output.getCount() != plan.resultAmount())
                return WorkerCraftingGrid.Result.EMPTY;
            level.destroyBlock(site, false, player);
            boolean keepHeld = !stripping && level.getRecipeManager().byKey(plan.recipeId())
                    .map(holder -> holder.value() instanceof ItemApplicationRecipe application
                            && application.shouldKeepHeldItem()).orElse(false);
            List<ItemStack> returned = new java.util.ArrayList<>(byproducts);
            if((stripping || keepHeld) && !hand.isEmpty()) returned.add(hand.copy());
            return new WorkerCraftingGrid.Result(output, returned);
        }finally{
            // Never leave the temporary workpiece behind if the interaction or loot check fails.
            if(!level.isEmptyBlock(site)) level.destroyBlock(site, false, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setPos(previousPosition.x, previousPosition.y, previousPosition.z);
        }
    }

    private static BlockHitResult hit(BlockPos pos, Direction face){
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), face, pos, false);
    }

    private static BlockPos findSite(ServerLevel level, PlayerMannequinEntity worker,
                                     Predicate<BlockPos> forbidden){
        BlockPos origin = worker.blockPosition();
        for(int distance = 1; distance <= 2; distance++){
            for(Direction direction : Direction.Plane.HORIZONTAL){
                BlockPos site = origin.relative(direction, distance);
                if(forbidden.test(site) || !level.isEmptyBlock(site)
                        || !level.getBlockState(site.below()).isFaceSturdy(level, site.below(), Direction.UP))
                    continue;
                if(worker.getBoundingBox() != null
                        && worker.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(site))) continue;
                return site;
            }
        }
        return null;
    }
}
