package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

// Verify configured Claw pickup through the dedicated server's real entity scan
@GameTestHolder("createthrusters_claw_pickup")
@PrefixGameTestTemplate(false)
public final class ClawPickupGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Pick up and release a mob on a dedicated server when entity grabbing is enabled
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void enabledClawPicksUpMobs(GameTestHelper helper){
        helper.assertTrue(FMLEnvironment.dist.isDedicatedServer(), "Run this regression on a dedicated server");
        boolean prev = CTConfigs.COMMON.clawCanGrabEntities.get();
        var claw = claw(helper);
        var mob = helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        mob.setNoAi(true);
        try{
            CTConfigs.COMMON.clawCanGrabEntities.set(true);
            placeTarget(claw, mob);
            claw.setComputerSignalOverride(15);
            claw.tick();
            held(helper, claw, mob);
            claw.setComputerSignalOverride(0);
            helper.assertTrue(!claw.isHoldingEntity(), "Opening the Claw did not release the mob");
        }finally{
            claw.forceRelease();
            mob.discard();
            CTConfigs.COMMON.clawCanGrabEntities.set(prev);
        }
        helper.succeed();
    }

    // Player entities must use the same enabled server pickup path as mobs
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void enabledClawPicksUpPlayers(GameTestHelper helper){
        boolean prev = CTConfigs.COMMON.clawCanGrabEntities.get();
        var claw = claw(helper);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ClawPickup"));
        player.setGameMode(GameType.SURVIVAL);
        placeTarget(claw, player);
        helper.getLevel().addNewPlayer(player);
        try{
            CTConfigs.COMMON.clawCanGrabEntities.set(true);
            claw.setComputerSignalOverride(15);
            player.hurtMarked = false;
            claw.tick();
            held(helper, claw, player);
            helper.assertTrue(player.hurtMarked, "The Claw did not sync its pull to the held player's client");
            claw.setComputerSignalOverride(0);
            helper.assertTrue(!claw.isHoldingEntity(), "Opening the Claw did not release the player");
        }finally{
            claw.forceRelease();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            CTConfigs.COMMON.clawCanGrabEntities.set(prev);
        }
        helper.succeed();
    }

    // Disabling entity grabbing still prevents both player and mob pickup
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void disabledClawLeavesLivingEntitiesAlone(GameTestHelper helper){
        boolean prev = CTConfigs.COMMON.clawCanGrabEntities.get();
        var claw = claw(helper);
        var mob = helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        mob.setNoAi(true);
        placeTarget(claw, mob);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ClawDisabled"));
        player.setGameMode(GameType.SURVIVAL);
        placeTarget(claw, player);
        helper.getLevel().addNewPlayer(player);
        try{
            CTConfigs.COMMON.clawCanGrabEntities.set(false);
            claw.setComputerSignalOverride(15);
            claw.tick();
            helper.assertTrue(!claw.isHoldingEntity(), "The Claw ignored the disabled entity pickup setting");
            helper.assertTrue(mob.getDeltaMovement().lengthSqr() == 0 && player.getDeltaMovement().lengthSqr() == 0,
                    "The disabled Claw pulled a living entity");
        }finally{
            claw.forceRelease();
            mob.discard();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            CTConfigs.COMMON.clawCanGrabEntities.set(prev);
        }
        helper.succeed();
    }

    // Create an unpowered Claw away from other block targets
    private static ClawBlockEntity claw(GameTestHelper helper){
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, CTBlocks.CLAW.get());
        return (ClawBlockEntity)helper.getBlockEntity(pos);
    }

    // Keep the target within pickup range with no initial motion
    private static void placeTarget(ClawBlockEntity claw, LivingEntity target){
        target.setNoGravity(true);
        target.setPos(Vec3.atCenterOf(claw.getBlockPos()).add(1, 0, 0));
        target.setDeltaMovement(Vec3.ZERO);
    }

    // Confirm the scanned entity is retained and receives motion toward the Claw
    private static void held(GameTestHelper helper, ClawBlockEntity claw, LivingEntity target){
        var data = claw.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(claw.isHoldingEntity() && data.hasUUID("GrabbedEntityUUID")
                && target.getUUID().equals(data.getUUID("GrabbedEntityUUID")), "The Claw did not pick up the requested entity");
        Vec3 direction = Vec3.atCenterOf(claw.getBlockPos()).subtract(target.position());
        helper.assertTrue(target.getDeltaMovement().dot(direction) > 0, "The Claw did not pull its held entity");
    }
}
