package dev.skyprofile.chat;

import dev.skyprofile.config.SkyProfileConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adds a "[View Profile]" button right after "[Block Player]" in the social options
 * message Hypixel prints when you click a player's name in chat.
 * <p>
 * Purely client-side: it only changes how the received message is displayed. Clicking the
 * button runs the local /skyprofile command, which is handled by the mod and never sent to the server.
 */
public final class SocialOptionsHook {
	private static final String BLOCK = "[Block Player]";
	private static final String REPORT = "[Report Player]";
	private static final String BUTTON = "[View Profile]";
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Set<String> NOT_NAMES = Set.of("add", "remove", "block", "unblock", "ignore", "report",
			"chatreport", "player", "players", "chat", "list", "confirm", "cancel", "true", "false", "socialoptions");

	/** Name the user most recently clicked in chat ("Click to show social options for X"). */
	private static volatile String lastClickedName;
	private static volatile long lastClickedAt;
	/** A message that was just a lone username, in case Hypixel sends the name and the buttons separately. */
	private static volatile String lastLoneName;
	private static volatile long lastLoneNameAt;

	private SocialOptionsHook() {}

	private record Segment(Style style, String text) {}

	public static void rememberClickedName(String name) {
		if (name != null && NAME.matcher(name).matches()) {
			lastClickedName = name;
			lastClickedAt = System.currentTimeMillis();
		}
	}

	/** Called from the chat click handler with the hover text of the clicked component. */
	public static void onChatComponentClicked(Style style) {
		if (style == null) return;
		if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
			String hover = value.getString();
			int i = hover.toLowerCase(Locale.ROOT).indexOf("social options for ");
			if (i >= 0) {
				String rest = hover.substring(i + "social options for ".length()).trim();
				String[] parts = rest.split("\\s+");
				if (parts.length > 0) rememberClickedName(strip(parts[0]));
			}
		}
	}

	public static Component modify(Component message, boolean overlay) {
		if (overlay || !SkyProfileConfig.get().addChatButton) return message;
		String plain = message.getString();

		int blockAt = plain.indexOf(BLOCK);
		if (blockAt < 0) {
			String trimmed = strip(plain.trim());
			if (!trimmed.isEmpty() && NAME.matcher(trimmed).matches() && plain.length() < 40) {
				lastLoneName = trimmed;
				lastLoneNameAt = System.currentTimeMillis();
			}
			return message;
		}
		if (plain.contains(BUTTON)) return message;

		List<Segment> segments = new ArrayList<>();
		message.visit((style, text) -> {
			if (!text.isEmpty()) segments.add(new Segment(style, text));
			return Optional.empty();
		}, Style.EMPTY);

		String name = findName(segments, plain);
		if (name == null) return message;

		int insertAt = blockAt + BLOCK.length();
		MutableComponent out = Component.empty();
		int offset = 0;
		boolean inserted = false;
		for (Segment s : segments) {
			int end = offset + s.text.length();
			if (!inserted && insertAt >= offset && insertAt <= end) {
				int cut = insertAt - offset;
				if (cut > 0) out.append(Component.literal(s.text.substring(0, cut)).setStyle(s.style));
				out.append(button(name));
				if (cut < s.text.length()) out.append(Component.literal(s.text.substring(cut)).setStyle(s.style));
				inserted = true;
			} else {
				out.append(Component.literal(s.text).setStyle(s.style));
			}
			offset = end;
		}
		if (!inserted) out.append(button(name));
		return out;
	}

	private static Component button(String name) {
		Style style = Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand("/skyprofile " + name))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("View " + name + "'s SkyBlock profile")
						.withStyle(ChatFormatting.YELLOW)
						.append(Component.literal("\nData from SkyCrypt").withStyle(ChatFormatting.DARK_GRAY))));
		return Component.literal(" ").append(Component.literal(BUTTON).setStyle(style));
	}

	private static String findName(List<Segment> segments, String plain) {
		// 1. The [Block Player] / [Report Player] buttons run a command that ends with the player's name.
		for (Segment s : segments) {
			if (!(s.text.contains("Block") || s.text.contains("Report"))) continue;
			String fromClick = switch (s.style.getClickEvent()) {
				case ClickEvent.RunCommand(String command) -> nameFromCommand(command, plain);
				case ClickEvent.SuggestCommand(String command) -> nameFromCommand(command, plain);
				case null, default -> null;
			};
			if (fromClick != null) return fromClick;
		}

		// 2. The name printed just before the buttons ("  Lowballing_\n[Report Player] [Block Player]").
		int buttonsAt = plain.indexOf(REPORT);
		if (buttonsAt < 0 || buttonsAt > plain.indexOf(BLOCK)) buttonsAt = plain.indexOf(BLOCK);
		String before = plain.substring(0, buttonsAt).trim();
		if (!before.isEmpty()) {
			String[] words = before.split("\\s+");
			String last = strip(words[words.length - 1]);
			if (NAME.matcher(last).matches() && !NOT_NAMES.contains(last.toLowerCase(Locale.ROOT))) return last;
		}

		// 3. A lone-name message right before, or the name the user just clicked.
		long now = System.currentTimeMillis();
		if (lastLoneName != null && now - lastLoneNameAt < 3000) return lastLoneName;
		if (lastClickedName != null && now - lastClickedAt < 15000) return lastClickedName;
		return null;
	}

	private static String nameFromCommand(String command, String plain) {
		if (command == null) return null;
		String[] tokens = command.trim().split("\\s+");
		String fallback = null;
		for (int i = tokens.length - 1; i >= 1; i--) {
			String t = strip(tokens[i]);
			if (!NAME.matcher(t).matches() || NOT_NAMES.contains(t.toLowerCase(Locale.ROOT))) continue;
			if (plain.contains(t)) return t;
			if (fallback == null) fallback = t;
		}
		return fallback;
	}

	private static String strip(String s) {
		return s.replaceAll("§.", "").replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
	}
}
