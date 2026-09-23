package com.rieno.gadgetsandgizmos.compat.emi;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.IonThrusterStacks;
import com.rieno.gadgetsandgizmos.content.SupporterHeads;
import com.rieno.gadgetsandgizmos.content.WorkerEnergyBatteryItem;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueJoystickConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.ClawConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.GyroscopeLinkConfigScreen;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletScreen;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.slf4j.Logger;

import java.lang.reflect.Method;

// Add the addon's recipes and workstations to EMI
@dev.emi.emi.api.EmiEntrypoint
public class CTEmiPlugin implements EmiPlugin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active registry
    private static EmiRegistry activeRegistry;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the CT EMI plugin
    @Override
    public void register(EmiRegistry registry) {
        if (!FMLEnvironment.dist.isClient()) {
            activeRegistry = null;
            return;
        }
        activeRegistry = registry;
        try {
            registry.addDragDropHandler(AnalogueJoystickConfigScreen.class, createGhostDragHandler());
            registry.addDragDropHandler(AnalogueContraptionControllerConfigScreen.class, createGhostDragHandler());
            registry.addDragDropHandler(AdvancedContraptionControllerScreen.class, createAdvGhostDragHandler());
            registry.addDragDropHandler(ClawConfigScreen.class, createGhostDragHandler());
            registry.addDragDropHandler(GyroscopeLinkConfigScreen.class, createGhostDragHandler());
            registry.addDragDropHandler(DiagnosticTabletScreen.class, createTabletGhostDragHandler());
            registry.addExclusionArea(DiagnosticTabletScreen.class, (screen, consumer) -> {
                var area = screen.tabletGuiArea();
                consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
            });
            registry.addGenericExclusionArea((screen, consumer) -> {
                if (screen instanceof AdvancedContraptionControllerScreen advanced) {
                    advanced.getRecipeViewerExclusionAreas().forEach(rect ->
                            consumer.accept(new Bounds(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight())));
                }
            });
            registerIonThruster(registry);
            registry.removeEmiStacks(CTEmiPlugin::isDisabledModStack);
            registry.removeRecipes(CTEmiPlugin::hasDisabledOutput);
        } catch (Throwable throwable) {

            CT_LOGGER.warn("[CT][EMI] Disabled EMI drag-drop integration due to compatibility error", throwable);
        }
    }

    // Add the focused Thruster as a separate EMI entry
    private static void registerIonThruster(EmiRegistry registry) {
        if (CTItems.THRUSTER == null || !CTFeatureToggles.isItemEnabled("thruster")) {
            return;
        }
        registry.setDefaultComparison(CTItems.THRUSTER.get(), Comparison.compareData(stack ->
                IonThrusterStacks.isIonThruster(stack.getItemStack())));
        ItemStack ionThruster = IonThrusterStacks.create();
        if (!ionThruster.isEmpty()) {
            registry.addEmiStack(EmiStack.of(ionThruster));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh the hidden items from current toggles
    public static void refreshHiddenItemsFromCurrentToggles() {
        if (!FMLEnvironment.dist.isClient() || activeRegistry == null || Minecraft.getInstance().level == null) {
            return;
        }
        try {
            Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            Method reload = reloadManager.getMethod("reload");
            reload.invoke(null);
        } catch (Throwable throwable) {
            CT_LOGGER.warn("[CT][EMI] Failed to refresh hidden item visibility", throwable);
        }
    }

    // Check if this has disabled output
    private static boolean hasDisabledOutput(EmiRecipe recipe) {
        return recipe.getOutputs().stream().anyMatch(CTEmiPlugin::isDisabledModStack);
    }

    // Check if this is a disabled mod stack
    private static boolean isDisabledModStack(EmiStack stack) {
        ItemStack itemStack = stack.getItemStack();
        if (itemStack.isEmpty()) {
            return false;
        }
        if (WorkerEnergyBatteryItem.isInternal(itemStack)) return true;
        if (SupporterHeads.isSupporterHead(itemStack)) {
            return !CTFeatureToggles.isItemEnabled("player_mannequin");
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return id != null
                && CreateThrusters.MOD_ID.equals(id.getNamespace())
                && !CTFeatureToggles.isItemEnabled(id.getPath());
    }

    // Create the ghost drag handler
    private static <T extends com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen<? extends GhostItemMenu<?>>> EmiDragDropHandler<T> createGhostDragHandler() {
        return new EmiDragDropHandler.SlotBased<>(
                screen -> screen.getMenu().slots.stream().skip(36).filter(Slot::isActive).toList(),
                CTEmiPlugin::acceptIngredient);
    }

    // Create the adv ghost drag handler
    private static EmiDragDropHandler<AdvancedContraptionControllerScreen> createAdvGhostDragHandler() {
        return new EmiDragDropHandler.SlotBased<>(
                screen -> screen.getMenu().slots.stream().filter(slot -> isAdvancedGhostSlot(screen, slot)).toList(),
                CTEmiPlugin::acceptAdvIngredient);
    }

    // Create the tablet ghost drag handler
    private static EmiDragDropHandler<DiagnosticTabletScreen> createTabletGhostDragHandler() {
        return new EmiDragDropHandler.BoundsBased<>((screen, targets) -> {
            var areas = screen.redstoneGhostAreas();
            for (int idx = 0; idx < areas.size(); idx++) {
                int slot = idx;
                var area = areas.get(idx);
                targets.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()),
                        ingredient -> acceptTabletIngredient(screen, slot, ingredient));
            }
        });
    }

    // Accept the tablet ingredient
    private static void acceptTabletIngredient(DiagnosticTabletScreen screen, int slot,
                                               EmiIngredient ingredient) {
        ItemStack stack = ingredient.getEmiStacks().stream()
                .filter(emiStack -> !emiStack.isEmpty())
                .findFirst()
                .map(EmiStack::getItemStack)
                .orElse(ItemStack.EMPTY)
                .copy();
        if (stack.isEmpty()) return;
        stack.setCount(1);
        screen.acceptRedstoneGhost(slot, stack);
    }

    // Accept the adv ingredient
    private static void acceptAdvIngredient(AdvancedContraptionControllerScreen screen, Slot slot, EmiIngredient ingredient) {
        if (!isAdvancedGhostSlot(screen, slot)) return;
        ItemStack stack = ingredient.getEmiStacks().stream().filter(emiStack -> !emiStack.isEmpty()).findFirst()
                .map(EmiStack::getItemStack).orElse(ItemStack.EMPTY).copy();
        if (stack.isEmpty()) return;
        stack.setCount(1);
        slot.set(stack);
        slot.setChanged();
    }

    // Check if this is an advanced ghost slot
    private static boolean isAdvancedGhostSlot(AdvancedContraptionControllerScreen screen, Slot slot) {
        return slot.isActive() && slot instanceof SlotItemHandler itemSlot
                && itemSlot.getItemHandler() == screen.getMenu().ghostInventory;
    }

    // Accept the ingredient
    private static <T extends com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen<? extends GhostItemMenu<?>>> void acceptIngredient(T screen, Slot slot, EmiIngredient ingredient) {
        try {
            ItemStack stack = ingredient.getEmiStacks().stream()
                    .filter(emiStack -> !emiStack.isEmpty())
                    .findFirst()
                    .map(EmiStack::getItemStack)
                    .orElse(ItemStack.EMPTY)
                    .copy();
            if (stack.isEmpty()) {
                return;
            }

            stack.setCount(1);
            GhostItemMenu<?> menu = screen.getMenu();
            int slotIndex = menu.slots.indexOf(slot) - 36;
            if (slotIndex < 0 || slotIndex >= menu.ghostInventory.getSlots()) {
                return;
            }

            menu.ghostInventory.setStackInSlot(slotIndex, stack);
            slot.setChanged();
            CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
        } catch (Throwable throwable) {

            CT_LOGGER.warn("[CT][EMI] Ignored ghost drag due to compatibility error", throwable);
        }
    }
}
