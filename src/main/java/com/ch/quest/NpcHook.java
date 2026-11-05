package com.ch.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Citizens 연동 (리플렉션 - Citizens 가 없어도 컴파일/동작) */
public class NpcHook implements Listener {
    private final CHQuest plugin;
    private Method getClicker;
    private Method getNpc;
    private Method getId;
    private final Map<UUID, Long> last = new HashMap<>();

    public NpcHook(CHQuest plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        try {
            Class<? extends Event> evc = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent");
            Class<?> npcc = Class.forName("net.citizensnpcs.api.npc.NPC");
            getClicker = evc.getMethod("getClicker");
            getNpc = evc.getMethod("getNPC");
            getId = npcc.getMethod("getId");
            Bukkit.getPluginManager().registerEvent(evc, this, EventPriority.NORMAL, (l, e) -> handle(e), plugin);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Citizens 연동 실패: " + t);
            return false;
        }
    }

    private void handle(Event e) {
        try {
            Player p = (Player) getClicker.invoke(e);
            Object npc = getNpc.invoke(e);
            int id = (int) getId.invoke(npc);
            long now = System.currentTimeMillis();
            Long prev = last.get(p.getUniqueId());
            if (prev != null && now - prev < 300) return; // 한 번 클릭에 두 번 호출되는 것 방지
            last.put(p.getUniqueId(), now);
            plugin.manager().npcClick(p, id);
        } catch (Throwable t) {
            plugin.getLogger().warning("NPC 클릭 처리 오류: " + t);
        }
    }

    /** NPC 이름 (실패하면 번호) */
    public static String name(int id) {
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object reg = api.getMethod("getNPCRegistry").invoke(null);
            Class<?> regc = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            Object npc = regc.getMethod("getById", int.class).invoke(reg, id);
            if (npc == null) return "NPC 번호 " + id;
            Class<?> npcc = Class.forName("net.citizensnpcs.api.npc.NPC");
            String n = (String) npcc.getMethod("getName").invoke(npc);
            return "&e" + n + "&f";
        } catch (Throwable t) {
            return "NPC 번호 " + id;
        }
    }
}
