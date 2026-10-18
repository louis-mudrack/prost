/**
 * Prost's public API (MIT licensed, unlike the GPL-3.0 plugin). Get it with
 * {@code Bukkit.getServicesManager().load(ProstApi.class)}; it is null when Prost is not installed. Add
 * {@code softdepend: [Prost]} to your plugin.yml so Prost enables first.
 *
 * <p>Every Prost event fires on the main server thread and is not cancellable.
 */
package dev.louismudrack.prost.api;
