package com.mengcraft.playersql;

import com.avaje.ebean.EbeanServer;
import com.mengcraft.playersql.lib.*;
import com.mengcraft.playersql.task.FetchUserTask;
import com.mengcraft.simpleorm.EbeanHandler;
import com.mengcraft.simpleorm.EbeanManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/**
 * Created on 16-1-2.
 */
public class PluginMain extends JavaPlugin {

    private EventExecutor eventExecutor;
    private Field server;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveConfig();

        ItemUtil itemUtil = new ItemUtilHandler(this).handle();
        ExpUtil expUtil = new ExpUtilHandler(this).handle();

        EbeanHandler db = EbeanManager.DEFAULT.getHandler(this);
        if (db.isNotInitialized()) {
            db.define(User.class);

            db.setMaxSize(getConfig().getInt("plugin.max-db-connection"));
            try {
                db.initialize();
            } catch (Exception e) {
                throw new PluginException("Can't connect to database!", e);
            }
        }
        db.install();
        db.reflect();

        UserManager userManager = UserManager.INSTANCE;
        userManager.setMain(this);
        userManager.setItemUtil(itemUtil);
        userManager.setExpUtil(expUtil);

        eventExecutor = new EventExecutor();
        eventExecutor.setMain(this);
        eventExecutor.setUserManager(userManager);

        getServer().getScheduler().runTaskTimer(this, userManager::pendFetched, Config.SYN_DELAY * 2, 1);

        getServer().getPluginManager().registerEvents(eventExecutor, this);

        try {
            new Metrics(this).start();
        } catch (IOException e) {
            logException(e);
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
            UserManager.INSTANCE.saveUser(p.getUniqueId(), false);
        }
    }

    @Override
    public EbeanServer getDatabase() {
        try {
            if (server == null) {
                server = JavaPlugin.class.getDeclaredField("ebean");
                server.setAccessible(true);
            }
            return (EbeanServer) server.get(this);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(PluginMain.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
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
