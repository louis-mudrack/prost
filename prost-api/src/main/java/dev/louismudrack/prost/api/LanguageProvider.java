package dev.louismudrack.prost.api;

import org.bukkit.entity.Player;

import java.util.Optional;

/** A language code such as "en" or "de"; empty (or a code Prost has no file for) = Prost decides. */
@FunctionalInterface
public interface LanguageProvider {
    /** the language code for this player, or empty to let Prost decide */
    Optional<String> language(Player player);
}
