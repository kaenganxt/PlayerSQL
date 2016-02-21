package com.mengcraft.playersql.task;

import com.mengcraft.playersql.Config;
import com.mengcraft.playersql.EventExecutor;
import com.mengcraft.playersql.User;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Created on 16-1-2.
 */
public class FetchUserTask implements Runnable {

    private final String prefix = ChatColor.GRAY + "[" + ChatColor.RED + "SYSTEM" + ChatColor.GRAY + "] " + ChatColor.RESET;

    private EventExecutor executor;
    private UUID uuid;

    private int taskId;
    private int retryCount;

    private final boolean isReload;

    public FetchUserTask(boolean isReload) {
        this.isReload = isReload;
    }

    public FetchUserTask() {
        this(false);
    }

    @Override
    public synchronized void run() {
        User user = this.executor.getUserManager().fetchUser(this.uuid);
        if (user == null) {
            this.executor.getUserManager().cacheUser(this.uuid);
            this.executor.getUserManager().saveUser(this.uuid, true);
            this.executor.getUserManager().createTask(this.uuid);
            this.executor.getUserManager().unlockUser(this.uuid, true);
            if (Config.DEBUG) {
                this.executor.getMain().logMessage("User data " + this.uuid + " not found!");
            }
            this.executor.cancelTask(this.taskId);
            if (!isReload) {
                Player P = Bukkit.getPlayer(this.uuid);
                if (P != null) this.executor.getUserManager().fireSafeLogin(P);
            }
        } else if (!isReload && user.isLocked() && this.retryCount++ < 8) {
            if (this.retryCount > 1) {
                Player P = Bukkit.getPlayer(this.uuid);
                P.sendMessage(prefix + ChatColor.YELLOW + "Nutzerdaten werden geladen...");
            } else if (this.retryCount > 3 && this.retryCount % 2 == 0) {
                Player P = Bukkit.getPlayer(this.uuid);
                P.sendMessage(prefix + ChatColor.RED + "Lädt noch, bitte warten...");
            }
            if (Config.DEBUG) {
                this.executor.getMain().logMessage("Load user data " + uuid + " fail " + retryCount + '.');
            }
        } else {
            this.executor.getUserManager().saveUser(user, true);
            if (Config.DEBUG) {
                this.executor.getMain().logMessage("Lock user data " + uuid + " done.");
            }

            this.executor.getUserManager().cacheUser(this.uuid, user);
            this.executor.getUserManager().addFetched(user, !isReload);

            if (Config.DEBUG) {
                this.executor.getMain().logMessage("Load user data " + uuid + " done.");
            }

            if (retryCount > 1) {
                Player P = Bukkit.getPlayer(this.uuid);
                P.sendMessage(prefix + ChatColor.GREEN + "Nutzerdaten geladen!");
            }

            this.executor.cancelTask(this.taskId);
        }
    }

    public void setExecutor(EventExecutor executor) {
        this.executor = executor;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

}
