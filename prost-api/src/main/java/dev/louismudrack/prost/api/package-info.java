/**
 * Prost's public API (MIT licensed, unlike the GPL-3.0 plugin). Compile against it with {@code compileOnly} and never
 * shade it: Prost's jar contains these classes. Add {@code softdepend: [Prost]} to your plugin.yml so Prost enables
 * first, and check {@code isPluginEnabled("Prost")} before touching any class here - without Prost they do not exist.
 * Then get the API with {@code Bukkit.getServicesManager().load(ProstApi.class)}.
 *
 * <p>Every Prost event fires on the main server thread and is not cancellable.
 */
package dev.louismudrack.prost.api;
