package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Verify painted card saves preserve frequencies and runtime state across edits and reloads
@GameTestHolder("createthrusters_physics_regressions")
@PrefixGameTestTemplate(false)
public final class DiagnosticTabletRedstoneGameTests{
    @GameTest(template = "empty", batch = "tablet_redstone")
    public static void paintedCardsPersistWithoutChangingTheirControlState(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CardEditor"));
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        var id = DiagnosticTabletData.ensureTabletId(tablet);
        var ctx = new TabletActionContext(player, tablet, null, null, false, id, null, null);
        var draft = draft();
        helper.assertTrue(action(ctx, "card_save", draft.toString()).success(), "The card editor could not create a channel");
        var first = DiagnosticTabletRedstoneLinkApp.channels(player.server, id).getFirst();
        var snapshot = DiagnosticTabletRedstoneLinkApp.snapshot(player, id, List.of(first));
        var style = snapshot.getList("Channels", Tag.TAG_COMPOUND).getCompound(0).getCompound("Card");
        helper.assertTrue(ControlCardStyle.fromTag(style).size() == ControlCardStyle.Size.WIDE && style.getInt("Color") == 0x553344,
                "The card size or color did not survive persistence");
        helper.assertTrue(action(ctx, "channel_toggle", first.id().toString()).success(), "The control could not toggle");
        draft.putUUID("Id", first.id()); draft.putString("Label", "Renamed light");
        draft.getCompound("Card").putString("Size", "tall");
        helper.assertTrue(action(ctx, "card_save", draft.toString()).success(), "An existing card could not be edited");
        var updated = DiagnosticTabletRedstoneLinkApp.channels(player.server, id).getFirst();
        helper.assertTrue(updated.id().equals(first.id()) && updated.active() && updated.outputStrength() == 15
                && updated.firstColor() == 0xAA0000 && updated.secondColor() == 0x0000AA, "Editing changed identity, frequencies or active output");
        var duplicate = draft.copy(); duplicate.remove("Id");
        helper.assertTrue(!action(ctx, "card_save", duplicate.toString()).success()
                && DiagnosticTabletRedstoneLinkApp.channels(player.server, id).size() == 1, "A duplicate frequency pair was accepted");
        helper.assertTrue(action(ctx, "channel_remove", first.id().toString()).success(), "Card deletion failed");
        helper.assertTrue(DiagnosticTabletAppStorage.data(player.server, id, DiagnosticTabletRedstoneLinkApp.APP_ID).getCompound("Cards").isEmpty(),
                "Deleting a channel retained its presentation data");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_redstone")
    public static void invalidCardDraftsLeaveTheStoredChannelsAlone(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InvalidCard"));
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        var id = DiagnosticTabletData.ensureTabletId(tablet);
        var ctx = new TabletActionContext(player, tablet, null, null, false, id, null, null);
        var draft = draft(); draft.putString("FirstItem", "missing:not_registered");
        helper.assertTrue(!action(ctx, "card_save", draft.toString()).success(), "An unknown frequency item was accepted");
        draft = draft(); draft.putInt("Strength", 16);
        helper.assertTrue(!action(ctx, "card_save", draft.toString()).success(), "An invalid signal strength was accepted");
        helper.assertTrue(!action(ctx, "card_save", "{broken").success(), "A malformed draft was accepted");
        helper.assertTrue(DiagnosticTabletRedstoneLinkApp.channels(player.server, id).isEmpty(), "Invalid edits changed storage");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_redstone")
    public static void emptyAndSingleFrequenciesAndMediaCardsPersist(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OptionalFrequency"));
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        var id = DiagnosticTabletData.ensureTabletId(tablet);
        var ctx = new TabletActionContext(player, tablet, null, null, false, id, null, null);
        for(int idx = 0; idx < 5; idx++){
            var draft = draft();
            draft.putString("FirstItem", idx == 0 ? "minecraft:diamond" : "minecraft:air");
            draft.putString("SecondItem", idx == 1 ? "minecraft:emerald" : "minecraft:air");
            if(idx == 4) draft.putString("Mode", "media");
            helper.assertTrue(action(ctx, "card_save", draft.toString()).success(), "An empty/single frequency or Media card was rejected: " + idx);
        }
        var channels = DiagnosticTabletRedstoneLinkApp.channels(player.server, id);
        helper.assertTrue(channels.size() == 5 && channels.getLast().controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.MEDIA
                && channels.getLast().outputStrength() == 0, "Media was not persisted as a separate non-transmitting card type");
        helper.assertTrue(channels.get(0).secondColor() == -1 && channels.get(1).firstColor() == -1, "Empty frequency colors were not normalized");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_redstone")
    public static void emptyAndSingleFrequencyOutputsReachTheCreateNetwork(GameTestHelper helper){
        var level = helper.getLevel();
        var pos = new net.minecraft.core.BlockPos(2, 2, 2);
        helper.setBlock(pos, com.rieno.gadgetsandgizmos.registry.CTBlocks.DIAGNOSTIC_TABLET.get().defaultBlockState());
        var tablet = (DiagnosticTabletBlockEntity)helper.getBlockEntity(pos);
        tablet.setState(tablet.state().withTabletId(UUID.randomUUID()));
        var channels = new java.util.ArrayList<DiagnosticTabletData.RedstoneLinkChannel>();
        var probes = new java.util.ArrayList<com.simibubi.create.content.redstone.link.IRedstoneLinkable>();
        var air = ResourceLocation.parse("minecraft:air");
        for(int idx = 0; idx < 3; idx++){
            var channel = new DiagnosticTabletData.RedstoneLinkChannel(UUID.randomUUID(), "Optional frequency", idx == 1 ? ResourceLocation.parse("minecraft:diamond") : air, -1,
                    idx == 2 ? ResourceLocation.parse("minecraft:emerald") : air, -1, DiagnosticTabletData.RedstoneLinkControlMode.TOGGLE, 7, true);
            channels.add(channel);
            probes.add(new com.simibubi.create.content.redstone.link.IRedstoneLinkable(){
                public int getTransmittedStrength(){ return 0; }
                public void setReceivedStrength(int strength){}
                public boolean isListening(){ return true; }
                public boolean isAlive(){ return true; }
                public net.minecraft.core.BlockPos getLocation(){ return tablet.getBlockPos(); }
                public net.createmod.catnip.data.Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){
                    return net.createmod.catnip.data.Couple.create(
                            com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(DiagnosticTabletRedstoneLinkApp.frequencyStack(channel.firstItem(), -1)),
                            com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(DiagnosticTabletRedstoneLinkApp.frequencyStack(channel.secondItem(), -1)));
                }
            });
        }
        var network = com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER;
        try{
            probes.forEach(probe -> network.addToNetwork(level, probe));
            DiagnosticTabletRedstoneLinkRuntime.syncPlaced(null, tablet, channels);
            for(var probe : probes) helper.assertTrue(network.getNetworkOf(level, probe).stream().anyMatch(source -> source.getTransmittedStrength() == 7),
                    "The runtime discarded an empty or single-item frequency pair");
            DiagnosticTabletRedstoneLinkRuntime.syncPlaced(null, tablet, List.of(channels.getFirst().withControlMode(DiagnosticTabletData.RedstoneLinkControlMode.MEDIA)));
            for(var probe : probes) helper.assertTrue(network.getNetworkOf(level, probe).stream().noneMatch(source -> source.getTransmittedStrength() > 0), "A Media card retained a redstone output");
        }finally{
            DiagnosticTabletRedstoneLinkRuntime.syncPlaced(null, tablet, List.of());
            probes.forEach(probe -> network.removeFromNetwork(level, probe));
        }
        helper.succeed();
    }

    private static com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler.Result action(TabletActionContext ctx, String action, String val){
        return DiagnosticTabletRedstoneLinkApp.execute(ctx, new TabletAction(DiagnosticTabletRedstoneLinkApp.APP_ID, "devices", action, Map.of("value", val)));
    }

    @GameTest(template = "empty", batch = "tablet_redstone")
    public static void clocksAndCardOrderPersistWithValidation(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ClockOrder"));
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        var id = DiagnosticTabletData.ensureTabletId(tablet);
        var ctx = new TabletActionContext(player, tablet, null, null, false, id, null, null);
        for(String mode : List.of("toggle", "media", "clock")){
            var draft = draft(); draft.putString("Mode", mode);
            var style = new ControlCardStyle(ControlCardStyle.Size.WIDE, 0x336699, ResourceLocation.parse("minecraft:clock"),
                    new com.rieno.gadgetsandgizmos.lib.tablet.ClockCardSettings(true, true, "Europe/London"));
            draft.put("Card", style.toTag());
            helper.assertTrue(action(ctx, "card_save", draft.toString()).success(), "A clock or media card was rejected");
        }
        var cards = DiagnosticTabletRedstoneLinkApp.channels(player.server, id);
        var order = List.of(cards.get(2).id(), cards.get(0).id(), cards.get(1).id());
        helper.assertTrue(action(ctx, "card_reorder", String.join("|", order.stream().map(UUID::toString).toList())).success(), "Card reordering failed");
        var reloaded = DiagnosticTabletRedstoneLinkApp.channels(player.server, id);
        helper.assertTrue(reloaded.stream().map(DiagnosticTabletData.RedstoneLinkChannel::id).toList().equals(order), "Card order did not persist");
        var snapshot = DiagnosticTabletRedstoneLinkApp.snapshot(player, id, reloaded).getList("Channels", Tag.TAG_COMPOUND);
        var clock = ControlCardStyle.fromTag(snapshot.getCompound(0).getCompound("Card")).clock();
        helper.assertTrue(clock.digital() && clock.realTime() && clock.timezone().equals("Europe/London")
                && reloaded.getFirst().outputStrength() == 0, "Clock options were lost or emitted redstone");
        helper.assertTrue(!action(ctx, "card_reorder", order.getFirst() + "|" + order.getFirst() + "|" + order.getLast()).success(), "Duplicate IDs were allowed in a reorder");
        helper.assertTrue(DiagnosticTabletRedstoneLinkApp.channels(player.server, id).stream().map(DiagnosticTabletData.RedstoneLinkChannel::id).toList().equals(order),
                "An invalid reorder changed storage");
        helper.succeed();
    }
    private static CompoundTag draft(){
        var tag = new CompoundTag();
        tag.putString("Label", "Lights"); tag.putString("Mode", "toggle"); tag.putInt("Strength", 15);
        tag.putString("FirstItem", "minecraft:diamond"); tag.putInt("FirstColor", 0xAA0000);
        tag.putString("SecondItem", "minecraft:emerald"); tag.putInt("SecondColor", 0x0000AA);
        tag.put("Card", new ControlCardStyle(ControlCardStyle.Size.WIDE, 0x553344, ResourceLocation.parse("minecraft:torch")).toTag());
        return tag;
    }
}
