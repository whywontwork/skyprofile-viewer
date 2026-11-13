package dev.skyprofile.dev;

import dev.skyprofile.SkyProfileClient;
import dev.skyprofile.chat.SocialOptionsHook;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.ProfileViewerScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Development only, never active in normal play: when the JVM is started with
 * -Dskyprofile.devShot=true (gradlew runClient -PdevShot), it creates a throwaway singleplayer
 * world (item icons need a loaded world in 26.x), opens the viewer on mock data, screenshots
 * every tab and quits.
 */
public final class DevHarness {
	private enum Phase { TITLE, CREATE_WORLD, JOINING, TABS }

	private static Phase phase = Phase.TITLE;
	private static int ticks;
	private static int tab;
	private static ProfileViewerScreen screen;

	private DevHarness() {}

	public static void init() {
		if (!Boolean.getBoolean("skyprofile.devShot")) return;
		ClientTickEvents.END_CLIENT_TICK.register(DevHarness::tick);
	}

	private static void tick(Minecraft mc) {
		ticks++;
		switch (phase) {
			case TITLE -> {
				if (!(mc.gui.screen() instanceof TitleScreen) || ticks < 40) return;
				CreateWorldScreen.openFresh(mc, () -> {});
				next(Phase.CREATE_WORLD);
			}
			case CREATE_WORLD -> {
				if (!(mc.gui.screen() instanceof CreateWorldScreen cws) || ticks < 20) return;
				String label = Component.translatable("selectWorld.create").getString();
				for (GuiEventListener child : cws.children()) {
					if (child instanceof Button b && b.getMessage().getString().equals(label)) {
						b.onPress(new KeyEvent(257, 0, 0));
						next(Phase.JOINING);
						return;
					}
				}
			}
			case JOINING -> {
				if (mc.level == null || mc.player == null || mc.gui.screen() != null) {
					if (ticks > 20 * 120) fail(mc, "world never loaded");
					return;
				}
				if (ticks < 60) return;
				selfTestChatHook(mc);
				screen = new ProfileViewerScreen(new PlayerProfile("DevPlayer", null), null);
				mc.gui.setScreen(screen);
				next(Phase.TABS);
			}
			case TABS -> {
				if (ticks == 80) {
					int t = tab;
					Screenshot.grab(mc.gameDirectory, "skyprofile_tab" + t + ".png", mc.gameRenderer.mainRenderTarget(), 1,
							msg -> SkyProfileClient.LOGGER.info("[DevHarness] saved tab {}", t));
				}
				if (ticks >= 100) {
					tab++;
					ticks = 0;
					if (tab >= 6) {
						SkyProfileClient.LOGGER.info("[DevHarness] done");
						mc.stop();
						return;
					}
					screen.selectTab(tab);
				}
			}
		}
	}

	private static void next(Phase p) {
		phase = p;
		ticks = 0;
	}

	private static void fail(Minecraft mc, String why) {
		SkyProfileClient.LOGGER.error("[DevHarness] {}", why);
		mc.stop();
	}

	/** Feeds a Hypixel-style social options message through the chat hook, logs it and shows it in chat. */
	private static void selfTestChatHook(Minecraft mc) {
		Component msg = Component.literal("  Lowballing_\n")
				.append(Component.literal("[Report Player]").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)
						.withClickEvent(new ClickEvent.RunCommand("/report Lowballing_"))))
				.append(Component.literal(" "))
				.append(Component.literal("[Block Player]").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
						.withClickEvent(new ClickEvent.RunCommand("/block add Lowballing_"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Block Lowballing_")))));
		Component out = SocialOptionsHook.modify(msg, false);
		SkyProfileClient.LOGGER.info("[DevHarness] chat hook in : {}", msg.getString().replace("\n", "\\n"));
		SkyProfileClient.LOGGER.info("[DevHarness] chat hook out: {}", out.getString().replace("\n", "\\n"));
		out.visit((style, text) -> {
			if (style.getClickEvent() != null) SkyProfileClient.LOGGER.info("[DevHarness]   '{}' -> {}", text, style.getClickEvent());
			return Optional.empty();
		}, Style.EMPTY);
		mc.gui.hud.getChat().addClientSystemMessage(out);
	}
}
