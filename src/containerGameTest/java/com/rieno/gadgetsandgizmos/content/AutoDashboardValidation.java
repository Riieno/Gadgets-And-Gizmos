package com.rieno.gadgetsandgizmos.content;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationDestinations;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.GgAuto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Exercise popup hit testing and editable fields in the actual painted dashboard
final class AutoDashboardValidation{
    private final GgAuto renderer = new GgAuto();
    private final TabletAppClientState state = renderer.createScreenState();
    private final CompoundTag data = new CompoundTag();
    private final List<String> actions = new ArrayList<>();
    private TabletAppClientContext ctx;

    static void run(){
        var test = new AutoDashboardValidation();
        try(var surface = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "auto_dashboard_validation"))){
            test.validate(surface);
        }catch(Exception err){ throw new AssertionError("Auto dashboard validation failed", err); }
        finally{
            try{ ((AutoCloseable)test.state).close(); }
            catch(Exception err){ throw new AssertionError(err); }
        }
    }

    private void validate(FramebufferGuiRenderer surface) throws Exception{
        data.putBoolean("Available", true);
        data.putString("Ship", "Dashboard validation");
        data.putString("Facing", "north");
        data.putDouble("X", 100); data.putDouble("Y", 80); data.putDouble("Z", -200);
        data.putDouble("Speed", 12.6); data.putDouble("Fuel", .75);
        data.putLong("EtaSeconds", -1);
        String dashboard = String.format(Locale.ROOT, LocalizedText.get("gadgetsngizmos.text.speed_1f_blocks_s"), Math.round(12.6));
        String stats = String.format(Locale.ROOT, LocalizedText.get("gadgetsngizmos.text.1f_blocks_s"), Math.round(12.6));
        require(dashboard.contains("13") && !dashboard.contains("12.6") && stats.startsWith("13"), "Speed labels were not rounded");
        set(state, "navigation", true);
        render(surface);
        click(100, 70);
        render(surface);
        click(100, 70);
        type("Harbor");
        click(100, 96);
        renderer.keyPressed(ctx, state, 261, 0, 0);
        type("500, 90, -100");
        render(surface);
        screenshot(surface, "destination-editor.png");
        click(100, 134);
        require(actions.contains("destination_save:Harbor|500, 90, -100"), "The Save button lost the form values");
        var saved = new NavigationDestinations(List.of());
        var dimension = Minecraft.getInstance().level.dimension().location();
        for(int idx = 0; idx < 5; idx++) saved = saved.save("Harbor " + idx, dimension, new Vec3(500 + idx, 90, -100));
        data.put("SavedDestinations", saved.toTag());
        render(surface);
        screenshot(surface, "saved-destinations.png");
        click(100, 58);
        require(actions.contains("destination_navigate:" + saved.entries().getFirst().id()), "A saved row did not navigate");
        click(240, 58);
        require(actions.contains("destination_remove:" + saved.entries().getFirst().id()), "The delete button did not remove its row");
        click(120, 134);
        render(surface);
        click(100, 58);
        require(actions.contains("destination_navigate:" + saved.entries().get(3).id()), "Saved destination paging lost its selection");
        click(241, 44);
        render(surface);
        screenshot(surface, "dashboard-speed.png");
    }

    private void render(FramebufferGuiRenderer surface){
        var mc = Minecraft.getInstance();
        var app = TabletAppRegistry.definition(DiagnosticTabletData.appId("gg_auto"));
        var tab = app.tabs().stream().filter(val -> val.id().equals("drive")).findFirst().orElseThrow();
        surface.render(960, 540, 320, 180, graphics -> {
            ctx = new TabletAppClientContext(app, tab, data, graphics, mc.font, 0, 0, 320, 180,
                    new TabletAppClientSurface(0, 0, 320, 180), -1, -1,
                    (action, val) -> actions.add(action + ":" + val), () -> {});
            renderer.render(ctx, state);
        });
    }

    private void click(int x, int y){ require(renderer.mouseClicked(ctx, state, x, y, 0), "Popup click was not handled: " + x + ", " + y); }
    private void type(String text){ for(char ch : text.toCharArray()) require(renderer.chatTyped(ctx, state, ch, 0), "Typing was not handled"); }

    private static void screenshot(FramebufferGuiRenderer surface, String name) throws Exception{
        var field = surface.getClass().getDeclaredField("target"); field.setAccessible(true);
        var directory = Path.of(System.getProperty("gadgetsngizmos.renderingValidationResult")).getParent();
        try(var image = Screenshot.takeScreenshot((RenderTarget)field.get(surface))){ image.writeToFile(directory.resolve(name)); }
    }

    private static void set(Object owner, String name, Object val) throws Exception{
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, val);
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
}
