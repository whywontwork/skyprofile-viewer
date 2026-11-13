package dev.skyprofile;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.skyprofile.chat.SocialOptionsHook;
import dev.skyprofile.config.SkyProfileConfig;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.dev.DevHarness;
import dev.skyprofile.ui.ProfileViewerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyProfileClient implements ClientModInitializer {
	public static final String MOD_ID = "skyprofile";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Screen pendingScreen;

	@Override
	public void onInitializeClient() {
		SkyProfileConfig.load();

		ClientReceiveMessageEvents.MODIFY_GAME.register(SocialOptionsHook::modify);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("skyprofile")
						.executes(ctx -> {
							openProfile(ctx.getSource().getClient().getUser().getName());
							return 1;
						})
						.then(ClientCommands.literal("apikey").executes(ctx -> {
							openNextTick(ProfileViewerScreen.keyPrompt(new PlayerProfile(ctx.getSource().getClient().getUser().getName(), null), null));
							return 1;
						}))
						.then(ClientCommands.argument("player", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
								.executes(ctx -> {
									openProfile(StringArgumentType.getString(ctx, "player"));
									return 1;
								}))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingScreen != null) {
				Screen s = pendingScreen;
				pendingScreen = null;
				client.gui.setScreen(s);
			}
		});

		DevHarness.init();
	}

	public static void openProfile(String name) {
		if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
		openNextTick(new ProfileViewerScreen(new PlayerProfile(name, null), null));
	}

	/** Opening a screen straight from a chat command gets undone when the chat screen closes, so wait a tick. */
	public static void openNextTick(Screen screen) {
		pendingScreen = screen;
	}

	/** Used by the chat click handler, where it's safe to switch screens immediately. */
	public static void openProfileNow(String name) {
		if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
		Minecraft.getInstance().gui.setScreen(new ProfileViewerScreen(new PlayerProfile(name, null), null));
	}
}
