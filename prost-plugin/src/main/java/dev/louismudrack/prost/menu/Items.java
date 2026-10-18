package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.i18n.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/** Menu items in the UX standard's look: MiniMessage names, no italics, no attribute noise. */
public final class Items {
    private Items() {}

    public static ItemStack item(Material material, String nameMini, List<String> loreMini) {
        ItemStack it = new ItemStack(material);
        it.editMeta(meta -> {
            meta.displayName(Msg.mm("<!italic>" + nameMini));
            meta.lore(loreMini.stream().map(l -> Msg.mm("<!italic>" + l)).toList());
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    public static ItemStack head(UUID owner, String nameMini, List<String> loreMini) {
        ItemStack it = item(Material.PLAYER_HEAD, nameMini, loreMini);
        it.editMeta(meta -> { if (meta instanceof SkullMeta skull) skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner)); });
        return it;
    }

    static ItemStack filler() { return item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); }
}
