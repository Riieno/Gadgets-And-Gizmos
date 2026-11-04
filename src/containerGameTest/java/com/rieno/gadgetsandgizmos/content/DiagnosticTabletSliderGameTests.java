package com.rieno.gadgetsandgizmos.content;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.control.RedstoneLinkReceiver;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

// Verify slider actions reach the selected Create frequency at every analog strength
@GameTestHolder("createthrusters_tablet_sliders")
@PrefixGameTestTemplate(false)
public final class DiagnosticTabletSliderGameTests{
    @GameTest(template = "empty")
    public static void sliderActionsTransmitEveryStrengthAndClearAtZero(GameTestHelper helper){
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, CTBlocks.DIAGNOSTIC_TABLET.get().defaultBlockState());
        var placed = (DiagnosticTabletBlockEntity)helper.getBlockEntity(pos);
        UUID tabletId = UUID.randomUUID();
        placed.setState(placed.state().withTabletId(tabletId));
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SliderValidation"));
        var ctx = new TabletActionContext(player, new ItemStack(CTItems.DIAGNOSTIC_TABLET.get()), null, null,
                true, tabletId, null, placed.getBlockPos());
        var draft = new CompoundTag();
        draft.putString("Label", "Analog output"); draft.putString("Mode", "slider"); draft.putInt("Strength", 0);
        draft.putString("FirstItem", "minecraft:diamond"); draft.putInt("FirstColor", 0x1467AB);
        draft.putString("SecondItem", "minecraft:emerald"); draft.putInt("SecondColor", 0xBC3612);
        helper.assertTrue(action(ctx, "card_save", draft.toString()).success(), "Slider card save failed");
        var channel = DiagnosticTabletRedstoneLinkApp.channels(player.server, tabletId).getFirst();
        try(var receiver = new RedstoneLinkReceiver(placed::getBlockPos);
            var otherFrequency = new RedstoneLinkReceiver(placed::getBlockPos)){
            receiver.bind(helper.getLevel(), DiagnosticTabletRedstoneLinkApp.frequencyStack(channel.firstItem(), channel.firstColor()),
                    DiagnosticTabletRedstoneLinkApp.frequencyStack(channel.secondItem(), channel.secondColor()));
            otherFrequency.bind(helper.getLevel(), DiagnosticTabletRedstoneLinkApp.frequencyStack(channel.firstItem(), channel.firstColor() + 1),
                    DiagnosticTabletRedstoneLinkApp.frequencyStack(channel.secondItem(), channel.secondColor()));
            for(int step = 0; step <= 30; step++){
                int strength = step <= 15 ? step : 30 - step;
                helper.assertTrue(action(ctx, "channel_slider", channel.id() + "|" + strength).success(), "Slider action failed at " + strength);
                helper.assertTrue(receiver.strength() == strength, "Create receiver did not receive strength " + strength + ": " + receiver.strength());
                helper.assertTrue(otherFrequency.strength() == 0, "Slider transmitted on another frequency");
                helper.assertTrue(DiagnosticTabletRedstoneLinkApp.channels(player.server, tabletId).getFirst().strength() == strength,
                        "Slider strength did not persist");
            }
            helper.assertTrue(!action(ctx, "channel_slider", channel.id() + "|16").success(), "Out-of-range slider output was accepted");
            helper.assertTrue(receiver.strength() == 0, "Rejected slider output changed the signal");
        }finally{
            DiagnosticTabletRedstoneLinkRuntime.syncPlaced(null, placed, java.util.List.of());
        }
        helper.succeed();
    }

    private static com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler.Result action(TabletActionContext ctx, String action, String value){
        return DiagnosticTabletRedstoneLinkApp.execute(ctx,
                new TabletAction(DiagnosticTabletRedstoneLinkApp.APP_ID, "home", action, Map.of("value", value)));
    }
}
