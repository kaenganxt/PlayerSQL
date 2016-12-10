package com.mengcraft.playersql;

import com.mengcraft.playersql.lib.*;
import com.mengcraft.playersql.task.FetchUserTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;

/**
 * Created on 16-1-2.
 */
public class PluginMain extends JavaPlugin {

    private EventExecutor eventExecutor;
    private static MySQL db;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveConfig();

        ItemUtil itemUtil = new ItemUtilHandler(this).handle();
        ExpUtil expUtil = new ExpUtilHandler(this).handle();

        db = new MySQL(this, "thetown");
        db.queryUpdate("CREATE TABLE IF NOT EXISTS `playerData` (" +
                    "  `uuid` varchar(40) NOT NULL," +
                    "  `health` double DEFAULT NULL," +
                    "  `food` int(11) DEFAULT NULL," +
                    "  `hand` int(11) DEFAULT NULL," +
                    "  `exp` int(11) DEFAULT NULL," +
                    "  `inventory` text," +
                    "  `armor` text," +
                    "  `chest` text," +
                    "  `effect` text," +
                    "  `locked` tinyint(1) DEFAULT '0'," +
                    "  `last_update` int(11) NOT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='PlayerSQL | kaenganxt'");

        UserManager userManager = UserManager.INSTANCE;
        userManager.setMain(this);
        userManager.setItemUtil(itemUtil);
        userManager.setExpUtil(expUtil);

        eventExecutor = new EventExecutor();
        eventExecutor.setMain(this);
        eventExecutor.setUserManager(userManager);

        getServer().getScheduler().runTaskTimer(this, userManager::pendFetched, Config.SYN_DELAY * 2, 1);

        getServer().getPluginManager().registerEvents(eventExecutor, this);

        for (Player P : Bukkit.getOnlinePlayers()) {
            userManager.lockUser(P.getUniqueId());
        }

        for (Player P : Bukkit.getOnlinePlayers()) {
            FetchUserTask task = new FetchUserTask(true);
            task.setUuid(P.getUniqueId());
            task.setExecutor(eventExecutor);
            if (Config.DEBUG) {
                logMessage("Scheduling user load after reload for " + P.getUniqueId() + '.');
            }
            task.setTaskId(runTaskTimerAsynchronously(task, Config.SYN_DELAY).getTaskId());
        }
    }

    @Override
    public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) {
            UserManager.INSTANCE.syncUser(p.getUniqueId(), true);
            UserManager.INSTANCE.saveUser(p.getUniqueId(), false);
        }
    }

    public static MySQL getDB() {
        return db;
    }

    public Player getPlayer(UUID uuid) {
        return getServer().getPlayer(uuid);
    }

    public void logException(Exception e) {
        getLogger().log(Level.WARNING, e.toString(), e);
    }

    public void logMessage(String s) {
        getLogger().log(Level.INFO, s);
    }

    public BukkitTask runTaskTimerAsynchronously(Runnable r, int i) {
        return getServer().getScheduler().runTaskTimerAsynchronously(this, r, i / 4, i);
    }

    public BukkitTask runTaskAsynchronously(Runnable r) {
        return getServer().getScheduler().runTaskAsynchronously(this, r);
    }

    public BukkitTask runTask(Runnable r) {
        return getServer().getScheduler().runTask(this, r);
    }

    public BukkitTask runTaskTimer(Runnable r, int i) {
        return getServer().getScheduler().runTaskTimer(this, r, i, i);
    }

    public EventExecutor getEventExecutor() {
        return eventExecutor;
    }

}
