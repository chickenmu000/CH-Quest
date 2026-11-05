package com.ch.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 퀘스트 데이터 + 진행 로직 */
public class QuestManager {
    public static final String PREFIX = "&6[Quest]&f ";

    private final CHQuest plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final File questFile;
    private final File playerFile;
    private boolean playersDirty;

    /** 관리자 NPC 연결 대기 (UUID -> 퀘스트 이름) */
    public final Map<UUID, String> binding = new HashMap<>();
    /** 모루 입력 세션 */
    public final Map<UUID, InputSession> inputs = new HashMap<>();

    public QuestManager(CHQuest plugin) {
        this.plugin = plugin;
        this.questFile = new File(plugin.getDataFolder(), "quests.yml");
        this.playerFile = new File(plugin.getDataFolder(), "players.yml");
    }

    // ------------------------------------------------------------ 메시지

    public static void msg(Player p, String s) {
        p.sendMessage(T.c(PREFIX + s));
    }

    public static void title(Player p, String t, String sub, double seconds) {
        p.showTitle(Title.title(T.c(t), T.c(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis((long) (seconds * 1000)), Duration.ofMillis(300))));
    }

    public int maxGoals() {
        return plugin.getConfig().getInt("max-goals", 12);
    }

    // ------------------------------------------------------------ 퀘스트 목록

    public Quest quest(String name) {
        return quests.get(name);
    }

    public Collection<Quest> quests() {
        return quests.values();
    }

    public Quest create(String name) {
        if (quests.containsKey(name)) return null;
        Quest q = new Quest(name);
        quests.put(name, q);
        saveQuests();
        return q;
    }

    public void delete(String name) {
        quests.remove(name);
        saveQuests();
    }

    // ------------------------------------------------------------ 플레이어 데이터

    public PlayerData data(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    public void dirty() {
        playersDirty = true;
    }

    public boolean isAvailable(UUID id, Quest q) {
        PlayerData d = data(id);
        return q.repeat || !d.done.contains(q.name);
    }

    /** 이 퀘스트를 진행 중인 모든 유저의 처치 진행도 초기화 (목표가 바뀌었을 때) */
    public void resetProgress(String questName) {
        for (PlayerData d : players.values()) {
            if (questName.equals(d.current)) d.progress.clear();
        }
        dirty();
    }

    // ------------------------------------------------------------ 진행도

    public int countItem(Player p, ItemStack target) {
        int c = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.isSimilar(target)) c += it.getAmount();
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off.isSimilar(target)) c += off.getAmount();
        return c;
    }

    public void removeItem(Player p, ItemStack target, int amount) {
        PlayerInventory inv = p.getInventory();
        int left = amount;
        ItemStack[] cont = inv.getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || !it.isSimilar(target)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) cont[i] = null;
            left -= take;
        }
        inv.setStorageContents(cont);
        if (left > 0) {
            ItemStack off = inv.getItemInOffHand();
            if (off.isSimilar(target)) {
                int take = Math.min(left, off.getAmount());
                off.setAmount(off.getAmount() - take);
                inv.setItemInOffHand(off.getAmount() <= 0 ? null : off);
            }
        }
    }

    public int progress(Player p, Quest q, int idx) {
        Goal g = q.goals.get(idx);
        int c;
        if (g.type == Goal.Type.ITEM) {
            c = countItem(p, g.item);
        } else {
            c = data(p.getUniqueId()).progress.getOrDefault(idx, 0);
        }
        return Math.min(c, g.amount);
    }

    public boolean complete(Player p, Quest q) {
        if (q.goals.isEmpty()) return false;
        for (int i = 0; i < q.goals.size(); i++) {
            if (progress(p, q, i) < q.goals.get(i).amount) return false;
        }
        return true;
    }

    public String goalText(Player p, Quest q, int idx, boolean showProgress) {
        Goal g = q.goals.get(idx);
        String st;
        if (showProgress) {
            int c = progress(p, q, idx);
            st = " &8(" + c + "/" + g.amount + ") " + (c >= g.amount ? "&a완료" : "&c미완료");
        } else {
            st = " &8(" + g.amount + "개)";
        }
        if (g.type == Goal.Type.ITEM) {
            return " &c└  &f<" + T.itemName(g.item) + "&f> 아이템 가져오기" + st;
        }
        if (g.mob.equals("아무")) return " &c└  &f아무 몬스터나 처치하기" + st;
        return " &c└  &f<" + g.mob + "&f> 몬스터 처치하기" + st;
    }

    public static boolean killMatch(String key, LivingEntity victim) {
        if (key.equals("아무")) return true;
        Component cn = victim.customName();
        if (cn == null) return false;
        return T.plain(cn).toLowerCase().contains(key.toLowerCase());
    }

    public void onKill(Player killer, LivingEntity victim) {
        PlayerData d = data(killer.getUniqueId());
        if (d.current == null) return;
        Quest q = quests.get(d.current);
        if (q == null) return;
        for (int i = 0; i < q.goals.size(); i++) {
            Goal g = q.goals.get(i);
            if (g.type != Goal.Type.KILL || !killMatch(g.mob, victim)) continue;
            int prog = d.progress.getOrDefault(i, 0);
            if (prog >= g.amount) continue;
            prog++;
            d.progress.put(i, prog);
            dirty();
            if (prog >= g.amount) {
                msg(killer, g.mob + " 처치하기 &a완료! &7(/q 에서 클리어하세요)");
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1f);
            } else {
                killer.sendActionBar(T.c("&6[Q] &f" + g.mob + " 처치 &e" + prog + "&7/&e" + g.amount));
            }
        }
    }

    // ------------------------------------------------------------ NPC / 수락 / 클리어

    public void npcClick(Player p, int id) {
        UUID u = p.getUniqueId();
        String bq = binding.remove(u);
        if (bq != null) {
            Quest q = quests.get(bq);
            if (q != null) {
                q.npc = id;
                saveQuests();
                msg(p, "&a퀘스트 &f" + bq + "&a 에 NPC 번호 &f" + id + "&a 를 연결했습니다.");
            }
            return;
        }
        List<Quest> mine = new ArrayList<>();
        for (Quest q : quests.values()) {
            if (q.npc != null && q.npc == id) mine.add(q);
        }
        if (mine.isEmpty()) return;
        PlayerData d = data(u);
        if (d.current != null) {
            Quest cur = quests.get(d.current);
            if (cur != null && cur.npc != null && cur.npc == id) {
                QuestGui.openProgress(plugin, p);
            } else {
                title(p, "&7다른 퀘스트를 진행중입니다.", "&f/q 를 이용해 현재 퀘스트를 확인하거나 포기할 수 있습니다!", 2);
            }
            return;
        }
        for (Quest q : mine) {
            if (isAvailable(u, q)) {
                QuestGui.openOffer(plugin, p, q);
                return;
            }
        }
        title(p, "&7클리어한 퀘스트 입니다", "", 2);
        for (Quest q : mine) nextInfo(p, q.name);
    }

    public void accept(Player p, Quest q) {
        PlayerData d = data(p.getUniqueId());
        if (d.current != null) {
            title(p, "&7다른 퀘스트를 진행중입니다.", "", 2);
            return;
        }
        if (!isAvailable(p.getUniqueId(), q)) return;
        d.current = q.name;
        d.progress.clear();
        dirty();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.75f);
        animate(p, "QuestAccept", "&6[Q] &f" + q.name + " 퀘스트를 수락하셨습니다!");
    }

    public void giveUp(Player p) {
        PlayerData d = data(p.getUniqueId());
        d.current = null;
        d.progress.clear();
        dirty();
        title(p, "&c퀘스트 포기", "&f당신은 퀘스트를 포기했습니다. &c해당 사항은 복구가 불가능합니다!", 2);
    }

    public int freeSlots(Player p) {
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) free++;
        }
        return free;
    }

    public void tryClear(Player p, Quest q) {
        PlayerData d = data(p.getUniqueId());
        if (!q.name.equals(d.current)) return;
        if (!complete(p, q)) {
            title(p, "&4클리어실패", "&f당신은 &6클리어하지 못한 퀘스트목록&f이 남아있습니다!", 2);
            return;
        }
        int need = q.rewards.size();
        if (need > 0 && freeSlots(p) < need) {
            title(p, "&c인벤토리 공간 부족", "&f빈 칸 &e" + need + "칸&f 이상을 비우고 다시 클리어해 주세요!", 3);
            msg(p, "보상을 받으려면 인벤토리 빈 칸이 &e" + need + "칸&f 필요합니다. 인벤토리를 비우고 다시 /q 에서 클리어해 주세요.");
            return;
        }
        for (Goal g : q.goals) {
            if (g.type == Goal.Type.ITEM && g.recall) removeItem(p, g.item, g.amount);
        }
        d.done.add(q.name);
        d.current = null;
        d.progress.clear();
        dirty();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.75f);
        animate(p, "QuestClear", "&6[Q] &f" + q.name + " 퀘스트를 클리어하셨습니다!");
        for (ItemStack r : q.rewards) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(r.clone());
            for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
        }
        nextInfo(p, q.name);
    }

    /** 이 퀘스트를 선행으로 가진 다음 퀘스트 안내 */
    public int nextInfo(Player p, String clearedName) {
        int k = 0;
        for (Quest nx : quests.values()) {
            if (!clearedName.equals(nx.prerequisite)) continue;
            if (!isAvailable(p.getUniqueId(), nx)) continue;
            k++;
            if (nx.npc != null) {
                msg(p, "다음 퀘스트 &6[Q] &f" + nx.name + " &7- " + NpcName.get(nx.npc) + " &f(을)를 찾아가세요!");
            } else {
                msg(p, "다음 퀘스트 &6[Q] &f" + nx.name + " &7(NPC 미지정)");
            }
        }
        return k;
    }

    // ------------------------------------------------------------ 타이틀 연출

    /** 글자가 한 칸씩 금색으로 채워지는 타이틀 */
    public void animate(Player p, String word, String sub) {
        Component subC = T.c(sub);
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (i <= word.length()) {
                    Component t = Component.text(word.substring(0, i), net.kyori.adventure.text.format.NamedTextColor.GOLD)
                            .append(Component.text(word.substring(i), net.kyori.adventure.text.format.NamedTextColor.WHITE));
                    p.showTitle(Title.title(t, subC,
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)));
                    i++;
                } else {
                    p.showTitle(Title.title(Component.text(word, net.kyori.adventure.text.format.NamedTextColor.GOLD), subC,
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------ 저장 / 불러오기

    public void load() {
        quests.clear();
        players.clear();
        if (questFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(questFile);
            ConfigurationSection root = y.getConfigurationSection("quests");
            if (root != null) {
                for (String name : root.getKeys(false)) {
                    ConfigurationSection s = root.getConfigurationSection(name);
                    if (s == null) continue;
                    Quest q = new Quest(name);
                    if (s.contains("npc")) q.npc = s.getInt("npc");
                    q.prerequisite = s.getString("prerequisite");
                    q.repeat = s.getBoolean("repeat", false);
                    ConfigurationSection gs = s.getConfigurationSection("goals");
                    if (gs != null) {
                        List<String> keys = new ArrayList<>(gs.getKeys(false));
                        keys.sort((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
                        for (String k : keys) {
                            ConfigurationSection gsec = gs.getConfigurationSection(k);
                            if (gsec == null) continue;
                            Goal g = new Goal();
                            g.type = Goal.Type.valueOf(gsec.getString("type", "KILL"));
                            g.item = gsec.getItemStack("item");
                            g.mob = gsec.getString("mob", "아무");
                            g.amount = gsec.getInt("amount", 1);
                            g.recall = gsec.getBoolean("recall", false);
                            q.goals.add(g);
                        }
                    }
                    List<?> rw = s.getList("rewards");
                    if (rw != null) {
                        for (Object o : rw) {
                            if (o instanceof ItemStack is) q.rewards.add(is);
                        }
                    }
                    quests.put(name, q);
                }
            }
        }
        if (playerFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(playerFile);
            for (String key : y.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection s = y.getConfigurationSection(key);
                    if (s == null) continue;
                    PlayerData d = new PlayerData();
                    d.current = s.getString("current");
                    ConfigurationSection ps = s.getConfigurationSection("progress");
                    if (ps != null) {
                        for (String pk : ps.getKeys(false)) d.progress.put(Integer.parseInt(pk), ps.getInt(pk));
                    }
                    d.done.addAll(s.getStringList("done"));
                    players.put(id, d);
                } catch (IllegalArgumentException ignored) {
                    // 잘못된 키는 무시
                }
            }
        }
    }

    public void saveQuests() {
        YamlConfiguration y = new YamlConfiguration();
        for (Quest q : quests.values()) {
            String b = "quests." + q.name;
            if (q.npc != null) y.set(b + ".npc", q.npc);
            if (q.prerequisite != null) y.set(b + ".prerequisite", q.prerequisite);
            y.set(b + ".repeat", q.repeat);
            for (int i = 0; i < q.goals.size(); i++) {
                Goal g = q.goals.get(i);
                String gb = b + ".goals." + (i + 1);
                y.set(gb + ".type", g.type.name());
                y.set(gb + ".amount", g.amount);
                if (g.type == Goal.Type.ITEM) {
                    y.set(gb + ".item", g.item);
                    y.set(gb + ".recall", g.recall);
                } else {
                    y.set(gb + ".mob", g.mob);
                }
            }
            y.set(b + ".rewards", q.rewards);
        }
        write(y, questFile);
    }

    public void savePlayers() {
        if (!playersDirty) return;
        playersDirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            PlayerData d = e.getValue();
            if (d.current == null && d.done.isEmpty()) continue;
            String b = e.getKey().toString();
            if (d.current != null) y.set(b + ".current", d.current);
            for (Map.Entry<Integer, Integer> pe : d.progress.entrySet()) {
                y.set(b + ".progress." + pe.getKey(), pe.getValue());
            }
            y.set(b + ".done", new ArrayList<>(d.done));
        }
        write(y, playerFile);
    }

    private void write(YamlConfiguration y, File f) {
        try {
            plugin.getDataFolder().mkdirs();
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().severe(f.getName() + " 저장 실패: " + ex);
        }
    }
}
