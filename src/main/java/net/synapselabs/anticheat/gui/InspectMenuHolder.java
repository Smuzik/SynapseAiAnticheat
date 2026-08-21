package net.synapselabs.anticheat.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class InspectMenuHolder implements InventoryHolder {
    private final UUID targetId;
    private final String targetName;
    private Inventory inventory;

    public InspectMenuHolder(UUID targetId, String targetName) {
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
