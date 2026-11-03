package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.item.RegisteredItemCatalog;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Check registry ghost choices and the linker's separate graph target modes
@GameTestHolder("createthrusters_remote_controls")
@PrefixGameTestTemplate(false)
public final class GraphPickerGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create and edit a survival player's frequencies without owning the referenced items
    @GameTest(template = "empty")
    public static void registryFrequenciesDoNotRequireInventory(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RegistryPicker"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        helper.assertTrue(RegisteredItemCatalog.items().containsAll(List.of(Items.DIAMOND, Items.STONE,
                CTItems.DIAGNOSTIC_TABLET.get())), "The registry catalog omitted an item, block or addon item");
        helper.assertTrue(!RegisteredItemCatalog.contains(null)
                && !RegisteredItemCatalog.contains(ResourceLocation.parse("minecraft:air"))
                && !RegisteredItemCatalog.contains(ResourceLocation.parse("missing:frequency")),
                "The catalog accepted an empty or unregistered frequency");
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        UUID tabletId = DiagnosticTabletData.ensureTabletId(tablet);
        var ctx = new TabletActionContext(player, tablet, null, null, false, tabletId, null, null);
        var create = DiagnosticTabletRedstoneLinkApp.execute(ctx, new TabletAction(
                DiagnosticTabletRedstoneLinkApp.APP_ID, "", "channel_create",
                Map.of("value", "minecraft:diamond|-1|minecraft:stone|-1|button|15|Registry pair")));
        helper.assertTrue(create.success(), "A registered ghost frequency was rejected: " + create.message().getString());
        var channels = DiagnosticTabletRedstoneLinkApp.channels(player.server, tabletId);
        helper.assertTrue(channels.size() == 1, "The registry frequency pair was not persisted");
        UUID channelId = channels.getFirst().id();
        var update = DiagnosticTabletRedstoneLinkApp.execute(ctx, new TabletAction(
                DiagnosticTabletRedstoneLinkApp.APP_ID, "", "channel_update",
                Map.of("value", channelId + "|minecraft:gold_ingot|-1|create:andesite_alloy|-1|button|7|Updated pair")));
        helper.assertTrue(update.success(), "An unowned mod frequency was rejected: " + update.message().getString());
        var saved = DiagnosticTabletRedstoneLinkApp.channels(player.server, tabletId).getFirst();
        helper.assertTrue(saved.firstItem().equals(ResourceLocation.parse("minecraft:gold_ingot"))
                && saved.secondItem().equals(ResourceLocation.parse("create:andesite_alloy")),
                "The new registry frequencies were not saved");
        var invalid = DiagnosticTabletRedstoneLinkApp.execute(ctx, new TabletAction(
                DiagnosticTabletRedstoneLinkApp.APP_ID, "", "channel_update",
                Map.of("value", channelId + "|minecraft:air|-1|missing:frequency|-1|button|7|Invalid pair")));
        helper.assertTrue(!invalid.success(), "An invalid frequency pair replaced a saved channel");
        helper.assertTrue(DiagnosticTabletRedstoneLinkApp.channels(player.server, tabletId).getFirst().equals(saved),
                "Rejecting an invalid frequency changed the saved channel");
        helper.assertTrue(player.getInventory().isEmpty(), "Ghost frequency selection created physical items");
        helper.succeed();
    }

    // Keep SCM endpoint discovery available without including it in the main graph's input and output targets
    @GameTest(template = "empty")
    public static void scmTargetsUseSeparateDiscovery(GameTestHelper helper){
        var targets = List.of(
                new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(1, 1, 1), null, "minecraft:chest", "Input",
                        ContraptionNetworkLinkerData.LinkMode.INPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of()),
                new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(2, 1, 1), null, "minecraft:furnace", "Output",
                        ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of()),
                new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(3, 1, 1), null, "minecraft:barrel", "Worker container",
                        ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of()));
        var main = ContraptionNetworkLinkerData.toDiscoveryNodes(targets);
        var worker = ContraptionNetworkLinkerData.scmDiscoveryNodes(targets);
        helper.assertTrue(main.size() == 2 && main.stream().noneMatch(target -> "minecraft:barrel".equals(target.blockId())),
                "SCM targets leaked into main graph discovery");
        helper.assertTrue(worker.size() == 1 && "minecraft:barrel".equals(worker.getFirst().blockId()),
                "The worker container target was lost");
        helper.succeed();
    }
}
