package dev.skyprofile.ui;

import com.mojang.authlib.GameProfile;
import dev.skyprofile.SkyProfileClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Fetches a player's skin by UUID the same way vanilla does (Mojang session server + texture cache). */
public final class SkinLoader {
	private static final Map<UUID, AtomicReference<PlayerSkin>> SKINS = new ConcurrentHashMap<>();

	private SkinLoader() {}

	public static Supplier<PlayerSkin> skin(UUID uuid) {
		AtomicReference<PlayerSkin> ref = SKINS.computeIfAbsent(uuid, id -> {
			AtomicReference<PlayerSkin> r = new AtomicReference<>(DefaultPlayerSkin.get(id));
			Minecraft mc = Minecraft.getInstance();

			// Already in our lobby / tab list? Use the skin the client has.
			if (mc.getConnection() != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(id);
				if (info != null) {
					r.set(info.getSkin());
					return r;
				}
			}

			CompletableFuture.supplyAsync(() -> mc.services().profileResolver().fetchById(id), dev.skyprofile.api.SkyCryptApi.EXECUTOR)
					.thenCompose((Optional<GameProfile> p) -> p.isPresent()
							? mc.getSkinManager().get(p.get())
							: CompletableFuture.completedFuture(Optional.<PlayerSkin>empty()))
					.whenComplete((skin, t) -> {
						if (t != null) SkyProfileClient.LOGGER.debug("Skin lookup failed for {}", id, t);
						else skin.ifPresent(r::set);
					});
			return r;
		});
		return ref::get;
	}

	public static UUID parseUuid(String s) {
		if (s == null) return null;
		String hex = s.replace("-", "");
		if (!hex.matches("[0-9a-fA-F]{32}")) return null;
		return UUID.fromString(hex.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
	}
}
