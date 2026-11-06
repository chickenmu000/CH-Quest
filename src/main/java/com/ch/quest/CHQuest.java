package com.ch.quest;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class CHQuest extends JavaPlugin {
    private QuestManager manager;

    public QuestManager manager() {
        return manager;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new QuestManager(this);
        manager.load();

        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new KillListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            if (new NpcHook(this).register()) getLogger().info("Citizens 연동 완료");
        } else {
            getLogger().warning("Citizens 가 없어 NPC 우클릭 퀘스트를 사용할 수 없습니다.");
        }

        QuestCommand cmd = new QuestCommand(this);
        for (String name : new String[]{"퀘스트", "q"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }

        // 30초마다 유저 진행도 저장
        Bukkit.getScheduler().runTaskTimer(this, () -> manager.savePlayers(), 600L, 600L);
        getLogger().info("CH-Quest 활성화 (퀘스트 " + manager.quests().size() + "개)");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.hideAllKillBars();
            manager.saveQuests();
            manager.dirty();
            manager.savePlayers();
        }
    }
}
