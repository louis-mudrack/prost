package dev.louismudrack.prost.ui;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.i18n.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Boss bar colours per kind, titles, action bar. */
public final class Hud {
    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500));
    /** a new challenge stays longer: kind, name and goal have to be read, not glimpsed */
    private static final Title.Times ANNOUNCE = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(5000), Duration.ofMillis(500));

    private Hud() {}

    public static BossBar.Color color(Kind k) {
        return switch (k) {
            case TEAM, LUCK -> BossBar.Color.GREEN;
            case EACH -> BossBar.Color.BLUE;
            case RACE, DUEL, REACTION -> BossBar.Color.RED;
            case SOLO -> BossBar.Color.YELLOW;
            case VOTE -> BossBar.Color.PURPLE;
        };
    }

    public static void title(Player p, String titleMini, String subMini) { p.showTitle(Title.title(Msg.mm(titleMini), Msg.mm(subMini), TIMES)); }

    public static void announce(Player p, String titleMini, String subMini) { p.showTitle(Title.title(Msg.mm(titleMini), Msg.mm(subMini), ANNOUNCE)); }

    public static void actionBar(Player p, String mini) { p.sendActionBar(Msg.mm(mini)); }
}
