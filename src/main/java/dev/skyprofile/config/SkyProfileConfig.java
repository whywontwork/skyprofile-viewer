package dev.skyprofile.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skyprofile.SkyProfileClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stored in config/skyprofile.json. The API key never leaves this machine except
 * in the x-api-key header sent to api.shiiyu.moe.
 */
public final class SkyProfileConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyprofile.json");
	private static SkyProfileConfig instance;

	/** Personal SkyCrypt API key (starts with "skycrypt_"). Get one from the developer page of your SkyCrypt dashboard. */
	public String apiKey = "";
	/** Adds the [View Profile] button after [Block Player] in Hypixel's social options message. */
	public boolean addChatButton = true;

	public static SkyProfileConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try {
				instance = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SkyProfileConfig.class);
			} catch (Exception e) {
				SkyProfileClient.LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
			}
		}
		if (instance == null) instance = new SkyProfileConfig();
		if (instance.apiKey == null) instance.apiKey = "";
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(get()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SkyProfileClient.LOGGER.warn("Couldn't save {}", FILE, e);
		}
	}

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}
}
