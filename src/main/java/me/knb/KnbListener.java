package me.knb;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class KnbListener implements Listener {

    private final Knb plugin;

    public KnbListener(Knb plugin) {
        this.plugin = plugin;
    }

    private static Choice choiceBySlot(int slot) {
        switch (slot) {
            case 11: return Choice.ROCK;
            case 13: return Choice.SCISSORS;
            case 15: return Choice.PAPER;
            default: return null;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof KnbHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof KnbHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        KnbHolder h = (KnbHolder) e.getInventory().getHolder();
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        Menus menus = plugin.getMenus();

        switch (h.type) {
            case MAIN:
                if (slot == 11) menus.openList(p, 0);
                else if (slot == 15) menus.openCreate(p);
                else if (slot == 22) p.closeInventory();
                break;

            case LIST:
                if (slot == 49) { menus.openMain(p); break; }
                if (slot == 48 && h.page > 0) { menus.openList(p, h.page - 1); break; }
                if (slot == 50) { menus.openList(p, h.page + 1); break; }
                Integer id = h.slotToGame.get(slot);
                if (id == null) break;
                Game g = plugin.getGame(id);
                if (g == null) {
                    plugin.msg(p, "§cЭта игра уже недоступна.");
                    menus.openList(p, h.page);
                } else if (g.getCreator().equals(p.getUniqueId())) {
                    plugin.cancelGame(p, id);
                    menus.openList(p, h.page);
                } else {
                    menus.openPick(p, g);
                }
                break;

            case CREATE:
                if (slot == 22) { menus.openMain(p); break; }
                Choice c = choiceBySlot(slot);
                if (c == null) break;
                p.closeInventory();
                plugin.getAwaitingBet().put(p.getUniqueId(), c);
                p.sendMessage("");
                plugin.msg(p, "Твой ход: " + c.getColored() + "§7. Напиши в чат §aсумму ставки §7(например §e2000§7 или §e5k§7).");
                plugin.msg(p, "Минимум: §e" + plugin.fmt(plugin.getMinBet()) + "§7, максимум: §e" + plugin.fmt(plugin.getMaxBet()));
                plugin.msg(p, "Для отмены напиши §ccancel §7или §cотмена§7.");
                p.sendMessage("");
                break;

            case PICK:
                if (slot == 22) { menus.openList(p, 0); break; }
                Choice pick = choiceBySlot(slot);
                if (pick == null) break;
                plugin.play(p, h.gameId, pick);
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Choice choice = plugin.getAwaitingBet().get(p.getUniqueId());
        if (choice == null) return;
        e.setCancelled(true);
        String text = e.getMessage().trim().toLowerCase();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equals("cancel") || text.equals("отмена")) {
                plugin.getAwaitingBet().remove(p.getUniqueId());
                plugin.msg(p, "§eСоздание игры отменено.");
                return;
            }
            Long amount = parseAmount(text);
            if (amount == null || amount <= 0) {
                plugin.msg(p, "§cЭто не похоже на сумму. Напиши число (например §e2000§c) или §ecancel§c.");
                return;
            }
            plugin.getAwaitingBet().remove(p.getUniqueId());
            plugin.createGame(p, choice, amount);
        });
    }

    /** Понимает: 2000, 2 000, 2,5k, 5k, 5к, 1m */
    private static Long parseAmount(String s) {
        try {
            s = s.replace(" ", "").replace(',', '.');
            double mult = 1;
            if (s.endsWith("k") || s.endsWith("к")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m") || s.endsWith("м")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v > 1e15) return null;
            return Math.round(v);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getAwaitingBet().remove(e.getPlayer().getUniqueId());
    }
}
