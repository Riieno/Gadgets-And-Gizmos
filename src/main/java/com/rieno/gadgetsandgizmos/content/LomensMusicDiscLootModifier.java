package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTLootModifiers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

// Add the encrypted music disc to matching loot tables
public class LomensMusicDiscLootModifier extends LootModifier {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<LomensMusicDiscLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
            .and(Codec.FLOAT.optionalFieldOf("chance", 0.1F).forGetter(modifier -> modifier.chance))
            .apply(instance, LomensMusicDiscLootModifier::new));

    private static final Set<ResourceLocation> MUSIC_DISC_LOOT_TABLES = Set.of(
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon"),
            ResourceLocation.withDefaultNamespace("chests/woodland_mansion"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_corridor"),
            ResourceLocation.withDefaultNamespace("chests/bastion_other"),
            ResourceLocation.withDefaultNamespace("chests/ancient_city"),
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/reward_unique"),
            ResourceLocation.withDefaultNamespace("chests/trial_chambers/reward_ominous_unique"),
            ResourceLocation.withDefaultNamespace("pots/trial_chambers/corridor"),
            ResourceLocation.withDefaultNamespace("archaeology/trail_ruins_rare")
    );
    private static final String SOPHISTICATED_BACKPACK_WRAPPER =
            "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper";

    private static final List<Supplier<? extends Item>> DISCS = List.of(
            CTItems.MUSIC_DISC_KINETIC_CURRENCY,
            CTItems.MUSIC_DISC_TWISTED_ALIVE,
            CTItems.MUSIC_DISC_UNPLUG_THE_EARTH
    );

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

    // Initialize the lomens music disc loot modifier
    public LomensMusicDiscLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = Mth.clamp(chance, 0.0F, 1.0F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the music disc loot
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext ctx) {
        if (!MUSIC_DISC_LOOT_TABLES.contains(ctx.getQueriedLootTableId())
                || isSophisticatedBackpackLootGeneration()
                || ctx.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }

        Supplier<? extends Item> disc = DISCS.get(ctx.getRandom().nextInt(DISCS.size()));
        generatedLoot.add(new ItemStack(disc.get()));
        return generatedLoot;
    }

    // Check if this is sophisticated backpack loot generation
    private static boolean isSophisticatedBackpackLootGeneration() {
        for (StackTraceElement elm : Thread.currentThread().getStackTrace()) {
            if (SOPHISTICATED_BACKPACK_WRAPPER.equals(elm.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // Get the codec
    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CTLootModifiers.LOMENS_MUSIC_DISCS.get();
    }
}
