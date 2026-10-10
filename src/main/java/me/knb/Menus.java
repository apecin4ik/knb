package me.knb;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Menus {

    public static final int[] CHOICE_SLOTS = {11, 13, 15};
    private static final int PAGE_SIZE = 45;

    private final Knb plugin;

    public Menus(Knb plugin) {
        this.plugin = plugin;
    }

    // ---------- утилиты ----------

    public static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack bg = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack edge = item(Material.ORANGE_STAINED_GLASS_PANE, " ");
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean border = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, border ? edge : bg);
        }
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    // ---------- главное меню ----------

    public void openMain(Player p) {
        KnbHolder holder = new KnbHolder(KnbHolder.Type.MAIN, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 27, "§6§l✂ Камень · Ножницы · Бумага");
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(11, glow(item(Material.EMERALD, "§a§lИграть",
                "§7Список игр, которые создали",
                "§7другие игроки. Выбери соперника",
                "§7и попробуй его обыграть!",
                "",
                "§7Открытых игр: §e" + plugin.getGames().size(),
                "",
                "§e▶ Нажми, чтобы открыть")));

        inv.setItem(15, glow(item(Material.GOLD_INGOT, "§6§lСоздать игру",
                "§7Выбери ход и поставь сумму.",
                "§7Твоя игра появится в списке,",
                "§7и соперник сможет её принять.",
                "",
                "§7Минимальная ставка: §e" + plugin.fmt(plugin.getMinBet()),
                "",
                "§e▶ Нажми, чтобы создать")));

        inv.setItem(22, item(Material.BARRIER, "§c§lЗакрыть"));

        p.openInventory(inv);
        click(p);
    }

    // ---------- список игр ----------

    public void openList(Player p, int page) {
        List<Game> all = new ArrayList<>(plugin.getGames());
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        KnbHolder holder = new KnbHolder(KnbHolder.Type.LIST, page, -1);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lОткрытые игры §8(" + (page + 1) + "/" + (maxPage + 1) + ")");
        holder.setInventory(inv);

        ItemStack bg = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            Game g = all.get(start + i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(g.getCreator()));
            boolean mine = g.getCreator().equals(p.getUniqueId());
            meta.setDisplayName((mine ? "§b" : "§e") + "§l" + g.getCreatorName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Ставка: §a" + plugin.fmt(g.getBet()));
            lore.add("§7Выигрыш: §6" + plugin.fmt(plugin.payoutFor(g.getBet())));
            lore.add("§7Ход соперника: §8§k???");
            lore.add("");
            lore.add(mine ? "§c▶ Это твоя игра. Нажми, чтобы отменить" : "§a▶ Нажми, чтобы играть");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
            inv.setItem(i, head);
            holder.slotToGame.put(i, g.getId());
        }

        if (all.isEmpty()) {
            inv.setItem(22, item(Material.COBWEB, "§7Пока нет открытых игр",
                    "§7Создай свою — вернись в меню",
                    "§7и нажми «Создать игру»."));
        }

        if (page > 0) inv.setItem(48, item(Material.ARROW, "§e« Назад"));
        inv.setItem(49, item(Material.BARRIER, "§c§lВ меню"));
        if (page < maxPage) inv.setItem(50, item(Material.ARROW, "§eВперёд »"));

        p.openInventory(inv);
        click(p);
    }

    // ---------- создание игры: выбор хода ----------

    public void openCreate(Player p) {
        KnbHolder holder = new KnbHolder(KnbHolder.Type.CREATE, 0, -1);
        Inventory inv = Bukkit.createInventory(holder, 27, "§6§lСоздать игру §8» §7твой ход");
        holder.setInventory(inv);
        fill(inv);

        Choice[] choices = {Choice.ROCK, Choice.SCISSORS, Choice.PAPER};
        for (int i = 0; i < 3; i++) {
            Choice c = choices[i];
            inv.setItem(CHOICE_SLOTS[i], item(c.getIcon(), c.getColored(),
                    "§7Поставить на этот ход.",
                    "",
                    "§7Бьёт: §f" + beatenBy(c).getName(),
                    "",
                    "§e▶ Нажми, затем введи ставку в чат"));
        }
        inv.setItem(22, item(Material.ARROW, "§e« Назад"));

        p.openInventory(inv);
        click(p);
    }

    // ---------- принять игру: выбор хода ----------

    public void openPick(Player p, Game g) {
        KnbHolder holder = new KnbHolder(KnbHolder.Type.PICK, 0, g.getId());
        Inventory inv = Bukkit.createInventory(holder, 27, "§6§lИгра с " + g.getCreatorName() + " §8» §7" + plugin.fmt(g.getBet()));
        holder.setInventory(inv);
        fill(inv);

        Choice[] choices = {Choice.ROCK, Choice.SCISSORS, Choice.PAPER};
        for (int i = 0; i < 3; i++) {
            Choice c = choices[i];
            inv.setItem(CHOICE_SLOTS[i], item(c.getIcon(), c.getColored(),
                    "§7Ставка: §a" + plugin.fmt(g.getBet()),
                    "§7Победа: §6+" + plugin.fmt(g.getBet()),
                    "",
                    "§e▶ Нажми, чтобы сыграть этим ходом"));
        }
        inv.setItem(4, item(Material.PAPER, "§e§lВыбери свой ход",
                "§7Ход соперника скрыт.",
                "§7Ставка спишется после выбора."));
        inv.setItem(22, item(Material.ARROW, "§e« К списку игр"));

        p.openInventory(inv);
        click(p);
    }

    private Choice beatenBy(Choice c) {
        for (Choice o : Choice.values()) if (c.beats(o)) return o;
        return c;
    }
}
