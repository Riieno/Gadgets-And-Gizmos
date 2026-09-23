package com.rieno.gadgetsandgizmos.compat.jei;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinCrafting;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariant;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.content.SupporterHeads;
import com.rieno.gadgetsandgizmos.content.WorkerEnergyBatteryItem;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueJoystickConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.ClawConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.GyroscopeLinkConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletScreen;
import com.rieno.gadgetsandgizmos.content.IonThrusterStacks;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.mojang.logging.LogUtils;
import com.simibubi.create.compat.jei.GhostIngredientHandler;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Add the addon's recipes and workstations to JEI
@JeiPlugin
public class CTJeiPlugin implements IModPlugin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "jei");
    private static final Logger CT_LOGGER = LogUtils.getLogger();
    // Keep removed JEI entries ready for feature toggle changes
    private static final Map<ResourceLocation, List<ItemStack>> KNOWN_ITEM_STACKS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, List<ItemStack>> REMOVED_ITEM_STACKS = new LinkedHashMap<>();
    private static final Map<RecipeType<?>, List<?>> HIDDEN_RECIPES = new HashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active runtime
    private static IJeiRuntime activeRuntime;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the plugin uid
    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    // Register the ion thruster as a distinct Thruster subtype
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        if (CTItems.THRUSTER != null) {
            registration.registerSubtypeInterpreter(CTItems.THRUSTER.get(), new ISubtypeInterpreter<>() {
                @Override
                public Object getSubtypeData(ItemStack stack, UidContext context) {
                    return IonThrusterStacks.isIonThruster(stack) ? "ion_thruster" : null;
                }

                @Override
                public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                    return IonThrusterStacks.isIonThruster(stack) ? "ion_thruster" : "";
                }
            });
        }
    }

    // Add the pre-equipped ion thruster stack to JEI's item list
    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        if (!CTFeatureToggles.isItemEnabled("thruster")) {
            return;
        }
        ItemStack ionThruster = IonThrusterStacks.create();
        if (!ionThruster.isEmpty()) {
            registration.addExtraItemStacks(List.of(ionThruster));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the gui handlers
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            registration.addGhostIngredientHandler(AnalogueJoystickConfigScreen.class, ghostHandler());
            registration.addGhostIngredientHandler(AnalogueContraptionControllerConfigScreen.class, ghostHandler());
            registration.addGhostIngredientHandler(AdvancedContraptionControllerScreen.class, advancedGhostHandler());
            registration.addGhostIngredientHandler(ClawConfigScreen.class, ghostHandler());
            registration.addGhostIngredientHandler(GyroscopeLinkConfigScreen.class, ghostHandler());
            registration.addGhostIngredientHandler(DiagnosticTabletScreen.class, tabletGhostHandler());
            registration.addGuiScreenHandler(AdvancedContraptionControllerScreen.class, screen -> {
                Rect2i area = screen.recipeViewerGuiArea();
                return new IGuiProperties() {
                    // Get the screen class
                    @Override
                    public Class<AdvancedContraptionControllerScreen> screenClass() {
                        return AdvancedContraptionControllerScreen.class;
                    }

                    // Get the gui left
                    @Override
                    public int guiLeft() {
                        return area.getX();
                    }

                    // Get the gui top
                    @Override
                    public int guiTop() {
                        return area.getY();
                    }

                    // Get the gui x size
                    @Override
                    public int guiXSize() {
                        return area.getWidth();
                    }

                    // Get the gui y size
                    @Override
                    public int guiYSize() {
                        return area.getHeight();
                    }

                    // Get the screen width
                    @Override
                    public int screenWidth() {
                        return screen.width;
                    }

                    // Get the screen height
                    @Override
                    public int screenHeight() {
                        return screen.height;
                    }
                };
            });
            registration.addGuiScreenHandler(DiagnosticTabletScreen.class, screen -> {
                Rect2i area = screen.tabletGuiArea();
                return new IGuiProperties() {
                    // Get the screen class
                    @Override
                    public Class<DiagnosticTabletScreen> screenClass() {
                        return DiagnosticTabletScreen.class;
                    }

                    // Get the gui left
                    @Override
                    public int guiLeft() {
                        return area.getX();
                    }

                    // Get the gui top
                    @Override
                    public int guiTop() {
                        return area.getY();
                    }

                    // Get the gui x size
                    @Override
                    public int guiXSize() {
                        return area.getWidth();
                    }

                    // Get the gui y size
                    @Override
                    public int guiYSize() {
                        return area.getHeight();
                    }

                    // Get the screen width
                    @Override
                    public int screenWidth() {
                        return screen.width;
                    }

                    // Get the screen height
                    @Override
                    public int screenHeight() {
                        return screen.height;
                    }
                };
            });
            registration.addGuiContainerHandler(AdvancedContraptionControllerScreen.class, new IGuiContainerHandler<>() {
                // Get the gui extra areas
                @Override
                public List<Rect2i> getGuiExtraAreas(AdvancedContraptionControllerScreen screen) {
                    return screen.getRecipeViewerExclusionAreas();
                }
            });
        } catch (Throwable throwable) {

            CT_LOGGER.warn("[CT][JEI] Disabled JEI ghost ingredient integration due to compatibility error", throwable);
        }
    }

    // Register the recipes
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            registration.addRecipes(RecipeTypes.CRAFTING, mannequinCraftingRecipes());
            registration.addRecipes(RecipeTypes.ANVIL, mannequinAnvilRecipes(registration));
        } catch (Throwable throwable) {
            CT_LOGGER.warn("[CT][JEI] Failed to register player mannequin recipe displays", throwable);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the runtime available event
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!FMLEnvironment.dist.isClient()) {
            activeRuntime = null;
            return;
        }
        activeRuntime = runtime;
        REMOVED_ITEM_STACKS.clear();
        HIDDEN_RECIPES.clear();
        captureKnownItemStacks(runtime);
        refreshHiddenItemsFromCurrentToggles();
    }

    // Handle the runtime unavailable event
    @Override
    public void onRuntimeUnavailable() {
        activeRuntime = null;
        KNOWN_ITEM_STACKS.clear();
        REMOVED_ITEM_STACKS.clear();
        HIDDEN_RECIPES.clear();
    }

    // Refresh the hidden items from current toggles
    public static void refreshHiddenItemsFromCurrentToggles() {
        if (!FMLEnvironment.dist.isClient()
                || activeRuntime == null
                || !Minecraft.getInstance().isRunning()) {
            return;
        }

        try {
            refreshItemIngredients(activeRuntime);
            refreshRecipes(activeRuntime.getRecipeManager());
        } catch (Throwable throwable) {
            CT_LOGGER.warn("[CT][JEI] Failed to refresh hidden item visibility", throwable);
        }
    }

    // Capture the known item stacks
    private static void captureKnownItemStacks(IJeiRuntime runtime) {
        KNOWN_ITEM_STACKS.clear();
        for (ItemStack stack : runtime.getIngredientManager().getAllItemStacks()) {
            ResourceLocation id = featureId(stack);
            if (id != null) {
                KNOWN_ITEM_STACKS.computeIfAbsent(id, ignored -> new ArrayList<>()).add(stack.copy());
            }
        }
        BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (isModItem(id)) {
                KNOWN_ITEM_STACKS.computeIfAbsent(id, ignored -> List.of(new ItemStack(item)));
            }
        });
    }

    // Get the feature id for an item stack
    private static ResourceLocation featureId(ItemStack stack) {
        if (SupporterHeads.isSupporterHead(stack)) {
            return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "player_mannequin");
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return isModItem(id) ? id : null;
    }

    // Refresh the item ingredients
    private static void refreshItemIngredients(IJeiRuntime runtime) {
        List<ResourceLocation> restored = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : REMOVED_ITEM_STACKS.entrySet()) {
            if (!isInternalWorkerItem(entry.getKey())
                    && CTFeatureToggles.isItemEnabled(entry.getKey().getPath())) {
                runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, entry.getValue());
                restored.add(entry.getKey());
            }
        }
        restored.forEach(REMOVED_ITEM_STACKS::remove);

        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : KNOWN_ITEM_STACKS.entrySet()) {
            if ((!isInternalWorkerItem(entry.getKey())
                    && CTFeatureToggles.isItemEnabled(entry.getKey().getPath()))
                    || REMOVED_ITEM_STACKS.containsKey(entry.getKey())) {
                continue;
            }
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, entry.getValue());
            REMOVED_ITEM_STACKS.put(entry.getKey(), entry.getValue());
        }
    }

    // Refresh the recipes
    private static void refreshRecipes(IRecipeManager recipeManager) {
        boolean hasDisabledItems = hasDisabledItems();
        if (HIDDEN_RECIPES.isEmpty() && !hasDisabledItems) {
            return;
        }

        for (Map.Entry<RecipeType<?>, List<?>> entry : new ArrayList<>(HIDDEN_RECIPES.entrySet())) {
            unhideRecipes(recipeManager, entry.getKey(), entry.getValue());
        }
        HIDDEN_RECIPES.clear();

        if (!hasDisabledItems) {
            return;
        }

        recipeManager.createRecipeCategoryLookup().get()
                .forEach(category -> hideDisabledOutputRecipes(recipeManager, category));
    }

    // Check if this has disabled items
    private static boolean hasDisabledItems() {
        return CTFeatureToggles.itemDefaults().keySet().stream()
                .anyMatch(itemId -> !CTFeatureToggles.isItemEnabled(itemId));
    }

    // Hide the disabled output recipes
    private static <T> void hideDisabledOutputRecipes(IRecipeManager recipeManager, IRecipeCategory<T> category) {
        RecipeType<T> recipeType = category.getRecipeType();
        List<T> recipes = recipeManager.createRecipeLookup(recipeType).get()
                .filter(recipe -> hasDisabledOutput(recipeManager, category, recipe))
                .toList();
        if (!recipes.isEmpty()) {
            recipeManager.hideRecipes(recipeType, recipes);
            HIDDEN_RECIPES.put(recipeType, recipes);
        }
    }

    // Check if this has disabled output
    private static <T> boolean hasDisabledOutput(IRecipeManager recipeManager,
                                                 IRecipeCategory<T> category,
                                                 T recipe) {
        try {
            IIngredientSupplier ingredients = recipeManager.getRecipeIngredients(category, recipe);
            return ingredients.getIngredients(RecipeIngredientRole.OUTPUT).stream()
                    .map(ITypedIngredient::getItemStack)
                    .flatMap(java.util.Optional::stream)
                    .anyMatch(CTJeiPlugin::isDisabledModItem);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // Unhide the recipes
    @SuppressWarnings("unchecked")
    private static <T> void unhideRecipes(IRecipeManager recipeManager,
                                          RecipeType<?> rawType,
                                          List<?> rawRecipes) {
        RecipeType<T> recipeType = (RecipeType<T>) rawType;
        List<T> recipes = (List<T>) rawRecipes;
        recipeManager.unhideRecipes(recipeType, recipes);
    }

    // Check if this is a disabled mod item
    private static boolean isDisabledModItem(ItemStack stack) {
        if (WorkerEnergyBatteryItem.isInternal(stack)) return true;
        if (SupporterHeads.isSupporterHead(stack)) {
            return !CTFeatureToggles.isItemEnabled("player_mannequin");
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return isModItem(id) && !CTFeatureToggles.isItemEnabled(id.getPath());
    }

    // Check if this is a mod item
    private static boolean isModItem(ResourceLocation id) {
        return id != null && CreateThrusters.MOD_ID.equals(id.getNamespace());
    }

    // Check whether an id belongs to a render-only worker item
    private static boolean isInternalWorkerItem(ResourceLocation id) {
        return id != null && CreateThrusters.MOD_ID.equals(id.getNamespace())
                && "worker_energy_battery".equals(id.getPath());
    }

    // Get the mannequin crafting recipes
    private static List<RecipeHolder<CraftingRecipe>> mannequinCraftingRecipes() {
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        for (PlayerMannequinVariant variant : PlayerMannequinVariants.all()) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(Ingredient.of(PlayerMannequinCrafting.sampleSkulls().stream()));
            ingredients.add(Ingredient.of(PlayerMannequinCrafting.namedNameTag(variant)));

            CraftingRecipe recipe = new ShapelessRecipe(
                    "player_mannequin",
                    CraftingBookCategory.MISC,
                    SupporterHeads.createStack(variant),
                    ingredients);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                    "jei/player_mannequin/crafting/" + variant.id());
            recipes.add(new RecipeHolder<>(id, recipe));
        }
        return recipes;
    }

    // Get the mannequin anvil recipes
    private static List<IJeiAnvilRecipe> mannequinAnvilRecipes(IRecipeRegistration registration) {
        List<IJeiAnvilRecipe> recipes = new ArrayList<>();
        for (PlayerMannequinVariant variant : PlayerMannequinVariants.all()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                    "jei/player_mannequin/anvil/" + variant.id());
            recipes.add(registration.getVanillaRecipeFactory().createAnvilRecipe(
                    PlayerMannequinCrafting.namedSampleSkulls(variant),
                    emptyInputs(PlayerMannequinCrafting.sampleSkulls().size()),
                    List.of(SupporterHeads.createStack(variant)),
                    id));
        }
        return recipes;
    }

    // Get the empty inputs
    private static List<ItemStack> emptyInputs(int count) {
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stacks.add(ItemStack.EMPTY);
        }
        return stacks;
    }

    // Get the ghost handler
    private static <M extends GhostItemMenu<?>, S extends AbstractSimiContainerScreen<M>> IGhostIngredientHandler<S> ghostHandler() {
        GhostIngredientHandler<M> delegate = new GhostIngredientHandler<>();
        return new IGhostIngredientHandler<>() {
            // Get the targets typed
            @Override
            public <I> List<Target<I>> getTargetsTyped(S screen, ITypedIngredient<I> ingredient, boolean doStart) {
                try {
                    return delegate.getTargetsTyped(screen, ingredient, doStart);
                } catch (Throwable throwable) {

                    CT_LOGGER.warn("[CT][JEI] Ghost target query failed; ignoring ingredient drag", throwable);
                    return List.of();
                }
            }

            // Handle the complete event
            @Override
            public void onComplete() {
                try {
                    delegate.onComplete();
                } catch (Throwable throwable) {
                    CT_LOGGER.warn("[CT][JEI] Ghost drag completion failed", throwable);
                }
            }

            // Check if this should highlight targets
            @Override
            public boolean shouldHighlightTargets() {
                try {
                    return delegate.shouldHighlightTargets();
                } catch (Throwable throwable) {
                    CT_LOGGER.warn("[CT][JEI] Ghost target highlight query failed", throwable);
                    return false;
                }
            }
        };
    }

    // Get the advanced ghost handler
    private static IGhostIngredientHandler<AdvancedContraptionControllerScreen> advancedGhostHandler() {
        return new IGhostIngredientHandler<>() {
            // Get the targets typed
            @Override
            public <I> List<Target<I>> getTargetsTyped(AdvancedContraptionControllerScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
                ItemStack src = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                if (src.isEmpty()) return List.of();
                List<Target<I>> targets = new ArrayList<>();
                for (Slot slot : screen.getMenu().slots) {
                    if (!isAdvancedGhostSlot(screen, slot)) continue;
                    targets.add(new Target<>() {
                        // Get the area
                        @Override
                        public Rect2i getArea() {
                            return new Rect2i(screen.slotScreenX(slot), screen.slotScreenY(slot), 16, 16);
                        }

                        // Accept the CT JEI plugin value
                        @Override
                        public void accept(I ignored) {
                            ItemStack stack = src.copy();
                            stack.setCount(1);
                            slot.set(stack);
                            slot.setChanged();
                        }
                    });
                }
                return targets;
            }

            // Handle the complete event
            @Override
            public void onComplete() {
            }
        };
    }

    // Check if this is an advanced ghost slot
    private static boolean isAdvancedGhostSlot(AdvancedContraptionControllerScreen screen, Slot slot) {
        return slot.isActive() && slot instanceof SlotItemHandler itemSlot
                && itemSlot.getItemHandler() == screen.getMenu().ghostInventory;
    }

    // Get the tablet ghost handler
    private static IGhostIngredientHandler<DiagnosticTabletScreen> tabletGhostHandler() {
        return new IGhostIngredientHandler<>() {
            // Get the targets typed
            @Override
            public <I> List<Target<I>> getTargetsTyped(DiagnosticTabletScreen screen,
                                                       ITypedIngredient<I> ingredient,
                                                       boolean doStart) {
                ItemStack src = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                if (src.isEmpty()) return List.of();
                List<Rect2i> areas = screen.redstoneGhostAreas();
                List<Target<I>> targets = new ArrayList<>();
                for (int idx = 0; idx < areas.size(); idx++) {
                    int slot = idx;
                    targets.add(new Target<>() {
                        // Get the area
                        @Override
                        public Rect2i getArea() {
                            return areas.get(slot);
                        }

                        // Accept the CT JEI plugin value
                        @Override
                        public void accept(I ignored) {
                            screen.acceptRedstoneGhost(slot, src);
                        }
                    });
                }
                return targets;
            }

            // Handle the complete event
            @Override
            public void onComplete() {
            }
        };
    }
}
