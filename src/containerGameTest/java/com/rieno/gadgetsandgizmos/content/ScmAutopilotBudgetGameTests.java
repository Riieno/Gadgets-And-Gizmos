package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Set;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ScmAutopilotBudgetGameTests{
    private ScmAutopilotBudgetGameTests(){}

    @GameTest(template="empty_container",batch="scm_autopilot_budget",timeoutTicks=160)
    public static void exactCompoundHullChecksResumeWithoutAcceptingAnUncheckedWall(GameTestHelper ctx){
        var level=ctx.getLevel();
        BlockPos base=ctx.absolutePos(new BlockPos(3,3,3));
        // Keep the chunk nonempty so even an empty corridor requires real block inspection.
        level.setBlock(base.offset(5,0,0),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(base.offset(0,0,4),Blocks.STONE.defaultBlockState(),3);
        Vec3 start=base.getCenter(), end=start.add(0,0,8);
        var budget=new SablePathfinder.CollisionBudget(1,1_000_000_000L);
        var options=new SablePathfinder.CollisionOptions(true,true,Set.of(),36);
        var validator=SablePathfinder.budgetedCompoundCollisionValidator(options,
                query->List.of(new AABB(query.start().subtract(1,1,1),query.start().add(1,1,1)),
                        new AABB(query.start().add(2,-1,-1),query.start().add(3,1,1))),0,budget);
        var query=new SablePathfinder.Query(level,start,end,SablePathfinder.Safety.DEFAULT,SablePathfinder.RouteMode.FLIGHT);
        ctx.assertTrue(validator.validate(query).result()==SablePathfinder.TraversalResult.PENDING,
                "Exact compound hull scan exhausted its work in one call");
        ctx.assertTrue(validator.validate(query).result()==SablePathfinder.TraversalResult.PENDING,
                "Second query exceeded the shared tick allowance");
        advance(ctx,validator,query,0);
    }


    @GameTest(template="empty_container",batch="scm_autopilot_fairness",timeoutTicks=40)
    public static void sharedHullBudgetGivesTheWaitingShipTheNextAllowance(GameTestHelper ctx){
        var level=ctx.getLevel();
        Vec3 start=ctx.absolutePos(new BlockPos(3,3,3)).getCenter();
        level.setBlock(BlockPos.containing(start.add(4,0,0)),Blocks.STONE.defaultBlockState(),3);
        var budget=new SablePathfinder.CollisionBudget(1,1_000_000_000L);
        var options=new SablePathfinder.CollisionOptions(true,true,Set.of(),36);
        java.util.function.Function<SablePathfinder.Query,List<AABB>> hull=query->List.of(
                new AABB(query.start().subtract(1,1,1),query.start().add(1,1,1)));
        var first=SablePathfinder.budgetedCompoundCollisionValidator(options,hull,0,budget);
        var second=SablePathfinder.budgetedCompoundCollisionValidator(options,hull,0,budget);
        var query=new SablePathfinder.Query(level,start,start.add(0,0,8),SablePathfinder.Safety.DEFAULT,SablePathfinder.RouteMode.FLIGHT);
        first.validate(query);second.validate(query);
        ctx.runAfterDelay(1,()->{
            ctx.assertTrue(first.validate(query).result()==SablePathfinder.TraversalResult.PENDING,
                    "First ship monopolized a second tick before its waiting peer");
            second.validate(query);
            try{
                var scans=second.getClass().getDeclaredField("scans");scans.setAccessible(true);
                ctx.assertTrue(!((java.util.Map<?,?>)scans.get(second)).isEmpty(),"Waiting ship got no retained scan work");
            }catch(ReflectiveOperationException err){throw new AssertionError(err);}
            // Release completed or abandoned callers from the shared fairness reservation.
            first.release();
            second.release();
            ctx.succeed();
        });
    }

    private static void advance(GameTestHelper ctx,SablePathfinder.Validator validator,SablePathfinder.Query query,int attempts){
        ctx.runAfterDelay(1,()->{
            var result=validator.validate(query);
            if(result.result()==SablePathfinder.TraversalResult.PENDING){
                ctx.assertTrue(attempts<140,"Retained collision scan never completed");
                advance(ctx,validator,query,attempts+1);
            }else{
                ctx.assertTrue(result.result()==SablePathfinder.TraversalResult.BLOCKED,"Exact hull check missed the wall");
                validator.release();ctx.succeed();
            }
        });
    }
}
