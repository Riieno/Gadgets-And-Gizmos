package com.rieno.gadgetsandgizmos.content.tablet;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveStore;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class DigisableStoreSelectedGameTests{
    private DigisableStoreSelectedGameTests(){}

    @GameTest(template = "empty_container",batch = "digisable_remote_store",timeoutTicks = 200)
    public static void remoteStorageRetainsRangePermissionsAndArchiveIntegrity(GameTestHelper helper) throws Exception{
        var level = helper.getLevel();var container = SubLevelContainer.getContainer(level);
        boolean[] creative = {false}, operator = {false};
        var player = new FakePlayer(level,new GameProfile(UUID.randomUUID(),"RemoteStoreTest")){
            @Override public boolean isCreative(){ return creative[0]; }
            @Override public boolean hasPermissions(int permission){ return operator[0] && permission <= 2; }
            @Override public void displayClientMessage(Component message,boolean overlay){}
        };
        var origin = helper.absolutePos(new BlockPos(2,3,2)).getCenter();player.setPos(origin);
        var ctx = new TabletActionContext(player,ItemStack.EMPTY,null,null,false,UUID.randomUUID(),null,null);
        var body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        var store = SubLevelArchiveStore.forServer(level.getServer());
        try{
            var plot = body.getPlot();plot.newEmptyChunk(plot.getCenterChunk());
            var chunk = plot.getLoadedChunks().iterator().next().getChunk();
            chunk.setBlockState(plot.getCenterBlock(),Blocks.GOLD_BLOCK.defaultBlockState(),false);
            body.logicalPose().position().set(origin.x + TabletAppsServerConfig.STORE_RANGE.get() + 32,origin.y,origin.z);
            body.logicalPose().rotationPoint().set(plot.getCenterBlock().getX(),plot.getCenterBlock().getY(),plot.getCenterBlock().getZ());
            body.updateLastPose();container.physicsSystem().getPipeline().onStatsChanged(body);
            for(boolean creativeMode : new boolean[]{false,true}){
                for(boolean op : new boolean[]{false,true}){
                    creative[0] = creativeMode;operator[0] = op;
                    var snapshot = DigisableApp.snapshot(ctx);
                    helper.assertTrue(snapshot.getBoolean("SublevelsAllowed") == (creativeMode || op),"Sublevel tab access did not follow creative OR operator permissions");
                    helper.assertTrue(snapshot.getBoolean("RemoteStoreAllowed") == (creativeMode || op),"Remote storage access did not follow creative OR operator permissions");
                    if(!creativeMode && !op) helper.assertTrue(snapshot.getList("Sublevels",10).isEmpty(),"Normal survival player received the sublevel catalogue");
                }
            }
            creative[0] = false;operator[0] = false;
            for(String tab : List.of("sublevels","archives")){
                try{
                    DigisableApp.execute(ctx,action(tab,"store_selected",body.getUniqueId()));
                    helper.fail("A survival player stored a remote sublevel");
                }catch(IllegalArgumentException expected){
                    helper.assertTrue(expected.getMessage().contains("creative mode or operator"),"Remote storage failed for the wrong reason");
                }
            }
            helper.assertTrue(!body.isRemoved(),"Rejected remote storage removed the assembly");
            creative[0] = true;
            try{
                SubLevelArchiveApi.store(player,level,body.getUniqueId(),TabletAppsServerConfig.archiveLimits());
                helper.fail("Store from world lost its original distance limit");
            }catch(IllegalArgumentException expected){
                helper.assertTrue(expected.getMessage().contains("storage range"),"Local storage failed for the wrong reason");
            }
            var result = DigisableApp.execute(ctx,action("sublevels","store_selected",body.getUniqueId()));
            helper.assertTrue("Assembly stored".equals(result),"Remote storage did not report success");
            helper.assertTrue(body.isRemoved() && container.getSubLevel(body.getUniqueId()) == null,"Remote storage left its original assembly live");
            helper.assertTrue(player.position().distanceToSqr(origin) < 0.001,"Remote storage moved the player");
            var archives = store.catalogue(player.getUUID(),false);
            helper.assertTrue(archives.size() == 1,"Remote storage did not create exactly one archive");
            UUID archiveId = archives.getFirst().getUUID("Id");var archive = store.entry(archiveId);
            helper.assertTrue("stored".equals(archive.getString("State")),"Archive transaction did not complete");
            helper.assertTrue(archive.getUUID("Root").equals(body.getUniqueId()) && archive.getUUID("Owner").equals(player.getUUID()),"Archive lost its root or owner identity");
            helper.assertTrue(archive.getList("Bodies",10).size() == 1 && !archive.getList("Preview",10).isEmpty(),"Archive lost its body or block preview");
            helper.succeed();
        }finally{
            if(!body.isRemoved()) SableSubLevelLifecycleApi.remove(level,List.of(body));
            for(var row : store.catalogue(player.getUUID(),false)){
                var archive = store.entry(row.getUUID("Id"));store.consume(archive);
                for(var raw : archive.getList("Bodies",10)){
                    var plot = ((net.minecraft.nbt.CompoundTag)raw).getCompound("plot");
                    container.getOccupancy().clear(container.getIndex(plot.getInt("plot_x"),plot.getInt("plot_z")));
                }
            }
        }
    }

    private static TabletAction action(String tab,String action,UUID id){
        return new TabletAction(PaidTabletApps.DIGISABLE.id(),tab,action,Map.of("value",id.toString()));
    }
}
