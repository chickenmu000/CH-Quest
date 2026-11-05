package com.ch.quest;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class KillListener implements Listener {
    private final CHQuest plugin;

    public KillListener(CHQuest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        plugin.manager().onKill(killer, e.getEntity());
    }
}
