package dev.skyprofile.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skyprofile.api.SkyCryptApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Everything the viewer shows for one player + profile. Sections load independently so the
 * Basic page can appear as soon as /api/stats answers.
 */
public final class PlayerProfile {
	public static final class Part<T> {
		public volatile T data;
		public volatile Throwable error;
		volatile boolean requested;

		public boolean loading() {
			return data == null && error == null;
		}
	}

	public record ProfileRef(String id, String name, String gameMode, boolean selected) {}

	public final String query;
	public final Part<JsonObject> stats = new Part<>();
	public final Part<JsonObject> networth = new Part<>();
	public final Part<JsonObject> playerStats = new Part<>();
	public final Part<JsonObject> combined = new Part<>();
	public final Part<JsonElement> inventory = new Part<>();

	public volatile String uuid;
	public volatile String username;
	public volatile String profileId;

	public PlayerProfile(String query, String profileId) {
		this.query = query;
		this.profileId = profileId;
	}

	public void load() {
		stats.requested = true;
		SkyCryptApi.stats(uuid != null ? uuid : query, profileId).whenComplete((s, t) -> {
			if (t != null) {
				stats.error = SkyCryptApi.rootCause(t);
				return;
			}
			uuid = J.str(s, "uuid");
			username = J.strOr(s, query, "username");
			profileId = J.str(s, "profile_id");
			stats.data = s;
			fetch(networth, SkyCryptApi.networth(uuid, profileId));
			fetch(playerStats, SkyCryptApi.playerStats(uuid, profileId));
			fetch(combined, SkyCryptApi.combined(uuid, profileId));
		});
	}

	/** Inventories are only fetched when the user opens that tab. */
	public void requestInventory() {
		if (inventory.requested || stats.data == null) return;
		fetch(inventory, SkyCryptApi.inventory(uuid, profileId));
	}

	private static <T> void fetch(Part<T> part, CompletableFuture<T> future) {
		part.requested = true;
		future.whenComplete((d, t) -> {
			if (t != null) part.error = SkyCryptApi.rootCause(t);
			else part.data = d;
		});
	}

	public List<ProfileRef> profiles() {
		List<ProfileRef> out = new ArrayList<>();
		for (JsonObject p : J.objects(stats.data, "profiles")) {
			out.add(new ProfileRef(J.str(p, "profile_id"), J.strOr(p, "?", "cute_name"), J.str(p, "game_mode"), J.bool(p, "selected")));
		}
		if (out.isEmpty() && stats.data != null) {
			out.add(new ProfileRef(profileId, J.strOr(stats.data, "?", "profile_cute_name"), J.str(stats.data, "game_mode"), true));
		}
		return out;
	}

	public String cuteName() {
		return J.strOr(stats.data, "?", "profile_cute_name");
	}

	public String gameMode() {
		return J.str(stats.data, "game_mode");
	}

	public void ifStats(Consumer<JsonObject> c) {
		JsonObject s = stats.data;
		if (s != null) c.accept(s);
	}
}
