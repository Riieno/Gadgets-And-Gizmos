package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.EntityLauncherClawEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

// Register addon entity types
public final class CTEntityTypes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<EntityType<?>> REGISTRAR =
            DeferredRegister.create(Registries.ENTITY_TYPE, CreateThrusters.MOD_ID);

    @Nullable
    public static final DeferredHolder<EntityType<?>, EntityType<EntityLauncherClawEntity>> LAUNCHED_CLAW =
            REGISTRAR.register("launched_claw", () -> EntityType.Builder
                    .<EntityLauncherClawEntity>of(EntityLauncherClawEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("launched_claw"));

    @Nullable
    public static final DeferredHolder<EntityType<?>, EntityType<PlayerMannequinEntity>> PLAYER_MANNEQUIN =
            REGISTRAR.register("player_mannequin", () -> EntityType.Builder
                    .<PlayerMannequinEntity>of(PlayerMannequinEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .vehicleAttachment(Player.DEFAULT_VEHICLE_ATTACHMENT)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("player_mannequin"));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT entity types
    private CTEntityTypes() {
    }
}
