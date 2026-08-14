package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Base64;

// Apply the addon's mob-head rules during NeoForge loot events
public final class CTMobHeadDrops {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float BASE_HEAD_DROP_CHANCE = 0.025F;
    private static final float LOOTING_BONUS_PER_LEVEL = 0.01F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT mob head drops
    private CTMobHeadDrops() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the living drops event
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (CTThrusterEntityDrops.handleLivingDrops(evt)) {
            return;
        }
        if (!CTConfigs.COMMON.enableMobHeadDrops.get()) {
            return;
        }

        LivingEntity killed = evt.getEntity();
        if (killed.level().isClientSide() || !(evt.getSource().getEntity() instanceof Player killer)) {
            return;
        }

        ItemStack head = createHeadDrop(killed);
        if (head.isEmpty()) {
            return;
        }

        float chance = BASE_HEAD_DROP_CHANCE + LOOTING_BONUS_PER_LEVEL * getLootingLevel(killer);
        if (killed.getRandom().nextFloat() >= chance) {
            return;
        }

        evt.getDrops().add(new ItemEntity(killed.level(), killed.getX(), killed.getY(), killed.getZ(), head));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the looting level
    private static int getLootingLevel(Player player) {
        Holder<Enchantment> looting = player.level()
            .registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.LOOTING);
        return EnchantmentHelper.getEnchantmentLevel(looting, player);
    }

    // Create the head drop
    private static ItemStack createHeadDrop(LivingEntity killed) {
        if (killed instanceof Player player) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(sanitizedProfile(player.getGameProfile())));
            return head;
        }

        EntityType<?> type = killed.getType();
        if (type == EntityType.WITHER_SKELETON) {
            return ItemStack.EMPTY;
        }
        if (type == EntityType.SKELETON) {
            return new ItemStack(Items.SKELETON_SKULL);
        }
        if (type == EntityType.ZOMBIE) {
            return new ItemStack(Items.ZOMBIE_HEAD);
        }
        if (type == EntityType.CREEPER) {
            return new ItemStack(Items.CREEPER_HEAD);
        }
        if (type == EntityType.PIGLIN) {
            return new ItemStack(Items.PIGLIN_HEAD);
        }
        if (type == EntityType.ENDER_DRAGON) {
            return new ItemStack(Items.DRAGON_HEAD);
        }

        ResourceLocation entityId = EntityType.getKey(type);
        ItemStack head = findTaggedSkull(entityId, "_head");
        return head.isEmpty() ? findTaggedSkull(entityId, "_skull") : head;
    }

    // Find the tagged skull
    private static ItemStack findTaggedSkull(ResourceLocation entityId, String suffix) {
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(entityId.getNamespace(), entityId.getPath() + suffix);
        return BuiltInRegistries.ITEM.getOptional(itemId)
            .filter(item -> item != Items.AIR)
            .map(ItemStack::new)
            .filter(stack -> stack.is(ItemTags.SKULLS))
            .orElse(ItemStack.EMPTY);
    }

    // Get the sanitized profile
    private static GameProfile sanitizedProfile(GameProfile profile) {
        GameProfile sanitized = new GameProfile(profile.getId(), profile.getName());
        for (Property property : profile.getProperties().values()) {
            if (isUsableProfileProperty(property)) {
                sanitized.getProperties().put(property.name(), property);
            }
        }
        return sanitized;
    }

    // Check if the profile property is usable
    private static boolean isUsableProfileProperty(Property property) {
        if (property == null) {
            return false;
        }
        if (!"textures".equals(property.name())) {
            return true;
        }
        if (isBlank(property.value()) || !isBase64Encoded(property.value())) {
            return false;
        }
        return !property.hasSignature() || (!isBlank(property.signature()) && isBase64Encoded(property.signature()));
    }

    // Check if this is base64 encoded
    private static boolean isBase64Encoded(String val) {
        try {
            Base64.getDecoder().decode(val);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    // Check if this is blank
    private static boolean isBlank(String val) {
        return val == null || val.isBlank();
    }
}
