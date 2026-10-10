package me.knb;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class KnbHolder implements InventoryHolder {

    public enum Type { MAIN, LIST, CREATE, PICK }

    public final Type type;
    public final int page;
    public final int gameId;
    /** слот -> id игры (для меню списка) */
    public final Map<Integer, Integer> slotToGame = new HashMap<>();
    private Inventory inventory;

    public KnbHolder(Type type, int page, int gameId) {
        this.type = type;
        this.page = page;
        this.gameId = gameId;
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
