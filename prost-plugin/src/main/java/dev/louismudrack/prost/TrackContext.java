package dev.louismudrack.prost;

/** what a tracker needs to know: the plugin, the round (members, placed blocks) and the entry */
record TrackContext(ProstPlugin plugin, Round round, ChallengeDef def) {}
