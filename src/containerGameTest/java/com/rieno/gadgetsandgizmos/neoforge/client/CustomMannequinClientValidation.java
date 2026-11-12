package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// Verify a live account lookup, deferred placement, synchronized head and rendered mannequin
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class CustomMannequinClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.customMannequinValidation");
    private static final BlockPos FLOOR = new BlockPos(0, -59, 0);
    private static boolean hidden, installed, complete, opened;
    private static int imageTicks;
    private static long started;
    private static volatile Throwable failure;
    private static volatile boolean ready;
    private static volatile UUID mannequinId;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(!hidden){ hidden = true; started = System.nanoTime(); GLFW.glfwHideWindow(mc.getWindow().getWindow()); }
        try{
            if(failure != null) throw new AssertionError("Server validation failed", failure);
            if(System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Custom mannequin validation timed out");
            if(mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            mc.options.pauseOnLostFocus = false;
            if(!installed){ installed = true; install(mc); }
            if(!ready) return;
            ItemStack stack = mc.player.getMainHandItem();
            if(!stack.is(CTItems.CUSTOM_MANNEQUIN.get()) || CTItems.CUSTOM_MANNEQUIN.get().skin(stack) == null) return;
            var entity = mc.level.getEntitiesOfClass(PlayerMannequinEntity.class, new AABB(FLOOR).inflate(5))
                    .stream().filter(val -> val.getUUID().equals(mannequinId)).findFirst().orElse(null);
            if(entity == null || entity.customMannequinName().isBlank()) return;
            require(entity.getPickResult().is(CTItems.CUSTOM_MANNEQUIN.get()), "The client picked a built-in supporter head");
            if(!opened){
                CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
                for(var tab : CreativeModeTabs.allTabs()){
                    require(tab.getDisplayItems().stream().noneMatch(val -> val.is(CTItems.CUSTOM_MANNEQUIN.get())),
                            "The command item appeared in a creative inventory");
                    require(tab.getSearchTabDisplayItems().stream().noneMatch(val -> val.is(CTItems.CUSTOM_MANNEQUIN.get())),
                            "The command item appeared in creative search");
                }
                mc.setScreen(new Preview(stack.copy(), entity));
                opened = true;
            }
            if(++imageTicks < 100) return;
            if(!SupporterSkinClientValidation.validate(mc, Path.of(OUTPUT))) return;
            if(net.neoforged.fml.ModList.get().isLoaded("jei")){
                var field = Class.forName("com.rieno.gadgetsandgizmos.compat.jei.CTJeiPlugin").getDeclaredField("activeRuntime");
                field.setAccessible(true);
                var runtime = (mezz.jei.api.runtime.IJeiRuntime)field.get(null);
                if(runtime == null) return;
                com.rieno.gadgetsandgizmos.compat.jei.CTJeiPlugin.refreshHiddenItemsFromCurrentToggles();
                com.rieno.gadgetsandgizmos.compat.jei.CTJeiPlugin.refreshHiddenItemsFromCurrentToggles();
                require(runtime.getIngredientManager().getAllItemStacks().stream()
                                .noneMatch(val -> val.is(CTItems.CUSTOM_MANNEQUIN.get())),
                        "JEI exposed the hidden command item after refreshing toggles");
            }
            try(var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                image.writeToFile(Path.of(OUTPUT).resolve("custom-mannequin.png"));
            }
            finish("PASSED live Notch profile lookup, one-click deferred placement, single consumption, skin and name synchronization, custom recovery, hidden creative entries and recipe-viewer refresh\n");
        }catch(Throwable err){
            err.printStackTrace();
            finish("FAILED " + err + "\n" + (failure == null ? "" : failure));
        }
    }

    // Give the exact documented item and use it before any inventory tick can resolve its skin
    private static void install(Minecraft mc){
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try{
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.serverLevel();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().selected = 0;
                player.connection.teleport(.5, -58, 3.5, 180, 0);
                level.setBlockAndUpdate(FLOOR, Blocks.STONE.defaultBlockState());
                for(var old : level.getEntitiesOfClass(PlayerMannequinEntity.class, new AABB(FLOOR).inflate(5))) old.discard();
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2),
                        "give @s createthrusters:custom_mannequin[custom_data={customName:\"Notch\"}] 2");
                ItemStack stack = player.getMainHandItem();
                require(stack.is(CTItems.CUSTOM_MANNEQUIN.get()) && !stack.has(DataComponents.PROFILE),
                        "The give command did not create an unresolved custom item");
                var ctx = new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(FLOOR).add(0, .5, 0), Direction.UP, FLOOR, false));
                CTItems.CUSTOM_MANNEQUIN.get().useOn(ctx);
                CTItems.CUSTOM_MANNEQUIN.get().useOn(ctx);
            }catch(Throwable err){ failure = err; }
        });
    }

    // Inspect the final server state while profile resolution is allowed to run asynchronously
    @SubscribeEvent
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post evt){
        if(OUTPUT == null || !installed || ready || failure != null) return;
        try{
            var server = evt.getServer();
            if(server.getPlayerList().getPlayers().isEmpty()) return;
            var player = server.getPlayerList().getPlayers().getFirst();
            var entities = player.serverLevel().getEntitiesOfClass(PlayerMannequinEntity.class, new AABB(FLOOR).inflate(5));
            if(entities.isEmpty()) return;
            require(entities.size() == 1, "Repeated pending clicks spawned duplicate mannequins");
            ItemStack stack = player.getMainHandItem();
            require(stack.getCount() == 1, "Placement did not consume exactly one head");
            var skin = CTItems.CUSTOM_MANNEQUIN.get().skin(stack);
            var entity = entities.getFirst();
            require(skin != null && skin.skinUrl().equals(entity.remoteSkinUrl())
                            && skin.playerName().equals(entity.getCustomName().getString()),
                    "The item and mannequin have different names or skins");
            mannequinId = entity.getUUID();
            ready = true;
        }catch(Throwable err){ failure = err; }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }

    private static void finish(String result){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT).resolve("result.txt"), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        Minecraft.getInstance().stop();
    }

    // Render the custom item beside the vanilla head with the identical profile
    private static final class Preview extends Screen{
        private final ItemStack stack;
        private final PlayerMannequinEntity entity;

        private Preview(ItemStack stack, PlayerMannequinEntity entity){
            super(Component.literal("Custom mannequin validation"));
            this.stack = stack;
            this.entity = entity;
        }

        @Override public boolean isPauseScreen(){ return false; }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partial){
            gui.fill(0, 0, width, height, 0xff18202b);
            gui.drawString(font, "Custom item", 24, 18, 0xffffff);
            gui.drawString(font, "Vanilla head", 144, 18, 0xffffff);
            gui.pose().pushPose();
            gui.pose().translate(24, 38, 0);
            gui.pose().scale(5, 5, 5);
            gui.renderItem(stack, 0, 0);
            gui.renderItem(CTItems.CUSTOM_MANNEQUIN.get().headStack(stack), 24, 0);
            gui.pose().popPose();
            gui.drawString(font, "Spawned mannequin: " + entity.getCustomName().getString(), 24, 140, 0xffffff);
            InventoryScreen.renderEntityInInventoryFollowsAngle(gui, 270, 20, 390, 210, 75, 0, 0, 0, entity);
        }
    }
}
