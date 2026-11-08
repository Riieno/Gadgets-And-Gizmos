package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.StringReader;
import com.rieno.gadgetsandgizmos.lib.profile.PlayerSkinLookup;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

// Exercise command parsing, appearance, placement and recovery in the real server
@GameTestHolder("createthrusters_custom_mannequin")
@PrefixGameTestTemplate(false)
public final class CustomMannequinGameTests{
    private static final String NAME = "Custom_Player";
    private static final String URL = "https://textures.minecraft.net/texture/" + "a".repeat(64);

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void giveSyntaxAndHeadProfileMatch(GameTestHelper helper) throws Exception{
        var ctx = CommandBuildContext.simple(helper.getLevel().registryAccess(), helper.getLevel().enabledFeatures());
        var input = ItemArgument.item(ctx).parse(new StringReader(
                "createthrusters:custom_mannequin[custom_data={customName:\"" + NAME + "\"}]"));
        ItemStack stack = input.createItemStack(1, false);
        var item = CTItems.CUSTOM_MANNEQUIN.get();
        item.setProfile(stack, profile());
        helper.assertTrue(NAME.equals(item.playerName(stack)), "The simple give syntax lost customName");
        helper.assertTrue(item.headStack(stack).get(DataComponents.PROFILE).equals(stack.get(DataComponents.PROFILE)),
                "The rendered head used a different player profile");
        helper.assertTrue(URL.equals(item.skin(stack).skinUrl()), "The rendered profile lost its skin URL");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void placesCustomSkinAndPreservesRecovery(GameTestHelper helper){
        ItemStack stack = ready();
        Player player = player(helper, stack);
        var ctx = context(helper, player);
        helper.assertTrue(CTItems.CUSTOM_MANNEQUIN.get().useOn(ctx).consumesAction(), "Custom head placement failed");
        var entity = mannequin(helper);
        helper.assertTrue(NAME.equals(entity.getCustomName().getString()) && URL.equals(entity.remoteSkinUrl())
                        && entity.usesSlimSkin() && !entity.usesSteveSkin(), "The spawned appearance did not match the item");
        helper.assertTrue(entity.isShowArms() && entity.isNoBasePlate() && !entity.isCustomNameVisible(),
                "The mannequin lost its normal supporter defaults");
        helper.assertTrue(stack.isEmpty(), "Survival placement did not consume exactly one item");
        var tag = entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var loaded = CTEntityTypes.PLAYER_MANNEQUIN.get().create(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(NAME.equals(loaded.customMannequinName()) && URL.equals(loaded.remoteSkinUrl()),
                "Saving lost the custom mannequin identity");
        ItemStack pick = loaded.getPickResult();
        helper.assertTrue(pick.is(CTItems.CUSTOM_MANNEQUIN.get())
                        && NAME.equals(CTItems.CUSTOM_MANNEQUIN.get().playerName(pick)),
                "Picking a custom mannequin returned a built-in supporter");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void occupiedSpaceDoesNotConsumeTheHead(GameTestHelper helper){
        ItemStack stack = ready();
        Player player = player(helper, stack);
        var ctx = context(helper, player);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
        helper.assertTrue(CTItems.CUSTOM_MANNEQUIN.get().useOn(ctx) == InteractionResult.FAIL,
                "A mannequin was placed inside a solid block");
        helper.assertTrue(stack.getCount() == 1, "Blocked placement consumed the custom head");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void customHeadsUseCreateSeats(GameTestHelper helper){
        ItemStack stack = ready();
        Player player = player(helper, stack);
        var ctx = context(helper, player);
        helper.setBlock(new BlockPos(1, 1, 1), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:white_seat")));
        helper.assertTrue(CTItems.CUSTOM_MANNEQUIN.get().useOn(ctx).consumesAction(), "Custom head could not use a seat");
        var entity = mannequin(helper);
        helper.assertTrue(entity.isPassenger() && NAME.equals(entity.customMannequinName()),
                "Seat placement did not preserve the custom mannequin");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void editedNamesNeverReuseThePreviousSkin(GameTestHelper helper){
        ItemStack stack = ready();
        var data = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        data.putString("customName", "Other_Player");
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
        var item = CTItems.CUSTOM_MANNEQUIN.get();
        helper.assertTrue(item.skin(stack) == null && !item.headStack(stack).has(DataComponents.PROFILE),
                "Changing customName kept the previous player's skin");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void bundledSupporterPlacementIsPreserved(GameTestHelper helper){
        var variant = PlayerMannequinVariants.byIdOrDefault(PlayerMannequinVariants.DEFAULT_ID);
        ItemStack stack = SupporterHeads.createStack(variant);
        Player player = player(helper, stack);
        helper.assertTrue(SupporterMannequinPlacement.useOn(context(helper, player)).consumesAction(),
                "Normal supporter heads no longer place mannequins");
        var entity = mannequin(helper);
        helper.assertTrue(entity.customMannequinName().isBlank() && entity.getVariant().equals(variant),
                "A built-in supporter was converted to a custom mannequin");
        helper.succeed();
    }

    // Prepare a deterministic slim profile without requiring an online test account
    private static PlayerSkinLookup.LookupResult profile(){
        GameProfile profile = new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000001"), NAME);
        String value = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + URL
                + "\",\"metadata\":{\"model\":\"slim\"}}}}").getBytes(StandardCharsets.UTF_8));
        profile.getProperties().put("textures", new Property("textures", value));
        return PlayerSkinLookup.fromProfile(profile);
    }

    private static ItemStack ready(){
        ItemStack stack = CustomMannequinItem.createStack(NAME);
        CTItems.CUSTOM_MANNEQUIN.get().setProfile(stack, profile());
        return stack;
    }

    private static Player player(GameTestHelper helper, ItemStack stack){
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static UseOnContext context(GameTestHelper helper, Player player){
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false));
    }

    private static PlayerMannequinEntity mannequin(GameTestHelper helper){
        return helper.getLevel().getEntitiesOfClass(PlayerMannequinEntity.class,
                new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).inflate(.5)).stream().findFirst().orElseThrow();
    }
}
