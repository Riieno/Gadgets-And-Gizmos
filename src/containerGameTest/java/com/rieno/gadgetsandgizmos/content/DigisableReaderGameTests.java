package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveStore;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Exercise world deletion through the same Reader item interaction used by a player
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class DigisableReaderGameTests{
    // Keep the framework entry point static
    private DigisableReaderGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Delete the pointed assembly while retaining other ships, ordinary world blocks and claimed ships
    @GameTest(template = "empty_container")
    public static void readerDeletesOnlyTheSelectedUnclaimedShip(GameTestHelper ctx) throws Exception{
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        Map<String, Boolean> blocks = new LinkedHashMap<>();
        CTFeatureToggles.blockDefaults().keySet().forEach(id -> blocks.put(id, CTFeatureToggles.isBlockEnabled(id)));
        Map<String, Boolean> items = new LinkedHashMap<>();
        CTFeatureToggles.itemDefaults().keySet().forEach(id -> items.put(id, CTFeatureToggles.isItemEnabled(id)));
        Map<String, Boolean> entities = new LinkedHashMap<>();
        CTFeatureToggles.entityDefaults().keySet().forEach(id -> entities.put(id, CTFeatureToggles.isEntityEnabled(id)));
        Map<String, Boolean> enabled = new LinkedHashMap<>(blocks);
        enabled.put("diagnostic_tablet", true);
        CTFeatureToggles.applyServerOverrides(enabled, items, entities);
        List<String> messages = new ArrayList<>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DigisableTest")){
            // Give this fixture the same permission required by the existing delete action
            @Override public boolean hasPermissions(int permission){ return permission <= 2; }
            // Retain action failures so an unsuccessful deletion identifies its rejection reason
            @Override public void displayClientMessage(Component message, boolean actionBar){ messages.add(message.getString()); }
        };
        DiagnosticTabletApps.register();
        ItemStack tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        UUID tabletId = DiagnosticTabletData.ensureTabletId(tablet);
        DiagnosticTabletData.write(tablet, DiagnosticTabletData.read(tablet).withApp(PaidTabletApps.DIGISABLE.id(), "sublevels")
                .withMode(TabletInteractionMode.READER, "delete"));
        var database = DiagnosticTabletDatabase.forServer(level.getServer());
        database.setInstalled(tabletId, PaidTabletApps.DIGISABLE.id(), true);
        database.grant(TabletAppPurchaseScope.PLAYER, player.getUUID(), tabletId, PaidTabletApps.DIGISABLE.id());
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        List<ServerSubLevel> bodies = new ArrayList<>();
        try{
            BlockPos worldPos = ctx.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(worldPos.getCenter());
            level.setBlock(worldPos, Blocks.STONE.defaultBlockState(), 2);
            tablet.getItem().useOn(target(player, worldPos));
            ctx.assertTrue(level.getBlockState(worldPos).is(Blocks.STONE), "Reader deleted an ordinary world block");
            ctx.assertTrue(DiagnosticTabletData.read(tablet).mode() == TabletInteractionMode.READER,
                    "An invalid world target ended Reader selection");
            for(int idx = 0; idx < 2; idx++){
                var body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
                bodies.add(body);
                var plot = body.getPlot();
                plot.newEmptyChunk(plot.getCenterChunk());
                var chunk = plot.getLoadedChunks().iterator().next().getChunk();
                chunk.setBlockState(plot.getCenterBlock(), Blocks.STONE.defaultBlockState(), false);
                body.logicalPose().position().set(worldPos.getX() + idx * 4, worldPos.getY(), worldPos.getZ());
                body.logicalPose().rotationPoint().set(plot.getCenterBlock().getX(), plot.getCenterBlock().getY(), plot.getCenterBlock().getZ());
                body.updateLastPose();
                container.physicsSystem().getPipeline().onStatsChanged(body);
            }
            var selected = bodies.getFirst();
            var permissions = ShipPermissionManager.get(level.getServer());
            permissions.claim(selected.getUniqueId(), player.getUUID());
            tablet.getItem().useOn(target(player, selected.getPlot().getCenterBlock()));
            ctx.assertTrue(!selected.isRemoved(), "Reader ignored the existing claimed-ship restriction");
            ctx.assertTrue(DiagnosticTabletData.read(tablet).mode() == TabletInteractionMode.READER,
                    "A rejected deletion ended Reader selection");
            permissions.unclaim(selected.getUniqueId());
            player.getPersistentData().remove("createthrusters:digisable_operation");
            tablet.getItem().useOn(target(player, selected.getPlot().getCenterBlock()));
            ctx.assertTrue(selected.isRemoved() && container.getSubLevel(selected.getUniqueId()) == null,
                    "Reader did not delete the pointed sublevel: " + messages);
            ctx.assertTrue(!bodies.getLast().isRemoved(), "Reader deleted a different sublevel");
            ctx.assertTrue(SubLevelArchiveStore.forServer(level.getServer()).catalogue(player.getUUID(), true).isEmpty(),
                    "Reader stored the selected ship instead of deleting it");
            var state = DiagnosticTabletData.read(tablet);
            ctx.assertTrue(state.mode() == TabletInteractionMode.STANDARD && state.pendingAction().isEmpty(),
                    "Successful deletion did not return the tablet to Standard mode");
            ctx.succeed();
        }finally{
            for(var body : bodies) ShipPermissionManager.get(level.getServer()).unclaim(body.getUniqueId());
            SableSubLevelLifecycleApi.remove(level, bodies.stream().filter(body -> !body.isRemoved()).toList());
            database.revoke(TabletAppPurchaseScope.PLAYER, player.getUUID(), tabletId, PaidTabletApps.DIGISABLE.id());
            CTFeatureToggles.applyServerOverrides(blocks, items, entities);
        }
    }

    // Point the held tablet at the exact block selected by the fixture
    private static UseOnContext target(FakePlayer player, BlockPos pos){
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
    }
}
