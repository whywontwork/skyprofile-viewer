package dev.skyprofile.ui;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class Fmt {
	private static final DecimalFormat COMMAS = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat ONE_DP = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat TWO_DP = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(ZoneId.systemDefault());
	private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
			"XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV", "XXV"};

	/** SkyCrypt's hex palette mapped back to Minecraft color codes. */
	private static final Map<String, Character> HEX_TO_CODE = Map.ofEntries(
			Map.entry("#000000", '0'), Map.entry("#0b277a", '1'), Map.entry("#00aa00", '2'), Map.entry("#038d8d", '3'),
			Map.entry("#920909", '4'), Map.entry("#a305a3", '5'), Map.entry("#d88f07", '6'), Map.entry("#636363", '7'),
			Map.entry("#2f2f2f", '8'), Map.entry("#4444f3", '9'), Map.entry("#40bb40", 'a'), Map.entry("#33aec3", 'b'),
			Map.entry("#c43c3c", 'c'), Map.entry("#e668c6", 'd'), Map.entry("#efc721", 'e'), Map.entry("#929292", 'f'));

	private Fmt() {}

	public static String commas(double v) {
		return COMMAS.format(Math.floor(v));
	}

	public static String dec(double v) {
		return ONE_DP.format(v);
	}

	public static String dec2(double v) {
		return TWO_DP.format(v);
	}

	public static String shortNum(double v) {
		double a = Math.abs(v);
		if (a >= 1e12) return ONE_DP.format(v / 1e12) + "T";
		if (a >= 1e9) return TWO_DP.format(v / 1e9) + "B";
		if (a >= 1e6) return ONE_DP.format(v / 1e6) + "M";
		if (a >= 1e3) return ONE_DP.format(v / 1e3) + "k";
		return COMMAS.format(v);
	}

	public static String roman(int n) {
		return n >= 0 && n < ROMAN.length ? ROMAN[n] : Integer.toString(n);
	}

	public static String date(double epochMillis) {
		if (epochMillis <= 0) return "?";
		// Some SkyCrypt fields are seconds, some milliseconds.
		long ms = (long) (epochMillis < 1e11 ? epochMillis * 1000 : epochMillis);
		return DATE.format(Instant.ofEpochMilli(ms));
	}

	/** Time in ms → "m:ss" (dungeon run times). */
	public static String time(double ms) {
		if (ms <= 0) return "-";
		long s = (long) (ms / 1000);
		return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
	}

	/** "#33aec3" or "b" or "AQUA" → "§b". Unknown colors fall back to white. */
	public static String colorCode(String color) {
		if (color == null || color.isEmpty()) return "§f";
		String c = color.trim().toLowerCase(Locale.ROOT);
		if (c.length() == 1) return "§" + c;
		if (c.startsWith("§") && c.length() >= 2) return c.substring(0, 2);
		Character code = HEX_TO_CODE.get(c);
		if (code != null) return "§" + code;
		return switch (c) {
			case "black" -> "§0";
			case "dark_blue" -> "§1";
			case "dark_green" -> "§2";
			case "dark_aqua" -> "§3";
			case "dark_red" -> "§4";
			case "dark_purple" -> "§5";
			case "gold" -> "§6";
			case "gray" -> "§7";
			case "dark_gray" -> "§8";
			case "blue" -> "§9";
			case "green" -> "§a";
			case "aqua" -> "§b";
			case "red" -> "§c";
			case "light_purple" -> "§d";
			case "yellow" -> "§e";
			default -> "§f";
		};
	}

	public static String rarityColor(String rarity) {
		if (rarity == null) return "§f";
		return switch (rarity.toUpperCase(Locale.ROOT).replace(' ', '_')) {
			case "UNCOMMON" -> "§a";
			case "RARE" -> "§9";
			case "EPIC" -> "§5";
			case "LEGENDARY" -> "§6";
			case "MYTHIC" -> "§d";
			case "DIVINE" -> "§b";
			case "SPECIAL", "VERY_SPECIAL" -> "§c";
			case "ULTIMATE" -> "§4";
			case "ADMIN" -> "§4";
			default -> "§f";
		};
	}

	public static int rarityRgb(String rarity) {
		return codeRgb(rarityColor(rarity).charAt(1));
	}

	public static int codeRgb(char code) {
		return switch (Character.toLowerCase(code)) {
			case '0' -> 0x000000;
			case '1' -> 0x0000AA;
			case '2' -> 0x00AA00;
			case '3' -> 0x00AAAA;
			case '4' -> 0xAA0000;
			case '5' -> 0xAA00AA;
			case '6' -> 0xFFAA00;
			case '7' -> 0xAAAAAA;
			case '8' -> 0x555555;
			case '9' -> 0x5555FF;
			case 'a' -> 0x55FF55;
			case 'b' -> 0x55FFFF;
			case 'c' -> 0xFF5555;
			case 'd' -> 0xFF55FF;
			case 'e' -> 0xFFFF55;
			default -> 0xFFFFFF;
		};
	}

	public static String titleCase(String id) {
		if (id == null || id.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (String part : id.replace('-', '_').split("_")) {
			if (part.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	public static String stripCodes(String s) {
		return s == null ? "" : s.replaceAll("§.", "");
	}
}
