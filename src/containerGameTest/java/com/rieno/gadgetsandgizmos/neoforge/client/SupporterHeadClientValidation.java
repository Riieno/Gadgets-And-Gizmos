package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariant;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.content.SupporterHeads;
import com.rieno.gadgetsandgizmos.registry.CTDataComponents;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Check actual creative and recipe-viewer indices using only the packaged supporter heads
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class SupporterHeadClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound the isolated client run and allow each viewer to finish indexing its world
    private static boolean installed, creativeChecked, complete;
    private static long started;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wait for the live indices and validate each supporter's display name and ID
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        String output = System.getProperty("gadgetsngizmos.supporterHeadValidation");
        if(output == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        try{
            if(!installed){
                installed = true; started = System.nanoTime();
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                if(ModList.get().isLoaded("emi")) EmiValidation.prepare();
            }
            if(System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Supporter head validation timed out");
            if(mc.level == null || mc.player == null || mc.getConnection() == null) return;
            mc.options.pauseOnLostFocus = false;
            if(!creativeChecked){
                creative(mc);
                creativeChecked = true;
            }
            boolean ready = ModList.get().isLoaded("emi") ? EmiValidation.check() : JeiValidation.check();
            if(!ready) return;
            finish(output, "PASSED " + PlayerMannequinVariants.all().size()
                    + " supporter variants, @gadgets searches, clean tooltips, creator labels, display-name and ID searches, creative search and distinct identities\n");
        }catch(Throwable err){
            err.printStackTrace();
            finish(output, "FAILED " + err);
        }
    }

    // Check Minecraft's real creative search tree using the same stacks shown in its search tab
    private static void creative(Minecraft mc){
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
        var stacks = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
        mc.getConnection().searchTrees().updateCreativeTooltips(mc.level.registryAccess(), stacks);
        var search = mc.getConnection().searchTrees().creativeNameSearch();
        for(var variant : PlayerMannequinVariants.all()){
            require(contains(stacks, variant), "Creative inventory omitted " + variant.id());
            require(contains(search.search(variant.displayName().getString().toLowerCase(java.util.Locale.ROOT)), variant),
                    "Creative name search omitted " + variant.id());
            ItemStack stack = SupporterHeads.createStack(variant);
            require("createthrusters".equals(stack.getItem().getCreatorModId(stack)), "Supporter head has the wrong creator mod");
            require(stack.is(Items.PLAYER_HEAD), "Mod attribution changed the saved item ID");
        }
    }

    // Match marked variants without merging them into ordinary player heads
    private static boolean contains(Collection<ItemStack> stacks, PlayerMannequinVariant variant){
        return stacks.stream().anyMatch(stack -> variant.id().equals(stack.get(CTDataComponents.PLAYER_MANNEQUIN_VARIANT)));
    }

    // Require one visible entry for every supporter and keep the command item out of the index
    private static void entries(Collection<ItemStack> stacks){
        for(var variant : PlayerMannequinVariants.all()){
            require(stacks.stream().filter(stack -> variant.id().equals(stack.get(CTDataComponents.PLAYER_MANNEQUIN_VARIANT))).count() == 1,
                    "Viewer omitted or duplicated " + variant.id());
        }
        require(stacks.stream().noneMatch(stack -> stack.is(CTItems.CUSTOM_MANNEQUIN.get())), "Viewer exposed the command-only mannequin");
    }

    // Close the client after preserving its validation result
    private static void finish(String output, String result){
        complete = true;
        CTFeatureToggles.clearServerOverrides();
        try{ Files.writeString(Path.of(output, "result.txt"), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        Minecraft.getInstance().stop();
    }

    // Fail with the missing name or registry contract
    private static void require(boolean val, String message){
        if(!val) throw new AssertionError(message);
    }

    // Capture the JEI runtime through its supported plugin lifecycle
    @mezz.jei.api.JeiPlugin
    public static final class JeiValidation implements mezz.jei.api.IModPlugin{
        // Publish the runtime supplied by JEI's plugin lifecycle
        private static volatile mezz.jei.api.runtime.IJeiRuntime runtime;

        // Use an isolated test plugin ID
        @Override public ResourceLocation getPluginUid(){
            return ResourceLocation.fromNamespaceAndPath("createthrusters", "supporter_head_validation");
        }

        // Wait until JEI has completed ingredient registration
        @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value){ runtime = value; }

        // Release the runtime when the isolated world closes
        @Override public void onRuntimeUnavailable(){ runtime = null; }

        // Test real ingredient UIDs, search aliases and disable/restore behaviour
        private static boolean check() throws ReflectiveOperationException{
            if(runtime == null) return false;
            var manager = runtime.getIngredientManager();
            entries(manager.getAllItemStacks());
            var helper = manager.getIngredientHelper(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
            var ids = new HashSet<>();
            for(var stack : SupporterHeads.allStacks()){
                require(ids.add(helper.getUid(stack, mezz.jei.api.ingredients.subtypes.UidContext.Ingredient)), "JEI collapsed supporter subtypes");
                require("createthrusters".equals(helper.getDisplayModId(stack)), "JEI labelled a supporter head as Minecraft");
                aliasTooltip(stack);
            }
            var filter = runtime.getIngredientFilter();
            for(var variant : PlayerMannequinVariants.all()){
                for(String query : List.of(variant.displayName().getString(), variant.id(), "mannequin", "@gadgets", "@createthrusters",
                        "@gadgets " + variant.displayName().getString())){
                    filter.setFilterText(query);
                    require(contains(filter.getFilteredItemStacks(), variant), "JEI search omitted " + variant.id() + " for " + query);
                }
            }
            CTFeatureToggles.applyServerOverrides(Map.of(), Map.of("player_mannequin", false), Map.of());
            com.rieno.gadgetsandgizmos.compat.jei.CTJeiPlugin.refreshHiddenItemsFromCurrentToggles();
            require(manager.getAllItemStacks().stream().noneMatch(SupporterHeads::isSupporterHead), "JEI retained disabled supporter entries");
            CTFeatureToggles.clearServerOverrides();
            com.rieno.gadgetsandgizmos.compat.jei.CTJeiPlugin.refreshHiddenItemsFromCurrentToggles();
            entries(manager.getAllItemStacks());
            return true;
        }

        // Exercise JEI's private alias section because its public renderer omits that grid-only stage
        private static void aliasTooltip(ItemStack stack) throws ReflectiveOperationException{
            var manager = runtime.getIngredientManager();
            var typed = manager.createTypedIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack).orElseThrow();
            require(!manager.getIngredientAliases(typed).isEmpty(), "Name aliases were removed from the search index");
            Class<?> tooltipType = Class.forName("mezz.jei.common.gui.JeiTooltip");
            Object tooltip = tooltipType.getConstructor().newInstance();
            Class<?> gridType = Class.forName("mezz.jei.gui.overlay.IngredientGridTooltipHelper");
            Object grid = gridType.getConstructors()[0].newInstance(manager, null, null, null, null);
            var method = gridType.getDeclaredMethod("addIngredientAliasesToTooltip", tooltipType,
                    mezz.jei.api.ingredients.ITypedIngredient.class, mezz.jei.api.runtime.IIngredientManager.class);
            method.setAccessible(true);
            method.invoke(grid, tooltip, typed, manager);
            require(((List<?>)tooltipType.getMethod("getLegacyComponents").invoke(tooltip)).isEmpty(),
                    "JEI added a Search Aliases heading or list to the tooltip");
        }
    }

    // Exercise both EMI index sources without depending on creative-only variant discovery
    private static final class EmiValidation{
        private static int phase;

        // Start with the index source that cannot discover component variants on its own
        private static void prepare(){
            dev.emi.emi.config.EmiConfig.indexSource = dev.emi.emi.config.IndexSource.REGISTERED;
            dev.emi.emi.config.EmiConfig.appendModId = true;
            dev.emi.emi.config.EmiConfig.appendItemModId = true;
        }

        // Verify explicit entries, searchable names and deduplication after a creative-index reload
        private static boolean check(){
            if(!dev.emi.emi.runtime.EmiReloadManager.isLoaded()) return false;
            require(dev.emi.emi.config.EmiConfig.indexSource == (phase == 0 ? dev.emi.emi.config.IndexSource.REGISTERED
                    : dev.emi.emi.config.IndexSource.CREATIVE), "EMI did not use the requested index source");
            List<ItemStack> stacks = dev.emi.emi.api.EmiApi.getIndexStacks().stream().map(dev.emi.emi.api.stack.EmiStack::getItemStack).toList();
            entries(stacks);
            var variants = SupporterHeads.allStacks();
            for(int idx = 0; idx < variants.size(); idx++){
                var variant = PlayerMannequinVariants.all().get(idx);
                var stack = dev.emi.emi.api.EmiApi.getIndexStacks().stream()
                        .filter(entry -> variant.id().equals(entry.getItemStack().get(CTDataComponents.PLAYER_MANNEQUIN_VARIANT)))
                        .findFirst().orElseThrow();
                String modName = ModList.get().getModContainerById("createthrusters").orElseThrow().getModInfo().getDisplayName();
                require(stack.getTooltipText().getLast().getString().equals(modName), "EMI tooltip has the wrong mod label");
                require(stack.getId().getNamespace().equals("minecraft"), "EMI changed the real registry ID");
                require(new dev.emi.emi.search.EmiSearch.CompiledQuery("@gadgets").test(stack.copy()),
                        "EMI unbaked search ignored the head's creator");
                for(int other = 0; other < variants.size(); other++){
                    require(stack.isEqual(dev.emi.emi.api.stack.EmiStack.of(variants.get(other))) == (idx == other),
                            "EMI collapsed different supporter heads");
                }
                for(String query : List.of(variant.displayName().getString(), variant.id(), "mannequin", "@gadgets", "@createthrusters",
                        "@gadgets " + variant.displayName().getString())){
                    require(new dev.emi.emi.search.EmiSearch.CompiledQuery(query).test(stack),
                            "EMI search omitted " + variant.id() + " for " + query);
                }
            }
            fallback();
            if(phase == 0){
                phase = 1;
                dev.emi.emi.config.EmiConfig.indexSource = dev.emi.emi.config.IndexSource.CREATIVE;
                dev.emi.emi.runtime.EmiReloadManager.reload();
                return false;
            }
            return true;
        }

        // Keep ordinary named and profiled player heads on the existing component comparison
        private static void fallback(){
            var prev = dev.emi.emi.api.stack.Comparison.compareComponents();
            var comparison = com.rieno.gadgetsandgizmos.lib.compat.emi.BundledHeadEmiSupport.withFallback(prev);
            var first = new ItemStack(Items.PLAYER_HEAD);
            first.set(DataComponents.PROFILE, new ResolvableProfile(new GameProfile(new UUID(1, 2), "First")));
            var second = first.copy();
            second.set(DataComponents.CUSTOM_NAME, Component.literal("Other name"));
            var a = dev.emi.emi.api.stack.EmiStack.of(first);
            var b = dev.emi.emi.api.stack.EmiStack.of(second);
            require(comparison.compare(a, b) == prev.compare(a, b), "EMI changed ordinary player-head comparison");
            require(!comparison.compare(a, dev.emi.emi.api.stack.EmiStack.of(SupporterHeads.allStacks().getFirst())),
                    "EMI merged ordinary and supporter heads");
        }
    }
}
