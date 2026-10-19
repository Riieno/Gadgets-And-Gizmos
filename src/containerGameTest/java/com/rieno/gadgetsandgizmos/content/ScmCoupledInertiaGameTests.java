package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmArticulatedFlightControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ScmCoupledInertiaGameTests{
    private ScmCoupledInertiaGameTests(){}

    @GameTest(template="empty_container",batch="scm_coupled_inertia",timeoutTicks=120)
    public static void liveCoupledInertiaProducesOpposingYawRecovery(GameTestHelper ctx){
        var level=ctx.getLevel();
        BlockPos firstPos=ctx.absolutePos(new BlockPos(4,4,4)),secondPos=firstPos.south();
        level.setBlock(firstPos,CTBlocks.SHIP_COUPLER.get().defaultBlockState().setValue(ShipCouplerBlock.FACING,Direction.SOUTH),3);
        level.setBlock(firstPos.north(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(secondPos,CTBlocks.SHIP_COUPLER.get().defaultBlockState().setValue(ShipCouplerBlock.FACING,Direction.NORTH),3);
        level.setBlock(secondPos.south(),Blocks.STONE.defaultBlockState(),3);
        ServerSubLevel first=SubLevelAssemblyApi.assemble(level,firstPos,List.of(firstPos,firstPos.north()));
        ServerSubLevel second=SubLevelAssemblyApi.assemble(level,secondPos,List.of(secondPos,secondPos.south()));
        ctx.assertTrue(first!=null&&second!=null,"Coupled fixture did not assemble");
        var lease=SableSubLevelResidency.lease("coupled_inertia_test/"+first.getUniqueId());lease.retain(first);lease.retain(second);
        ctx.runAtTickTime(10,()->{
            try{
                var a=SubLevelBlockEntityCollector.getBlockEntities(first,ShipCouplerBlockEntity.class).getFirst();
                var b=SubLevelBlockEntityCollector.getBlockEntities(second,ShipCouplerBlockEntity.class).getFirst();
                // Align the freely falling fixtures before requesting a native coupler.
                var firstHandleBefore=RigidBodyHandle.of(first);
                var secondHandleBefore=RigidBodyHandle.of(second);
                firstHandleBefore.addLinearAndAngularVelocity(firstHandleBefore.getLinearVelocity(new Vector3d()).negate(),
                        firstHandleBefore.getAngularVelocity(new Vector3d()).negate());
                secondHandleBefore.addLinearAndAngularVelocity(secondHandleBefore.getLinearVelocity(new Vector3d()).negate(),
                        secondHandleBefore.getAngularVelocity(new Vector3d()).negate());
                second.logicalPose().orientation().set(first.logicalPose().orientation());
                var tip=ShipCouplerBlockEntity.class.getDeclaredMethod("worldTip");tip.setAccessible(true);
                Vec3 firstTip=(Vec3)tip.invoke(a),secondTip=(Vec3)tip.invoke(b);
                Vec3 offset=firstTip.subtract(secondTip);
                second.logicalPose().position().add(offset.x,offset.y,offset.z);
                secondHandleBefore.teleport(second.logicalPose().position(),second.logicalPose().orientation());
                second.updateLastPose();second.updateBoundingBox();
                a.requestAttach();b.requestAttach();
                if(!a.isCoupled()){
                    var owner=a.endpointKey().compareTo(b.endpointKey())<0?a:b;
                    var partner=owner==a?b:a;
                    var method=ShipCouplerBlockEntity.class.getDeclaredMethod("establishPair",ShipCouplerBlockEntity.class);
                    method.setAccessible(true);
                    ctx.assertTrue((boolean)method.invoke(owner,partner),"Native yaw coupler did not connect: requested="+owner.isCouplingRequested()+"/"+partner.isCouplingRequested()+", compatible="+owner.canPairWith(partner)+", tips="+tip.invoke(owner)+"/"+tip.invoke(partner));
                }
                ctx.assertTrue(a.isCoupled()&&b.isCoupled(),"Native coupled link is unavailable");
                var topology=SableAssemblyTopologyApi.discover(first);
                var dynamics=SableAssemblyDynamicsApi.sample(topology);
                ctx.assertTrue(topology.carriagePartitions().size()==2&&dynamics.massAvailable(),"Coupled inertia snapshot is unavailable");
                var firstMass=SableAssemblyDynamicsApi.aggregate(dynamics,List.of(first.getUniqueId()));
                var secondMass=SableAssemblyDynamicsApi.aggregate(dynamics,List.of(second.getUniqueId()));
                RigidBodyHandle firstHandle=RigidBodyHandle.of(first),secondHandle=RigidBodyHandle.of(second);
                firstHandle.addLinearAndAngularVelocity(new Vector3d(),firstHandle.getAngularVelocity(new Vector3d()).negate());
                secondHandle.addLinearAndAngularVelocity(new Vector3d(),new Vector3d(0,2,0).sub(secondHandle.getAngularVelocity(new Vector3d())));
                Vector3d firstLocal=new Vector3d(0,0,0),secondLocal=new Vector3d(0,2,0);
                first.logicalPose().orientation().transformInverse(firstLocal);first.logicalPose().orientation().transformInverse(secondLocal);
                var firstCarriage=new ScmArticulatedFlightControl.Carriage(first.getUniqueId(),true,firstMass.mass(),firstMass.inertia(),
                        firstMass.centerOfMass(),Vec3.ZERO,new Vec3(firstLocal.x,firstLocal.y,firstLocal.z),new Vec3(0,1,0),capacity());
                var secondCarriage=new ScmArticulatedFlightControl.Carriage(second.getUniqueId(),false,secondMass.mass(),secondMass.inertia(),
                        secondMass.centerOfMass(),Vec3.ZERO,new Vec3(secondLocal.x,secondLocal.y,secondLocal.z),new Vec3(0,1,0),capacity());
                var request=ScmArticulatedFlightControl.requests(List.of(firstCarriage,secondCarriage),Vec3.ZERO,Vec3.ZERO,
                        new Vec3(0,1,0),0.05,0.1,0.02);
                ctx.assertTrue(request.get(0).torque().dot(secondCarriage.angularVelocity())>0,"Lead inertia got no opposing yaw reaction");
                ctx.assertTrue(request.get(1).torque().dot(secondCarriage.angularVelocity())<0,"Follower inertia got no counter torque");
                double initial=secondHandle.getAngularVelocity(new Vector3d()).sub(firstHandle.getAngularVelocity(new Vector3d())).length();
                for(int i=0;i<2;i++){
                    ServerSubLevel body=i==0?first:second;
                    Vector3d impulse=new Vector3d(request.get(i).torque().x,request.get(i).torque().y,request.get(i).torque().z).mul(0.05);
                    first.logicalPose().orientation().transform(impulse);
                    RigidBodyHandle.of(body).applyAngularImpulse(impulse);
                }
                double next=secondHandle.getAngularVelocity(new Vector3d()).sub(firstHandle.getAngularVelocity(new Vector3d())).length();
                ctx.assertTrue(Double.isFinite(next)&&next<initial,"Native tensor reaction increased joint spin");
                a.requestDetach();b.requestDetach();
                ctx.succeed();
            }catch(ReflectiveOperationException err){throw new AssertionError(err);}
            finally{
                lease.close();
                SableSubLevelLifecycleApi.remove(level,List.of(first,second).stream().filter(body->dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId())==body).toList());
            }
        });
    }

    private static List<ScmPrecisionAllocator.Unit> capacity(){
        List<ScmPrecisionAllocator.Unit> units=new ArrayList<>();
        for(Vec3 axis:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1))){
            units.add(new ScmPrecisionAllocator.Unit(axis.scale(100000),Vec3.ZERO,true));
            units.add(new ScmPrecisionAllocator.Unit(Vec3.ZERO,axis.scale(100000),true));
        }
        return units;
    }
}
