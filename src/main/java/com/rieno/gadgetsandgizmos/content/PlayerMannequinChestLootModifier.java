package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTLootModifiers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;

// Add mannequins with saved variants to configured chest loot
public class PlayerMannequinChestLootModifier extends LootModifier {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<PlayerMannequinChestLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
            .and(Codec.FLOAT.optionalFieldOf("chance", 0.05F).forGetter(modifier -> modifier.chance))
            .apply(instance, PlayerMannequinChestLootModifier::new));

    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String CHEST_TABLE_PREFIX = "chests/";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Chance
    private final float chance;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin chest loot modifier
    public PlayerMannequinChestLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = Mth.clamp(chance, 0.0F, 1.0F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the mannequin chest loot
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext ctx) {
        if (!CTFeatureToggles.isItemEnabled("player_mannequin")
                || !CTConfigs.COMMON.enableSpecialThanksMannequinLoot.get()
                || !isVanillaChestLoot(ctx.getQueriedLootTableId())
                || ctx.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }

        List<PlayerMannequinVariant> variants = PlayerMannequinVariants.all();
        if (!variants.isEmpty()) {
            PlayerMannequinVariant variant = variants.get(ctx.getRandom().nextInt(variants.size()));
            ItemStack stack = SupporterHeads.createStack(variant);
            if (!stack.isEmpty()) {
                generatedLoot.add(stack);
            }
        }
        return generatedLoot;
    }

    // Check if this is a vanilla chest loot
    private static boolean isVanillaChestLoot(ResourceLocation lootTableId) {
        return MINECRAFT_NAMESPACE.equals(lootTableId.getNamespace())
                && lootTableId.getPath().startsWith(CHEST_TABLE_PREFIX);
    }

    // Get the codec
    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CTLootModifiers.PLAYER_MANNEQUIN_CHESTS.get();
    }
}
