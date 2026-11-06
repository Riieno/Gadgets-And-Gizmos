package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedContainerRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// Inspect container cargo through the shared painted container API
public final class Manifest extends PaintedContainerRenderer{
    public static final Skin SKIN = new Skin(texture("contents"), texture("storage"), texture("network"));

    public Manifest(){ super(SKIN); }

    @Override protected ItemStack energyIcon(TabletAppClientContext ctx){ return new ItemStack(CTItems.WORKER_ENERGY_BATTERY.get()); }

    @Override
    protected void renderBody(TabletAppClientContext ctx, State state){
        if(!ctx.data().getBoolean("Attached")){
            text(ctx, LocalizedText.read(ctx.data(), "Help").getString(), 16, 42, 288, 35, .8F, TEXT, true);
            button(ctx, state, 83, 90, 154, LocalizedText.get("createthrusters.text.inspect_looked_at_container"), true, () -> ctx.actions().send("inspect", ""));
        }else super.renderBody(ctx, state);
        if(ctx.data().getBoolean("Attached") || ctx.data().getBoolean("Bound"))
            button(ctx, state, 238, 160, 74, LocalizedText.get("createthrusters.text.stop_inspecting"), true, () -> ctx.actions().send("detach", ""));
    }

    private static ResourceLocation texture(String name){
        return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "textures/gui/apps/screens/manifest_" + name + ".png");
    }
}
