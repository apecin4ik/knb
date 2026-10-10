package me.knb;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Knb extends JavaPlugin {

    private Economy econ;
    private Menus menus;
    private final Map<Integer, Game> games = new LinkedHashMap<>();
    /** игроки, которые выбрали ход и должны написать ставку в чат */
    private final Map<UUID, Choice> awaitingBet = new java.util.concurrent.ConcurrentHashMap<>();
    private int nextId = 1;

    private String prefix, currency;
    private long minBet, maxBet;
    private double commission;
    private int maxGames;
    private boolean broadcast;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault или плагин экономики не найден! Отключаюсь.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadSettings();
        menus = new Menus(this);
        getServer().getPluginManager().registerEvents(new KnbListener(this), this);
        getCommand("knb").setExecutor(this);
    }

    @Override
    public void onDisable() {
        // возвращаем все ставки открытых игр
        for (Game g : games.values()) {
            deposit(g.getCreator(), g.getBet());
        }
        games.clear();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private void loadSettings() {
        reloadConfig();
        prefix = color(getConfig().getString("prefix", "&6&lКНБ &8» &7"));
        currency = getConfig().getString("currency", "дублонов");
        minBet = Math.max(1, getConfig().getLong("min-bet", 100));
        maxBet = Math.max(minBet, getConfig().getLong("max-bet", 1000000));
        commission = Math.min(100, Math.max(0, getConfig().getDouble("commission-percent", 0)));
        maxGames = Math.max(1, getConfig().getInt("max-games-per-player", 3));
        broadcast = getConfig().getBoolean("broadcast-new-game", true);
    }

    // ---------- команда ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("knb.admin")) {
                sender.sendMessage(prefix + "§cНет прав.");
                return true;
            }
            loadSettings();
            sender.sendMessage(prefix + "§aКонфиг перезагружен.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("knb.use")) {
            p.sendMessage(prefix + "§cНет прав.");
            return true;
        }
        menus.openMain(p);
        return true;
    }

    // ---------- создание игры ----------

    public void createGame(Player p, Choice choice, long bet) {
        if (bet < minBet) {
            msg(p, "§cМинимальная ставка: §e" + fmt(minBet));
            return;
        }
        if (bet > maxBet) {
            msg(p, "§cМаксимальная ставка: §e" + fmt(maxBet));
            return;
        }
        long mine = games.values().stream().filter(g -> g.getCreator().equals(p.getUniqueId())).count();
        if (mine >= maxGames) {
            msg(p, "§cУ тебя уже максимум открытых игр (§e" + maxGames + "§c). Дождись соперника или отмени одну.");
            return;
        }
        if (!econ.has(p, bet)) {
            msg(p, "§cНе хватает денег. Баланс: §e" + fmt((long) econ.getBalance(p)));
            return;
        }
        if (!econ.withdrawPlayer(p, bet).transactionSuccess()) {
            msg(p, "§cНе удалось списать ставку.");
            return;
        }
        Game g = new Game(nextId++, p.getUniqueId(), p.getName(), choice, bet);
        games.put(g.getId(), g);

        msg(p, "§aИгра создана! Твой ход: " + choice.getColored() + "§a, ставка: §e" + fmt(bet)
                + "§a. Ставка заморожена до конца игры.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);

        if (broadcast) {
            Bukkit.broadcastMessage(prefix + "§e" + p.getName() + " §7создал игру на §a" + fmt(bet)
                    + "§7! Играть: §e/knb");
        }
    }

    public void cancelGame(Player p, int gameId) {
        Game g = games.get(gameId);
        if (g == null || !g.getCreator().equals(p.getUniqueId())) return;
        games.remove(gameId);
        deposit(g.getCreator(), g.getBet());
        msg(p, "§eИгра отменена, ставка §a" + fmt(g.getBet()) + " §eвозвращена.");
    }

    // ---------- сама игра ----------

    public void play(Player joiner, int gameId, Choice pick) {
        Game g = games.get(gameId);
        if (g == null) {
            msg(joiner, "§cЭта игра уже недоступна.");
            return;
        }
        if (g.getCreator().equals(joiner.getUniqueId())) {
            msg(joiner, "§cНельзя играть с самим собой.");
            return;
        }
        long bet = g.getBet();
        if (!econ.has(joiner, bet)) {
            msg(joiner, "§cНе хватает денег для ставки §e" + fmt(bet));
            return;
        }
        if (!econ.withdrawPlayer(joiner, bet).transactionSuccess()) {
            msg(joiner, "§cНе удалось списать ставку.");
            return;
        }
        games.remove(gameId);

        Choice host = g.getChoice();
        Player creator = Bukkit.getPlayer(g.getCreator());
        String head = "§8§m                    §r\n";

        if (pick == host) {
            // ничья — возвращаем обоим
            deposit(joiner.getUniqueId(), bet);
            deposit(g.getCreator(), bet);
            String text = "§e§lНИЧЬЯ! §7Оба выбрали " + pick.getColored() + "§7. Ставки возвращены.";
            result(joiner, g.getCreatorName(), pick, host, text, Sound.BLOCK_NOTE_BLOCK_PLING);
            if (creator != null) result(creator, joiner.getName(), host, pick, text, Sound.BLOCK_NOTE_BLOCK_PLING);
        } else if (pick.beats(host)) {
            // победил тот, кто принял игру
            long win = payoutFor(bet);
            deposit(joiner.getUniqueId(), win);
            result(joiner, g.getCreatorName(), pick, host,
                    "§a§lПОБЕДА! §7Ты получил §6" + fmt(win) + " §7(чистыми +" + fmt(win - bet) + ")", Sound.ENTITY_PLAYER_LEVELUP);
            if (creator != null) result(creator, joiner.getName(), host, pick,
                    "§c§lПОРАЖЕНИЕ. §7Ты потерял §c" + fmt(bet), Sound.ENTITY_VILLAGER_NO);
        } else {
            // победил создатель игры
            long win = payoutFor(bet);
            deposit(g.getCreator(), win);
            result(joiner, g.getCreatorName(), pick, host,
                    "§c§lПОРАЖЕНИЕ. §7Ты потерял §c" + fmt(bet), Sound.ENTITY_VILLAGER_NO);
            if (creator != null) result(creator, joiner.getName(), host, pick,
                    "§a§lПОБЕДА! §7Ты получил §6" + fmt(win) + " §7(чистыми +" + fmt(win - bet) + ")", Sound.ENTITY_PLAYER_LEVELUP);
        }
    }

    private void result(Player p, String opponent, Choice mine, Choice theirs, String text, Sound sound) {
        p.closeInventory();
        p.sendMessage("");
        p.sendMessage(prefix + "Игра против §e" + opponent);
        p.sendMessage("§7Твой ход: " + mine.getColored() + " §8| §7Ход соперника: " + theirs.getColored());
        p.sendMessage(text);
        p.sendMessage("");
        p.sendTitle(text.substring(0, Math.min(text.length(), text.indexOf(' ') > 0 ? text.indexOf(' ') : text.length())),
                "§7" + mine.getName() + " §8vs §7" + theirs.getName(), 5, 40, 10);
        p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    // ---------- экономика и утилиты ----------

    /** Сколько получит победитель (своя ставка + ставка соперника минус комиссия) */
    public long payoutFor(long bet) {
        double win = bet * (1.0 - commission / 100.0);
        return bet + (long) Math.floor(win);
    }

    private void deposit(UUID uuid, long amount) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        econ.depositPlayer(op, amount);
    }

    public String fmt(long amount) {
        return String.format("%,d", amount).replace(',', ' ') + " " + currency;
    }

    public void msg(Player p, String text) {
        p.sendMessage(prefix + text);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public Collection<Game> getGames() { return games.values(); }
    public Game getGame(int id) { return games.get(id); }
    public Menus getMenus() { return menus; }
    public Map<UUID, Choice> getAwaitingBet() { return awaitingBet; }
    public long getMinBet() { return minBet; }
    public long getMaxBet() { return maxBet; }
}
