package com.ch.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 텍스트 / 아이템 유틸 */
final class T {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    private T() {
    }

    static Component c(String s) {
        return L.deserialize(s);
    }

    /** 아이템 이름/설명용 (기본 이탤릭 제거) */
    static Component n(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(n(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(n(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    static void setLore(ItemStack it, List<String> lines) {
        ItemMeta meta = it.getItemMeta();
        List<Component> l = new ArrayList<>();
        for (String s : lines) l.add(n(s));
        meta.lore(l);
        it.setItemMeta(meta);
    }

    /** 아이템 표시 이름 (커스텀 이름이 없으면 재료 이름) */
    static String itemName(ItemStack it) {
        if (it == null) return "?";
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return L.serialize(meta.displayName());
        }
        return it.getType().name().toLowerCase().replace('_', ' ');
    }
}
