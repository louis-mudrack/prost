package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.game.Round;

/** what a tracker needs to know: the plugin, the round (members, placed blocks) and the entry */
public record TrackContext(ProstPlugin plugin, Round round, ChallengeDef def) {}
