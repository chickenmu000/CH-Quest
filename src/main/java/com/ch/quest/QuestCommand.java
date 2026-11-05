package com.ch.quest;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /퀘스트 (관리자) , /q (유저) */
public class QuestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("생성", "삭제", "목록", "정보", "NPC연결", "목표", "목표삭제", "선행", "보상", "초기화");
    private final CHQuest plugin;

    public QuestCommand(CHQuest plugin) {
        this.plugin = plugin;
    }

    private void m(CommandSender s, String text) {
        s.sendMessage(T.c(QuestManager.PREFIX + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (cmd.getName().equals("q")) {
            if (!(sender instanceof Player p)) {
                m(sender, "플레이어만 사용할 수 있습니다.");
                return true;
            }
            QuestGui.openProgress(plugin, p);
            return true;
        }

        if (!sender.hasPermission("chquest.admin")) {
            m(sender, "&c권한이 없습니다.");
            return true;
        }
        QuestManager qm = plugin.manager();
        if (a.length == 0) {
            if (sender instanceof Player p) QuestGui.openAdmin(plugin, p, 1);
            else help(sender);
            return true;
        }
        String sub = a[0];

        if (sub.equals("목록")) {
            m(sender, "퀘스트 목록 (" + qm.quests().size() + "개)");
            for (Quest q : qm.quests()) sender.sendMessage(T.c("  &6[Q] &f" + q.name));
            return true;
        }
        if (a.length < 2) {
            help(sender);
            return true;
        }
        String name = a[1];

        if (sub.equals("초기화")) {
            OfflinePlayer t = Bukkit.getPlayerExact(name);
            if (t == null) t = Bukkit.getOfflinePlayerIfCached(name);
            if (t == null) {
                m(sender, "&c플레이어를 찾을 수 없습니다. (서버에 접속한 적이 있어야 합니다)");
                return true;
            }
            PlayerData d = qm.data(t.getUniqueId());
            if (a.length >= 3) {
                String qn = a[2];
                if (!d.done.remove(qn)) {
                    m(sender, "&c" + name + "님은 &f" + qn + "&c 퀘스트를 클리어한 기록이 없습니다.");
                    return true;
                }
                qm.dirty();
                qm.savePlayers();
                m(sender, name + "님의 &6" + qn + "&f 클리어 기록을 초기화했습니다.");
                return true;
            }
            d.current = null;
            d.progress.clear();
            d.done.clear();
            qm.dirty();
            qm.savePlayers();
            m(sender, name + "님의 진행중 퀘스트와 모든 클리어 기록을 초기화했습니다.");
            return true;
        }

        if (sub.equals("생성")) {
            if (name.contains(".")) {
                m(sender, "&c퀘스트 이름에는 '.' 을 쓸 수 없습니다.");
            } else if (qm.create(name) == null) {
                m(sender, "&c이미 존재하는 퀘스트 명입니다!");
            } else {
                m(sender, "성공적으로 &6[Q] &f" + name + "(이)가 생성되었습니다!");
            }
            return true;
        }

        Quest q = qm.quest(name);
        if (q == null) {
            m(sender, "&c존재하지 않는 퀘스트 명입니다!");
            return true;
        }

        switch (sub) {
            case "삭제" -> {
                qm.delete(name);
                m(sender, "성공적으로 &6[Q] &f" + name + "(이)가 삭제되었습니다!");
            }
            case "정보" -> {
                m(sender, "&6[Q] &f" + name);
                sender.sendMessage(T.c("  &7NPC 번호 : &f" + (q.npc == null ? "&c미연결" : q.npc)));
                sender.sendMessage(T.c("  &7선행 퀘스트 : &f" + (q.prerequisite == null ? "&7없음" : q.prerequisite)));
                sender.sendMessage(T.c("  &7타입 : " + (q.repeat ? "&b반복" : "&f일반")));
                sender.sendMessage(T.c("  &7목표 (" + q.goals.size() + "개)"));
                Player viewer = sender instanceof Player pp ? pp : null;
                for (int i = 0; i < q.goals.size(); i++) {
                    if (viewer != null) sender.sendMessage(T.c("  &e" + (i + 1) + "." + qm.goalText(viewer, q, i, false)));
                }
                sender.sendMessage(T.c("  &7보상 아이템 : &f" + q.rewards.size() + "개"));
            }
            case "NPC연결" -> {
                if (!(sender instanceof Player p)) return true;
                qm.binding.put(p.getUniqueId(), name);
                m(sender, "퀘스트 &f" + name + "&f 에 연결할 &eNPC를 우클릭&f하세요.");
            }
            case "보상" -> {
                if (!(sender instanceof Player p)) return true;
                QuestGui.openReward(plugin, p, q, false, 1);
                m(sender, "보상 설정창을 엽니다. 아이템을 넣고 닫으면 저장됩니다.");
            }
            case "선행" -> {
                if (a.length < 3) {
                    m(sender, "사용법: /퀘스트 선행 <이름> <선행퀘스트|없음>");
                    return true;
                }
                if (a[2].equals("없음")) {
                    q.prerequisite = null;
                    qm.saveQuests();
                    m(sender, "선행 퀘스트를 해제했습니다.");
                } else if (qm.quest(a[2]) == null) {
                    m(sender, "&c존재하지 않는 선행 퀘스트 입니다!");
                } else if (a[2].equals(name)) {
                    m(sender, "&c자기 자신을 선행 퀘스트로 지정할 수 없습니다.");
                } else {
                    q.prerequisite = a[2];
                    qm.saveQuests();
                    m(sender, "&6" + name + "&f 의 선행 퀘스트 = &6" + a[2]);
                }
            }
            case "목표삭제" -> {
                int i = a.length >= 3 ? parse(a[2]) : -1;
                if (i < 1 || i > q.goals.size()) {
                    m(sender, "&c그 번호의 목표는 없습니다. 사용법: /퀘스트 목표삭제 <이름> <번호>");
                    return true;
                }
                q.goals.remove(i - 1);
                qm.resetProgress(name);
                qm.saveQuests();
                m(sender, "목표 " + i + "번을 삭제했습니다.");
            }
            case "목표" -> goal(sender, qm, q, a);
            default -> help(sender);
        }
        return true;
    }

    private void goal(CommandSender sender, QuestManager qm, Quest q, String[] a) {
        if (a.length < 3) {
            help(sender);
            return;
        }
        if (q.goals.size() >= qm.maxGoals()) {
            m(sender, "&c목표는 최대 " + qm.maxGoals() + "개까지 가능합니다.");
            return;
        }
        if (a[2].equals("가져오기")) {
            if (!(sender instanceof Player p)) return;
            int amt = a.length >= 4 ? parse(a[3]) : -1;
            if (amt < 1) {
                m(sender, "사용법: /퀘스트 목표 <이름> 가져오기 <개수> [회수]");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                m(sender, "&c목표로 지정할 아이템을 손에 들어주세요.");
                return;
            }
            Goal g = new Goal();
            g.type = Goal.Type.ITEM;
            g.item = hand.clone();
            g.item.setAmount(1);
            g.amount = amt;
            g.recall = a.length >= 5 && a[4].equals("회수");
            q.goals.add(g);
        } else if (a[2].equals("처치")) {
            // /퀘스트 목표 <이름> 처치 <몹이름(띄어쓰기 가능)> <마리>
            if (a.length < 5) {
                m(sender, "사용법: /퀘스트 목표 <이름> 처치 <몹이름> <마리>");
                return;
            }
            int amt = parse(a[a.length - 1]);
            String mob = String.join(" ", Arrays.copyOfRange(a, 3, a.length - 1));
            if (amt < 1 || mob.isEmpty()) {
                m(sender, "사용법: /퀘스트 목표 <이름> 처치 <몹이름> <마리>");
                return;
            }
            Goal g = new Goal();
            g.type = Goal.Type.KILL;
            g.mob = mob;
            g.amount = amt;
            q.goals.add(g);
        } else {
            m(sender, "목표 종류는 가져오기 / 처치 입니다.");
            return;
        }
        qm.resetProgress(q.name);
        qm.saveQuests();
        m(sender, "목표 " + q.goals.size() + "번을 추가했습니다.");
    }

    private void help(CommandSender s) {
        m(s, "/퀘스트 &7- 관리 GUI (생성/편집을 클릭으로)");
        m(s, "/퀘스트 생성|삭제|정보|NPC연결|보상 <이름>");
        m(s, "/퀘스트 목록");
        m(s, "/퀘스트 목표 <이름> 가져오기 <개수> [회수] &7- 손에 든 아이템");
        m(s, "/퀘스트 목표 <이름> 처치 <몹이름> <마리>");
        m(s, "/퀘스트 목표삭제 <이름> <번호>");
        m(s, "/퀘스트 선행 <이름> <선행퀘스트|없음>");
        m(s, "/퀘스트 초기화 <닉네임> [퀘스트] &7- 클리어 기록 초기화 (오프라인 가능)");
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equals("q") || !sender.hasPermission("chquest.admin")) return out;
        if (a.length == 1) {
            for (String s : SUBS) if (s.startsWith(a[0])) out.add(s);
        } else if (a.length == 2 && !a[0].equals("목록") && !a[0].equals("생성") && !a[0].equals("초기화")) {
            for (Quest q : plugin.manager().quests()) if (q.name.startsWith(a[1])) out.add(q.name);
        } else if (a.length == 2 && a[0].equals("초기화")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        } else if (a.length == 3 && a[0].equals("목표")) {
            out.addAll(List.of("가져오기", "처치"));
        } else if (a.length == 3 && (a[0].equals("선행") || a[0].equals("초기화"))) {
            for (Quest q : plugin.manager().quests()) if (q.name.startsWith(a[2])) out.add(q.name);
            if (a[0].equals("선행")) out.add("없음");
        }
        return out;
    }
}
