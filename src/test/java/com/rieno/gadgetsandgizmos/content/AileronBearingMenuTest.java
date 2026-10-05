package com.rieno.gadgetsandgizmos.content;

import io.netty.buffer.Unpooled;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify the warning mode survives opening without a client block entity
class AileronBearingMenuTest {
    // Initialize Minecraft registries for menu items
    @BeforeAll
    static void bootstrap(){
        ControllerTestBootstrap.bootstrap();
    }

    // Read every head mode in the real client menu
    @Test
    void openingMenuCarriesHeadModeWithoutAClientBlockEntity(){
        for(var mode : AileronBearingBlockEntity.HeadMode.values()){
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), RegistryAccess.EMPTY);
            try{
                MenuOpenHeader.encode(buf, new net.minecraft.core.BlockPos(1, 64, 2), null);
                for(BearingHead head : BearingHead.values()){
                    buf.writeDouble(-45.0D);
                    buf.writeDouble(45.0D);
                }
                for(int i = 0; i < 8; i++){
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, ItemStack.EMPTY);
                }
                buf.writeEnum(mode);
                AileronBearingMenu menu = new AileronBearingMenu(null, 1,
                        new Inventory(mock(Player.class)), buf);
                assertNull(menu.getMenuConfigTargetBlockEntity());
                assertEquals(mode, menu.getHeadMode());
                assertEquals(0, buf.readableBytes());

                menu.setData(0, AileronBearingBlockEntity.HeadMode.PRECISE.ordinal());
                assertEquals(AileronBearingBlockEntity.HeadMode.PRECISE, menu.getHeadMode());
                menu.setData(0, AileronBearingBlockEntity.HeadMode.MIRRORED.ordinal());
                assertEquals(AileronBearingBlockEntity.HeadMode.MIRRORED, menu.getHeadMode());
            }finally{
                buf.release();
            }
        }
    }
}
