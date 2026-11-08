package com.rieno.gadgetsandgizmos.content;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationDestinations;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Exercise saved destinations through the real tablet database and SCM command adapter
@GameTestHolder("createthrusters_navigation_speed")
@PrefixGameTestTemplate(false)
public final class AutoDestinationGameTests{
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedDestinationsPersistAndIssueFullPercentageNavigation(GameTestHelper helper){
        var ctx = context(helper);
        var app = DiagnosticTabletData.appId("gg_auto");
        TabletStorageApi.storage().updateApp(ctx.sourceTabletId(), app, data -> { data.putString("Unrelated", "retained"); return data; });
        helper.assertTrue(DiagnosticTabletAutoApp.editDestination(ctx, "destination_save", "Harbor | East|12.5, 80, -200").success(), "Saving failed");
        var data = TabletStorageApi.storage().app(ctx.sourceTabletId(), app);
        var entry = NavigationDestinations.fromTag(data).entries().getFirst();
        helper.assertTrue(entry.name().equals("Harbor | East") && data.getString("Unrelated").equals("retained"), "Saving lost the name or other app data");
        var ctrl = new CapturingController();
        helper.assertTrue(DiagnosticTabletAutoApp.navigateDestination(ctx, ctrl, entry.id().toString()).success(), "Saved navigation failed");
        helper.assertTrue(ctrl.values.get("x") == 12.5 && ctrl.values.get("y") == 80 && ctrl.values.get("z") == -200
                        && ctrl.values.get("speed") == 1 && ctrl.values.get("speed_fraction") == 1 && ctrl.values.get("avoid_collisions") == 1,
                "Saved navigation changed its coordinates, speed percentage or collision policy");
        helper.assertTrue(DiagnosticTabletAutoApp.editDestination(ctx, "destination_save", "Harbor | East|24, 100, -300").success(), "Updating failed");
        var updated = NavigationDestinations.fromTag(TabletStorageApi.storage().app(ctx.sourceTabletId(), app));
        helper.assertTrue(updated.entries().size() == 1 && updated.entries().getFirst().id().equals(entry.id())
                && updated.entries().getFirst().position().x == 24, "Updating duplicated the destination");
        helper.assertTrue(DiagnosticTabletAutoApp.editDestination(ctx, "destination_remove", entry.id().toString()).success(), "Removing failed");
        helper.assertTrue(!DiagnosticTabletAutoApp.navigateDestination(ctx, ctrl, entry.id().toString()).success(), "A deleted destination remained usable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void invalidAndOtherDimensionDestinationsNeverReachTheShip(GameTestHelper helper){
        var ctx = context(helper);
        var app = DiagnosticTabletData.appId("gg_auto");
        for(String input : List.of("No coordinates", "Home|NaN, 64, 0", " |1, 2, 3", "Home|1, 2, 3, 4")){
            helper.assertTrue(!DiagnosticTabletAutoApp.editDestination(ctx, "destination_save", input).success(), "Invalid destination accepted: " + input);
        }
        var foreign = new NavigationDestinations(List.of()).save("Nether", ResourceLocation.withDefaultNamespace("the_nether"), net.minecraft.world.phys.Vec3.ZERO);
        TabletStorageApi.storage().updateApp(ctx.sourceTabletId(), app, data -> foreign.toTag());
        var ctrl = new CapturingController();
        helper.assertTrue(!DiagnosticTabletAutoApp.navigateDestination(ctx, ctrl, foreign.entries().getFirst().id().toString()).success()
                && ctrl.values == null, "Navigation crossed dimensions");
        helper.succeed();
    }

    private static TabletActionContext context(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AutoDestination"));
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        return new TabletActionContext(player, tablet, null, null, false, DiagnosticTabletData.ensureTabletId(tablet), null, null);
    }

    private static final class CapturingController extends AdvancedContraptionControllerBlockEntity{
        private Map<String, Double> values;
        private CapturingController(){ super(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()); }
        @Override public boolean executeShipControlGraphCommand(String id, String type, Map<String, Double> values, Map<String, String> text){
            this.values = Map.copyOf(values);
            return true;
        }
    }
}
