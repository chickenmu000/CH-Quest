package com.ch.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class GuiListener implements Listener {
    private final CHQuest plugin;

    public GuiListener(CHQuest plugin) {
        this.plugin = plugin;
    }

    private boolean admin(Player p) {
        return p.hasPermission("chquest.admin");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        QuestManager m = plugin.manager();
        UUID u = p.getUniqueId();
        Inventory top = e.getView().getTopInventory();

        // ----- 모루 입력 -----
        if (top.getType() == InventoryType.ANVIL && m.inputs.containsKey(u)) {
            e.setCancelled(true);
            if (e.getRawSlot() != 2) return;
            String txt = QuestGui.readAnvil(e.getView());
            if (txt.isEmpty()) {
                QuestManager.msg(p, "&c이름칸에 내용을 입력해 주세요.");
                return;
            }
            InputSession s = m.inputs.remove(u);
            p.closeInventory();
            QuestGui.handleInput(plugin, p, s, txt);
            return;
        }

        if (!(top.getHolder() instanceof QuestHolder h)) return;
        if (h.mode.equals("reward")) return; // 보상 설정창은 자유롭게 넣고 뺄 수 있음
        e.setCancelled(true);
        if (!admin(p) && h.mode.startsWith("admin")) {
            p.closeInventory();
            return;
        }
        if (e.getClickedInventory() != top) return;

        int raw = e.getRawSlot();
        ClickType click = e.getClick();
        Quest q = m.quest(h.quest);

        switch (h.mode) {
            case "offer" -> {
                if (q == null) return;
                if (raw == 11) {
                    p.closeInventory();
                    QuestManager.title(p, "&c퀘스트 거절", "&f퀘스트 " + q.name + " 를 &c거절&f했습니다!", 2);
                } else if (raw == 15) {
                    p.closeInventory();
                    if (!h.canAccept) {
                        QuestManager.title(p, "&cError", "&f선행퀘스트를 먼저 클리어해주세요!", 2);
                        return;
                    }
                    m.accept(p, q);
                }
            }
            case "progress" -> {
                if (raw != 13 || q == null) return;
                if (click == ClickType.SHIFT_RIGHT) {
                    p.closeInventory();
                    m.giveUp(p);
                } else if (click == ClickType.LEFT) {
                    p.closeInventory();
                    m.tryClear(p, q);
                }
            }
            case "adminlist" -> adminList(p, h, raw);
            case "adminedit" -> adminEdit(p, h, q, raw, click, e.getCursor());
            default -> {
            }
        }
    }

    private void adminList(Player p, QuestHolder h, int raw) {
        QuestManager m = plugin.manager();
        if (raw == 45 && h.page > 1) {
            QuestGui.openAdmin(plugin, p, h.page - 1);
        } else if (raw == 53) {
            int pages = Math.max(1, (m.quests().size() + 44) / 45);
            if (h.page < pages) QuestGui.openAdmin(plugin, p, h.page + 1);
        } else if (raw == 49) {
            QuestGui.openAnvil(plugin, p, new InputSession("create", ""), "퀘스트 이름");
        } else {
            String qn = h.slotQuest.get(raw);
            if (qn != null) QuestGui.openEdit(plugin, p, qn, h.page);
        }
    }

    private void adminEdit(Player p, QuestHolder h, Quest q, int raw, ClickType click, ItemStack cursor) {
        QuestManager m = plugin.manager();
        if (q == null) return;
        switch (raw) {
            case 49 -> QuestGui.openAdmin(plugin, p, h.page);
            case 10 -> {
                p.closeInventory();
                m.binding.put(p.getUniqueId(), q.name);
                QuestManager.msg(p, "퀘스트 &f" + q.name + "&f 에 연결할 &eNPC를 우클릭&f하세요.");
            }
            case 12 -> {
                if (click == ClickType.RIGHT) {
                    q.prerequisite = null;
                    m.saveQuests();
                    QuestGui.openEdit(plugin, p, q.name, h.page);
                } else {
                    QuestGui.openAnvil(plugin, p, new InputSession("pre", q.name), "선행 퀘스트 이름 (해제 = 없음)");
                }
            }
            case 14 -> QuestGui.openReward(plugin, p, q, true, h.page);
            case 16 -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    m.delete(q.name);
                    QuestManager.msg(p, "퀘스트 &f" + q.name + "&f 를 삭제했습니다.");
                    QuestGui.openAdmin(plugin, p, h.page);
                }
            }
            case 28 -> {
                if (cursor == null || cursor.getType().isAir()) {
                    QuestManager.msg(p, "&c목표 아이템을 마우스 커서에 들고 이 버튼을 클릭하세요.");
                    return;
                }
                ItemStack one = cursor.clone();
                one.setAmount(1);
                InputSession s = new InputSession("itemamt", q.name);
                s.pending = one;
                QuestGui.openAnvil(plugin, p, s, "개수 (예: 10 / 10 회수)");
            }
            case 30 -> QuestGui.openAnvil(plugin, p, new InputSession("kill", q.name), "몹이름 마리수 (예: 핑크 돼지 10)");
            case 32 -> {
                q.repeat = !q.repeat;
                m.saveQuests();
                QuestGui.openEdit(plugin, p, q.name, h.page);
            }
            default -> {
                if (raw >= 36 && raw <= 47 && click == ClickType.SHIFT_RIGHT) {
                    int idx = raw - 36;
                    if (idx < q.goals.size()) {
                        q.goals.remove(idx);
                        m.resetProgress(q.name);
                        m.saveQuests();
                        QuestGui.openEdit(plugin, p, q.name, h.page);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.ANVIL && plugin.manager().inputs.containsKey(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (top.getHolder() instanceof QuestHolder h && !h.mode.equals("reward")) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        QuestManager m = plugin.manager();
        Inventory top = e.getView().getTopInventory();

        // 모루 입력 취소 (ESC)
        if (top.getType() == InventoryType.ANVIL && m.inputs.containsKey(p.getUniqueId())) {
            InputSession s = m.inputs.remove(p.getUniqueId());
            QuestManager.msg(p, "입력을 취소했습니다.");
            QuestGui.reopen(plugin, p, s.quest);
            return;
        }

        if (top.getHolder() instanceof QuestHolder h && h.mode.equals("reward")) {
            QuestGui.saveReward(plugin, p, h, top);
            if (h.fromGui) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) QuestGui.openEdit(plugin, p, h.quest, h.page);
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        plugin.manager().binding.remove(u);
        plugin.manager().inputs.remove(u);
        plugin.manager().hideKillBar(u);
        plugin.manager().savePlayers();
    }
}
