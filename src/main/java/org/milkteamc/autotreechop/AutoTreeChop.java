package org.milkteamc.autotreechop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class AutoTreeChop extends JavaPlugin implements Listener, CommandExecutor {

    private Map<UUID, PlayerConfig> playerConfigs;
    private String enabledMessage;
    private String disabledMessage;
    private String noPermissionMessage;
    private String hitmaxusageMessage;
    private String hitmaxblockMessage;
    private String usageMessage;
    private String blocksBrokenMessage;

    private int maxUsesPerDay;
    private int maxBlocksPerDay;

    @Override
    public void onEnable() {
        org.milkteamc.autotreechop.Metrics metrics = new Metrics(this, 20053); //bstats

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("autotreechop").setExecutor(this);

        saveDefaultConfig();
        loadConfig();

        playerConfigs = new HashMap<>();
    }

    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                if (!configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        FileConfiguration defaultConfig = new YamlConfiguration();
        defaultConfig.set("messages.enabled", "��aAuto tree chopping enabled.");
        defaultConfig.set("messages.disabled", "��cAuto tree chopping disabled.");
        defaultConfig.set("messages.no-permission", "��cYou don't have permission to use this command.");
        defaultConfig.set("messages.hitmaxusage", "��cYou've reached the daily usage limit.");
        defaultConfig.set("messages.hitmaxblock", "��cYou have reached your daily block breaking limit.");
        defaultConfig.set("messages.usage", "��aYou have used the AutoTreeChop %current_uses%/%max_uses% times today.");
        defaultConfig.set("messages.blocks-broken", "��aYou have broken %current_blocks%/%max_blocks% blocks today.");
        defaultConfig.set("max-uses-per-day", 50);
        defaultConfig.set("max-blocks-per-day", 500);

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        enabledMessage = config.getString("messages.enabled");
        disabledMessage = config.getString("messages.disabled");
        noPermissionMessage = config.getString("messages.no-permission");
        hitmaxusageMessage = config.getString("messages.hitmaxusage");
        hitmaxblockMessage = config.getString("messages.hitmaxblock");
        usageMessage = config.getString("messages.usage");
        blocksBrokenMessage = config.getString("messages.blocks-broken");
        maxUsesPerDay = config.getInt("max-uses-per-day");
        maxBlocksPerDay = config.getInt("max-blocks-per-day");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("autotreechop")) {
            if (sender instanceof Player player) {

                UUID playerUUID = player.getUniqueId();
                PlayerConfig playerConfig = getPlayerConfig(playerUUID);

                if (args.length > 0 && args[0].equalsIgnoreCase("usage")) {
                    String usageMsg = usageMessage.replace("%current_uses%", String.valueOf(playerConfig.getDailyUses()))
                            .replace("%max_uses%", String.valueOf(maxUsesPerDay));
                    player.sendMessage(usageMsg);

                    String blocksMsg = blocksBrokenMessage.replace("%current_blocks%", String.valueOf(playerConfig.getDailyBlocksBroken()))
                            .replace("%max_blocks%", String.valueOf(maxBlocksPerDay));
                    player.sendMessage(blocksMsg);
                    return true;
                }

                if (!player.hasPermission("autotreechop.use")) {
                    player.sendMessage(noPermissionMessage);
                    return true;
                }

                boolean autoTreeChopEnabled = !playerConfig.isAutoTreeChopEnabled();
                playerConfig.setAutoTreeChopEnabled(autoTreeChopEnabled);

                if (autoTreeChopEnabled) {
                    player.sendMessage(enabledMessage);
                } else {
                    player.sendMessage(disabledMessage);
                }
            } else {
                sender.sendMessage("Only players can use this command.");
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        PlayerConfig playerConfig = getPlayerConfig(playerUUID);

        Block block = event.getBlock();
        Material material = block.getType();

        if (playerConfig.isAutoTreeChopEnabled() && isLog(material)) {

            if (!player.hasPermission("autotreechop.vip") && playerConfig.getDailyBlocksBroken() >= maxBlocksPerDay) {
                player.sendMessage(hitmaxblockMessage);
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            checkedLocations.clear();
            chopTree(block);

            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), new ItemStack(material));
            } else {
                player.getInventory().addItem(new ItemStack(material));
            }

            playerConfig.incrementDailyUses();
            playerConfig.incrementDailyBlocksBroken();
        }
    }

    private Set<Location> checkedLocations = new HashSet<>();

    private void chopTree(Block block) {
        if (checkedLocations.contains(block.getLocation())) {
            return;
        }
        checkedLocations.add(block.getLocation());

        if (isLog(block.getType())) {
            block.breakNaturally();
        } else {
            return;
        }

        for (int yOffset = -1; yOffset <= 1; yOffset++) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue;
                    }
                    Block relativeBlock = block.getRelative(xOffset, yOffset, zOffset);
                    chopTree(relativeBlock);
                }
            }
        }
    }


    private boolean isLog(Material material) {
        return material == Material.OAK_LOG ||
                material == Material.SPRUCE_LOG ||
                material == Material.BIRCH_LOG ||
                material == Material.JUNGLE_LOG ||
                material == Material.ACACIA_LOG ||
                material == Material.DARK_OAK_LOG ||
                material == Material.MANGROVE_LOG ||
                material == Material.CHERRY_LOG;
    }

    private PlayerConfig getPlayerConfig(UUID playerUUID) {
        PlayerConfig playerConfig = playerConfigs.get(playerUUID);
        if (playerConfig == null) {
            playerConfig = new PlayerConfig(playerUUID);
            playerConfigs.put(playerUUID, playerConfig);
        }
        return playerConfig;
    }

    private class PlayerConfig {
        private final File configFile;
        private final FileConfiguration config;

        private boolean autoTreeChopEnabled;
        private int dailyUses;
        private int dailyBlocksBroken;
        private LocalDate lastUseDate;

        public PlayerConfig(UUID playerUUID) {
            this.configFile = new File(getDataFolder() + "/cache", playerUUID.toString() + ".yml");
            this.config = YamlConfiguration.loadConfiguration(configFile);
            this.autoTreeChopEnabled = false;
            this.dailyUses = 0;
            this.dailyBlocksBroken = 0;
            this.lastUseDate = LocalDate.now();
            loadConfig();
            saveConfig();
        }

        private void loadConfig() {
            if (configFile.exists()) {
                autoTreeChopEnabled = config.getBoolean("autoTreeChopEnabled");
                dailyUses = config.getInt("dailyUses");
                dailyBlocksBroken = config.getInt("dailyBlocksBroken", 0);
                lastUseDate = LocalDate.parse(Objects.requireNonNull(config.getString("lastUseDate")));
            } else {
                config.set("autoTreeChopEnabled", autoTreeChopEnabled);
                config.set("dailyUses", dailyUses);
                config.set("dailyBlocksBroken", dailyBlocksBroken);
                String lastUseDateString = config.getString("lastUseDate");
                if (lastUseDateString != null) {
                    lastUseDate = LocalDate.parse(lastUseDateString);
                } else {
                    lastUseDate = LocalDate.now();
                    config.set("lastUseDate", lastUseDate.toString());
                    saveConfig();
                }
                saveConfig();
            }
        }

        private void saveConfig() {
            try {
                config.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean isAutoTreeChopEnabled() {
            return autoTreeChopEnabled;
        }

        public void setAutoTreeChopEnabled(boolean enabled) {
            this.autoTreeChopEnabled = enabled;
            config.set("autoTreeChopEnabled", enabled);
            saveConfig();
        }

        public int getDailyUses() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyUses = 0;
                lastUseDate = LocalDate.now();
                config.set("dailyUses", dailyUses);
                config.set("lastUseDate", lastUseDate.toString());
                saveConfig();
            }
            return dailyUses;
        }

        public void incrementDailyUses() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyUses = 0;
                lastUseDate = LocalDate.now();
            }
            dailyUses++;
            config.set("dailyUses", dailyUses);
            saveConfig();
        }
        public int getDailyBlocksBroken() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyBlocksBroken = 0;
                lastUseDate = LocalDate.now();
                config.set("dailyBlocksBroken", dailyBlocksBroken);
                config.set("lastUseDate", lastUseDate.toString());
                saveConfig();
            }
            return dailyBlocksBroken;
        }

        public void incrementDailyBlocksBroken() {
            if (!lastUseDate.equals(LocalDate.now())) {
                dailyBlocksBroken = 0;
                lastUseDate = LocalDate.now();
            }
            dailyBlocksBroken++;
            config.set("dailyBlocksBroken", dailyBlocksBroken);
            saveConfig();
        }

    }
}