package dev.skyprofile.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skyprofile.SkyProfileClient;
import dev.skyprofile.config.SkyProfileConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal client for the SkyCrypt API (https://api.shiiyu.moe, docs at /openapi.json).
 * <p>
 * Follows the SkyCrypt API policy: one request per player when the user asks for that player,
 * responses cached in memory for at least 5 minutes (longer if Cache-Control says so), nothing
 * written to disk, and the key only ever sent to api.shiiyu.moe.
 */
public final class SkyCryptApi {
	public static final String BASE = "https://api.shiiyu.moe";
	public static final String WEBSITE = "https://sky.shiiyu.moe";
	private static final String USER_AGENT = "SkyProfileViewer/1.0 (Fabric mod)";
	private static final long MIN_CACHE_MS = 5 * 60 * 1000L;
	private static final long MAX_CACHE_MS = 30 * 60 * 1000L;
	private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

	public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "SkyProfile-API");
		t.setDaemon(true);
		return t;
	});

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.executor(EXECUTOR)
			.build();

	private record CacheEntry(JsonElement json, long expiresAt) {}

	private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<JsonElement>> IN_FLIGHT = new ConcurrentHashMap<>();
	private static volatile long rateLimitedUntil;

	private SkyCryptApi() {}

	public static CompletableFuture<JsonObject> stats(String player, String profileId) {
		String path = profileId == null ? "/api/stats/" + enc(player) : "/api/stats/" + enc(player) + "/" + enc(profileId);
		return getObject(path, "stats");
	}

	public static CompletableFuture<JsonObject> networth(String uuid, String profileId) {
		return getObject("/api/networth/" + enc(uuid) + "/" + enc(profileId), "networth");
	}

	public static CompletableFuture<JsonObject> playerStats(String uuid, String profileId) {
		return getObject("/api/playerStats/" + enc(uuid) + "/" + enc(profileId), "playerStats");
	}

	public static CompletableFuture<JsonObject> combined(String uuid, String profileId) {
		return getObject("/api/combined/" + enc(uuid) + "/" + enc(profileId), "combined");
	}

	public static CompletableFuture<JsonElement> inventory(String uuid, String profileId) {
		return get("/api/inventory/" + enc(uuid) + "/" + enc(profileId), "inventory");
	}

	/** Forget cached responses for one player, e.g. when the user presses Refresh after the 5 minute window. */
	public static void invalidate(String uuid) {
		long now = System.currentTimeMillis();
		CACHE.entrySet().removeIf(e -> e.getKey().contains(uuid) && e.getValue().expiresAt - MAX_CACHE_MS + MIN_CACHE_MS < now);
	}

	private static CompletableFuture<JsonObject> getObject(String path, String mockName) {
		return get(path, mockName).thenApply(e -> {
			if (e == null || !e.isJsonObject()) throw new ApiException(0, "BAD_RESPONSE", "SkyCrypt sent an unexpected response.", 0);
			return e.getAsJsonObject();
		});
	}

	private static CompletableFuture<JsonElement> get(String path, String mockName) {
		long now = System.currentTimeMillis();
		CacheEntry cached = CACHE.get(path);
		if (cached != null && cached.expiresAt > now) return CompletableFuture.completedFuture(cached.json);
		CACHE.values().removeIf(c -> c.expiresAt <= now);

		String mockDir = System.getProperty("skyprofile.mockDir");
		if (mockDir != null) return mock(Path.of(mockDir), mockName);

		if (!SkyProfileConfig.get().hasApiKey()) {
			return CompletableFuture.failedFuture(new ApiException(401, "MISSING_API_KEY", "No SkyCrypt API key set yet.", 0));
		}
		if (rateLimitedUntil > now) {
			return CompletableFuture.failedFuture(new ApiException(429, "RATE_LIMITED", "Rate limited by SkyCrypt.", (rateLimitedUntil - now + 999) / 1000));
		}

		return IN_FLIGHT.computeIfAbsent(path, p -> {
			HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + p))
					.timeout(Duration.ofSeconds(30))
					.header("User-Agent", USER_AGENT)
					.header("Accept", "application/json")
					.header("x-api-key", SkyProfileConfig.get().apiKey.trim())
					.GET().build();
			CompletableFuture<JsonElement> f = HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
					.thenApply(res -> handle(p, res));
			f.whenComplete((r, t) -> IN_FLIGHT.remove(p));
			return f;
		});
	}

	private static JsonElement handle(String path, HttpResponse<String> res) {
		int status = res.statusCode();
		if (status == 200) {
			JsonElement json = JsonParser.parseString(res.body());
			long ttl = MIN_CACHE_MS;
			String cc = res.headers().firstValue("Cache-Control").orElse("");
			Matcher m = MAX_AGE.matcher(cc);
			if (m.find()) ttl = Math.max(MIN_CACHE_MS, Math.min(MAX_CACHE_MS, Long.parseLong(m.group(1)) * 1000L));
			CACHE.put(path, new CacheEntry(json, System.currentTimeMillis() + ttl));
			return json;
		}

		String code = "HTTP_" + status;
		String detail = "SkyCrypt returned HTTP " + status + ".";
		try {
			JsonObject err = JsonParser.parseString(res.body()).getAsJsonObject();
			if (err.has("code")) code = err.get("code").getAsString();
			if (err.has("detail")) detail = err.get("detail").getAsString();
		} catch (Exception ignored) {
		}
		long retryAfter = 0;
		if (status == 429) {
			retryAfter = res.headers().firstValue("Retry-After").map(SkyCryptApi::parseLong).orElse(30L);
			rateLimitedUntil = System.currentTimeMillis() + retryAfter * 1000L;
		}
		SkyProfileClient.LOGGER.info("SkyCrypt {} -> {} {}", path, status, code);
		throw new ApiException(status, code, detail, retryAfter);
	}

	private static CompletableFuture<JsonElement> mock(Path dir, String name) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(150);
				Path file = dir.resolve(name + ".json");
				if (!Files.exists(file)) throw new ApiException(404, "NOT_CACHED", "Mock file missing: " + file, 0);
				return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			} catch (InterruptedException | java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}, EXECUTOR);
	}

	private static long parseLong(String s) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return 30L;
		}
	}

	public static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** Unwraps CompletionException layers. */
	public static Throwable rootCause(Throwable t) {
		while ((t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
			t = t.getCause();
		}
		return t;
	}
}
