package com.rieno.gadgetsandgizmos.compat.jei;

import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingDisplays;
import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingPreview;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProcessingRecipeViews;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Show one thruster exhaust upgrade as a JEI processing tab
public final class ThrusterJeiCategory implements IRecipeCategory<WorkerProcessingRecipeViews.View>{
    private final ThrusterProcessingDisplays.Mode mode;
    private final RecipeType<WorkerProcessingRecipeViews.View> type;
    private final IDrawable icon;

    public ThrusterJeiCategory(ThrusterProcessingDisplays.Mode mode, IGuiHelper gui){
        this.mode = mode;
        type = new RecipeType<>(mode.id(), WorkerProcessingRecipeViews.View.class);
        icon = gui.createDrawableItemStack(mode.icon());
    }

    @Override public RecipeType<WorkerProcessingRecipeViews.View> getRecipeType(){ return type; }
    @Override public Component getTitle(){ return Component.translatable("createthrusters.recipe." + mode.id().getPath()); }
    @Override public IDrawable getIcon(){ return icon; }
    @Override public int getWidth(){ return ThrusterProcessingPreview.WIDTH; }
    @Override public int getHeight(){ return ThrusterProcessingPreview.HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorkerProcessingRecipeViews.View view, IFocusGroup focuses){
        builder.addInputSlot(ThrusterProcessingPreview.INPUT_X, ThrusterProcessingPreview.INPUT_Y)
                .addIngredients(view.input()).setStandardSlotBackground();
        for(int idx = 0; idx < view.outputs().size(); idx++){
            WorkerProcessingRecipeViews.Output output = view.outputs().get(idx);
            var slot = builder.addOutputSlot(ThrusterProcessingPreview.outputX(view.outputs().size(), idx),
                            ThrusterProcessingPreview.outputY(view.outputs().size(), idx))
                    .addItemStack(output.stack()).setOutputSlotBackground();
            if(output.chance() < 1.0F){
                slot.addRichTooltipCallback((recipeSlot, tooltip) -> tooltip.add(Component.translatable(
                        "createthrusters.recipe.output_chance", Math.round(output.chance() * 100.0F))));
            }
        }
    }

    @Override
    public void draw(WorkerProcessingRecipeViews.View view, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY){
        ThrusterProcessingPreview.render(graphics, 0, 0);
    }

    @Override public ResourceLocation getRegistryName(WorkerProcessingRecipeViews.View view){
        ResourceLocation source = view.source().id();
        return ResourceLocation.fromNamespaceAndPath(mode.id().getNamespace(),
                mode.id().getPath() + "/" + source.getNamespace() + "/" + source.getPath());
    }
}
