package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockConfigPayload;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

// Edit Ship Dock settings
public class ShipDockScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/ship_dock.png");
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Ship dock position
    private final BlockPos pos;
    // Sub-level id
    private final UUID subLevelId;
    // Current dock name
    private String dockName;
    // Tracks whether refuel is set
    private boolean refuel;
    // Tracks whether restock is set
    private boolean restock;
    // Tracks whether packages are set
    private boolean packages;
    // Current name box
    private EditBox nameBox;
    // Current refuel button
    private Button refuelButton;
    // Current restock button
    private Button restockButton;
    // Current packages button
    private Button packagesButton;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock
    public ShipDockScreen(
            BlockPos pos,
            UUID subLevelId,
            String dockName,
            boolean refuel,
            boolean restock,
            boolean packages
    ) {
        super(Component.translatable("createthrusters.ship_dock.title"));
        this.pos = pos;
        this.subLevelId = subLevelId;
        this.dockName = dockName;
        this.refuel = refuel;
        this.restock = restock;
        this.packages = packages;
    }

    // Initialize the ship dock
    @Override
    protected void init() {
        int left = (width - 200) / 2;
        int top = (height - 127) / 2;
        nameBox = new EditBox(font, left + 24, top + 29, 152, 18,
                Component.translatable("createthrusters.ship_dock.name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(dockName);
        addRenderableWidget(nameBox);
        refuelButton = addRenderableWidget(Button.builder(serviceLabel("refuel", refuel), btn -> {
            refuel = !refuel;
            btn.setMessage(serviceLabel("refuel", refuel));
        }).bounds(left + 24, top + 54, 132, 18).build());
        restockButton = addRenderableWidget(Button.builder(serviceLabel("restock", restock), btn -> {
            restock = !restock;
            btn.setMessage(serviceLabel("restock", restock));
        }).bounds(left + 24, top + 75, 132, 18).build());
        packagesButton = addRenderableWidget(Button.builder(serviceLabel("packages", packages), btn -> {
            packages = !packages;
            btn.setMessage(serviceLabel("packages", packages));
        }).bounds(left + 24, top + 96, 132, 18).build());
        IconButton confirmButton = new IconButton(
                left + 167, top + 103, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the service label
    private Component serviceLabel(String service, boolean enabled) {
        return Component.translatable("createthrusters.ship_dock.service." + service,
                Component.literal(enabled ? "\u2714" : "\u2718"));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ship dock
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - 200) / 2;
        int top = (height - 127) / 2;
        graphics.blit(BACKGROUND, left, top, 0, 111, 200, 127, 256, 256);
        graphics.drawCenteredString(font, title, width / 2, top + 9, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Handle the close event
    @Override
    public void onClose() {
        dockName = nameBox == null ? dockName : nameBox.getValue();
        PacketDistributor.sendToServer(new ShipDockConfigPayload(
                pos, subLevelId, dockName, refuel, restock, packages));
        super.onClose();
    }
}
