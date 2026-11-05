package com.ch.quest;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.InventoryView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 모든 GUI 생성 */
public final class QuestGui {
    private QuestGui() {
    }

    private static ItemStack pane() {
        return T.item(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static Inventory make(QuestHolder h, int rows, String title) {
        Inventory inv = Bukkit.createInventory(h, rows * 9, T.c(title));
        h.inv = inv;
        return inv;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private static void open(Player p, Inventory inv) {
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.3f, 1f);
    }

    // ------------------------------------------------------------ 수락 / 거절

    public static void openOffer(CHQuest plugin, Player p, Quest q) {
        QuestManager m = plugin.manager();
        QuestHolder h = new QuestHolder("offer", q.name);
        Inventory inv = make(h, 3, QuestManager.PREFIX + q.name);
        fill(inv);

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (q.repeat) lore.add("    &b[반복 퀘스트]");
        lore.add("    &7&c&l[!]&f 퀘스트 수행 목록");
        for (int i = 0; i < q.goals.size(); i++) lore.add(m.goalText(p, q, i, false));
        lore.add("");
        ItemStack book = T.item(Material.BOOK, "&6[Q] &f" + q.name);
        T.setLore(book, lore);
        inv.setItem(13, book);

        inv.setItem(11, T.item(Material.RED_STAINED_GLASS_PANE, "&c(거절) &f퀘스트 거절",
                "", "  &e클릭시 &f퀘스트 &6" + q.name + " &e를 거절합니다!", ""));
        h.canAccept = true;
        if (q.prerequisite != null && !m.data(p.getUniqueId()).done.contains(q.prerequisite)) {
            h.canAccept = false;
            inv.setItem(15, T.item(Material.GRAY_STAINED_GLASS_PANE, "&c(Error) &f퀘스트 부여 실패",
                    "", "  &e퀘스트 &6" + q.prerequisite + " &e(을)를 클리어해야 수락이 가능합니다", ""));
        } else {
            inv.setItem(15, T.item(Material.LIME_STAINED_GLASS_PANE, "&a(수락) &f퀘스트 수락",
                    "", "  &e클릭시 &f퀘스트 &6" + q.name + " &e를 수락합니다!", ""));
        }
        open(p, inv);
    }

    // ------------------------------------------------------------ 진행도 (/q)

    public static void openProgress(CHQuest plugin, Player p) {
        QuestManager m = plugin.manager();
        PlayerData d = m.data(p.getUniqueId());
        if (d.current == null) {
            QuestManager.msg(p, "진행중인 퀘스트가 없습니다.");
            return;
        }
        Quest q = m.quest(d.current);
        if (q == null) {
            d.current = null;
            d.progress.clear();
            m.dirty();
            QuestManager.msg(p, "진행중이던 퀘스트가 삭제되어 초기화되었습니다.");
            return;
        }
        QuestHolder h = new QuestHolder("progress", q.name);
        Inventory inv = make(h, 3, QuestManager.PREFIX + q.name);
        fill(inv);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("    &7&c&l[!]&f 퀘스트 수행 목록");
        for (int i = 0; i < q.goals.size(); i++) lore.add(m.goalText(p, q, i, true));
        lore.add("");
        lore.add("  &7쉬프트+우클릭시 퀘스트를 포기합니다.");
        lore.add("  &7좌클릭시 퀘스트를 클리어 합니다.");
        lore.add("");
        ItemStack book = T.item(Material.BOOK, "&6[Q] &f" + q.name);
        T.setLore(book, lore);
        inv.setItem(13, book);
        open(p, inv);
    }

    // ------------------------------------------------------------ 관리자 : 목록 (페이지)

    public static void openAdmin(CHQuest plugin, Player p, int page) {
        QuestManager m = plugin.manager();
        List<Quest> all = new ArrayList<>(m.quests());
        int pages = Math.max(1, (all.size() + 44) / 45);
        if (page > pages) page = pages;
        if (page < 1) page = 1;

        QuestHolder h = new QuestHolder("adminlist", "");
        h.page = page;
        Inventory inv = make(h, 6, QuestManager.PREFIX + "퀘스트 관리 (" + page + "/" + pages + ")");
        fill(inv);

        int start = (page - 1) * 45;
        for (int s = 0; s < 45 && start + s < all.size(); s++) {
            Quest q = all.get(start + s);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("  &7NPC 번호 : &f" + (q.npc == null ? "&c미연결" : q.npc));
            lore.add("  &7선행 퀘스트 : &f" + (q.prerequisite == null ? "&7없음" : q.prerequisite));
            lore.add("  &7타입 : " + (q.repeat ? "&b반복" : "&f일반"));
            lore.add("  &7목표 : &f" + q.goals.size() + "개  &7보상 : &f" + q.rewards.size() + "개");
            lore.add("");
            lore.add("  &e클릭해서 편집");
            ItemStack book = T.item(Material.BOOK, "&6[Q] &f" + q.name);
            T.setLore(book, lore);
            inv.setItem(s, book);
            h.slotQuest.put(s, q.name);
        }
        if (page > 1) {
            inv.setItem(45, T.item(Material.ARROW, "&f< 이전 페이지", "", "  &7" + (page - 1) + " / " + pages, ""));
        }
        if (page < pages) {
            inv.setItem(53, T.item(Material.ARROW, "&f다음 페이지 >", "", "  &7" + (page + 1) + " / " + pages, ""));
        }
        inv.setItem(49, T.item(Material.NETHER_STAR, "&a+ 새 퀘스트 만들기", "", "  &e클릭 후 모루로 이름 입력", ""));
        open(p, inv);
    }

    // ------------------------------------------------------------ 관리자 : 편집

    public static void openEdit(CHQuest plugin, Player p, String name, int listPage) {
        QuestManager m = plugin.manager();
        Quest q = m.quest(name);
        if (q == null) {
            openAdmin(plugin, p, listPage);
            return;
        }
        QuestHolder h = new QuestHolder("adminedit", q.name);
        h.page = listPage;
        Inventory inv = make(h, 6, QuestManager.PREFIX + "&6" + q.name + " &f편집");
        fill(inv);

        inv.setItem(10, T.item(Material.NAME_TAG, "&eNPC 연결",
                "", "  &7현재 : &f" + (q.npc == null ? "&c미연결" : q.npc), "  &e클릭 후 연결할 NPC 우클릭", ""));
        inv.setItem(12, T.item(Material.PAPER, "&e선행 퀘스트",
                "", "  &7현재 : &f" + (q.prerequisite == null ? "&7없음" : q.prerequisite),
                "  &e좌클릭 : 모루로 입력", "  &e우클릭 : 해제", ""));
        inv.setItem(14, T.item(Material.CHEST, "&e보상 설정",
                "", "  &7현재 : &f" + q.rewards.size() + "개", "  &e클릭 : 보상 GUI (닫으면 저장)", ""));
        inv.setItem(16, T.item(Material.BARRIER, "&c퀘스트 삭제", "", "  &c쉬프트+우클릭 : 삭제", ""));
        inv.setItem(28, T.item(Material.HOPPER, "&a+ 목표 추가 : 아이템 가져오기",
                "", "  &7목표 아이템을 &e마우스 커서에 들고", "  &7이 버튼을 클릭하세요", ""));
        inv.setItem(30, T.item(Material.IRON_SWORD, "&a+ 목표 추가 : 몬스터 처치",
                "", "  &e클릭 후 모루로 입력", "  &7예: 핑크 돼지 10 (이름 + 마리수)", ""));
        if (q.repeat) {
            inv.setItem(32, T.item(Material.CLOCK, "&e퀘스트 타입 : &b반복",
                    "", "  &7클리어 후에도 계속 다시 받을 수 있습니다.", "  &e클릭 : 일반으로 전환", ""));
        } else {
            inv.setItem(32, T.item(Material.BOOK, "&e퀘스트 타입 : &f일반",
                    "", "  &7한 번 클리어하면 다시 받을 수 없습니다.", "  &e클릭 : 반복으로 전환", ""));
        }
        for (int i = 0; i < q.goals.size() && i < 12; i++) {
            Goal g = q.goals.get(i);
            ItemStack show;
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(m.goalText(p, q, i, false));
            if (g.type == Goal.Type.ITEM) {
                show = g.item.clone();
                show.setAmount(1);
                if (g.recall) lore.add("  &7클리어 시 아이템 &c회수");
            } else {
                show = new ItemStack(Material.IRON_SWORD);
            }
            lore.add("");
            lore.add("  &c쉬프트+우클릭 : 목표 삭제");
            T.setLore(show, lore);
            inv.setItem(36 + i, show);
        }
        inv.setItem(49, T.item(Material.ARROW, "&f뒤로 (퀘스트 목록)"));
        open(p, inv);
    }

    // ------------------------------------------------------------ 보상 설정

    public static void openReward(CHQuest plugin, Player p, Quest q, boolean fromGui, int listPage) {
        QuestHolder h = new QuestHolder("reward", q.name);
        h.fromGui = fromGui;
        h.page = listPage;
        Inventory inv = make(h, 6, QuestManager.PREFIX + "퀘스트 &6" + q.name + " &f보상 설정창");
        for (int i = 0; i < q.rewards.size() && i < 54; i++) inv.setItem(i, q.rewards.get(i).clone());
        p.openInventory(inv);
    }

    public static void saveReward(CHQuest plugin, Player p, QuestHolder h, Inventory inv) {
        Quest q = plugin.manager().quest(h.quest);
        if (q == null) return;
        q.rewards.clear();
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) q.rewards.add(it.clone());
        }
        plugin.manager().saveQuests();
        QuestManager.msg(p, "&a퀘스트 " + q.name + " 의 보상을 저장했습니다. &7(아이템 " + q.rewards.size() + "개)");
    }

    // ------------------------------------------------------------ 모루 입력

    public static void openAnvil(CHQuest plugin, Player p, InputSession session, String hint) {
        UUID u = p.getUniqueId();
        plugin.manager().inputs.remove(u);
        InventoryView view = p.openAnvil(p.getLocation(), true);
        if (view == null) {
            QuestManager.msg(p, "&c모루 창을 열 수 없습니다.");
            return;
        }
        view.getTopInventory().setItem(0, T.item(Material.PAPER, "&f입력"));
        plugin.manager().inputs.put(u, session);
        QuestManager.msg(p, "모루 이름칸에 &e" + hint + "&f 를 입력하고 &a오른쪽 결과 아이템을 클릭&f하세요. &7(ESC = 취소)");
    }

    /** 모루 이름칸 텍스트 읽기 */
    public static String readAnvil(InventoryView view) {
        String txt = null;
        if (view instanceof AnvilView av) txt = av.getRenameText();
        if (txt == null || txt.isEmpty()) {
            ItemStack res = view.getTopInventory().getItem(2);
            if (res != null && res.hasItemMeta() && res.getItemMeta().hasDisplayName()) {
                txt = T.plain(res.getItemMeta().displayName());
            }
        }
        return txt == null ? "" : txt.trim();
    }

    /** 입력 확정 처리 */
    public static void handleInput(CHQuest plugin, Player p, InputSession s, String text) {
        QuestManager m = plugin.manager();
        String qn = s.quest;
        String[] w = text.split("\\s+");
        switch (s.mode) {
            case "create" -> {
                String nm = w[0];
                if (nm.contains(".")) {
                    QuestManager.msg(p, "&c퀘스트 이름에는 '.' 을 쓸 수 없습니다.");
                    qn = "";
                } else if (m.create(nm) == null) {
                    QuestManager.msg(p, "&c이미 존재하는 퀘스트 이름입니다.");
                    qn = "";
                } else {
                    QuestManager.msg(p, "퀘스트 &6" + nm + "&f 를 만들었습니다.");
                    qn = nm;
                }
            }
            case "pre" -> {
                Quest q = m.quest(qn);
                String pre = w[0];
                if (q == null) break;
                if (pre.equals("없음")) {
                    q.prerequisite = null;
                    m.saveQuests();
                    QuestManager.msg(p, "선행 퀘스트를 해제했습니다.");
                } else if (m.quest(pre) == null) {
                    QuestManager.msg(p, "&c존재하지 않는 퀘스트입니다.");
                } else if (pre.equals(qn)) {
                    QuestManager.msg(p, "&c자기 자신을 선행 퀘스트로 지정할 수 없습니다.");
                } else {
                    q.prerequisite = pre;
                    m.saveQuests();
                    QuestManager.msg(p, "선행 퀘스트 = &6" + pre);
                }
            }
            case "kill" -> {
                Quest q = m.quest(qn);
                if (q == null) break;
                int amt = parse(w[w.length - 1]);
                String mob = String.join(" ", java.util.Arrays.copyOfRange(w, 0, w.length - 1)).trim();
                if (w.length < 2 || amt < 1 || mob.isEmpty()) {
                    QuestManager.msg(p, "&c형식: 몹이름 마리수 (예: 핑크 돼지 10)");
                } else if (q.goals.size() >= m.maxGoals()) {
                    QuestManager.msg(p, "&c목표는 최대 " + m.maxGoals() + "개까지 가능합니다.");
                } else {
                    Goal g = new Goal();
                    g.type = Goal.Type.KILL;
                    g.mob = mob;
                    g.amount = amt;
                    q.goals.add(g);
                    m.resetProgress(q.name);
                    m.saveQuests();
                    QuestManager.msg(p, "목표 " + q.goals.size() + "번 추가: " + mob + " 처치");
                }
            }
            case "itemamt" -> {
                Quest q = m.quest(qn);
                if (q == null || s.pending == null) break;
                int amt = parse(w[0]);
                if (amt < 1) {
                    QuestManager.msg(p, "&c개수를 1 이상의 숫자로 입력하세요.");
                } else if (q.goals.size() >= m.maxGoals()) {
                    QuestManager.msg(p, "&c목표는 최대 " + m.maxGoals() + "개까지 가능합니다.");
                } else {
                    Goal g = new Goal();
                    g.type = Goal.Type.ITEM;
                    g.item = s.pending;
                    g.amount = amt;
                    g.recall = w.length > 1 && w[1].equals("회수");
                    q.goals.add(g);
                    m.resetProgress(q.name);
                    m.saveQuests();
                    QuestManager.msg(p, "목표 " + q.goals.size() + "번 추가: 아이템 가져오기");
                }
            }
            default -> {
            }
        }
        reopen(plugin, p, qn);
    }

    public static void reopen(CHQuest plugin, Player p, String quest) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (quest == null || quest.isEmpty()) openAdmin(plugin, p, 1);
            else openEdit(plugin, p, quest, 1);
        });
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
