package com.nexus.serverrules.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifies our review-queue inventory to GuiListener via
 * getHolder() instead of comparing titles (titles can theoretically
 * collide with something else; holder identity can't).
 */
public final class ReviewQueueHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
