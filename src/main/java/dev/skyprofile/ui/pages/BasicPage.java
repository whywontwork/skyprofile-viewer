package dev.skyprofile.ui.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.Fmt;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.SkinLoader;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Net worth, player model, stats list and the skill/slayer grid, laid out like NEU's Basic Info page. */
public class BasicPage extends Page {
	private static final int LEFT_X = 8, LEFT_W = 106;
	private static final int STATS_X = 118, STATS_W = 104;
	private static final int SKILLS_X = 226, SKILLS_W = 186;
	private static final int ROW_H = 11;
	private static final int SKILL_ROW_H = 17;

	private record Entry(String key, String label, String color, Item icon, boolean slayer) {}

	private static final List<Entry> COL_1 = List.of(
			new Entry("taming", "Taming", "§d", Items.WOLF_SPAWN_EGG, false),
			new Entry("mining", "Mining", "§7", Items.STONE_PICKAXE, false),
			new Entry("foraging", "Foraging", "§2", Items.JUNGLE_SAPLING, false),
			new Entry("enchanting", "Enchanting", "§5", Items.ENCHANTING_TABLE, false),
			new Entry("carpentry", "Carpentry", "§6", Items.CRAFTING_TABLE, false),
			new Entry("farming", "Farming", "§e", Items.GOLDEN_HOE, false),
			new Entry("combat", "Combat", "§c", Items.STONE_SWORD, false),
			new Entry("social", "Social", "§a", Items.EMERALD, false),
			new Entry("hunting", "Hunting", "§3", Items.LEAD, false));

	private static final List<Entry> COL_2 = List.of(
			new Entry("fishing", "Fishing", "§b", Items.FISHING_ROD, false),
			new Entry("alchemy", "Alchemy", "§9", Items.BREWING_STAND, false),
			new Entry("runecrafting", "Runecraft", "§d", Items.MAGMA_CREAM, false),
			new Entry("zombie", "Revenant", "§2", Items.ROTTEN_FLESH, true),
			new Entry("spider", "Tarantula", "§4", Items.SPIDER_EYE, true),
			new Entry("wolf", "Sven", "§f", Items.MUTTON, true),
			new Entry("enderman", "Enderman", "§5", Items.ENDER_PEARL, true),
			new Entry("blaze", "Blaze", "§6", Items.BLAZE_POWDER, true),
			new Entry("vampire", "Vampire", "§4", Items.REDSTONE, true));

	/** Cumulative XP needed for each slayer level (index = level). */
	private static final Map<String, int[]> SLAYER_XP = Map.of(
			"zombie", new int[]{0, 5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000},
			"spider", new int[]{0, 5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000},
			"wolf", new int[]{0, 10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
			"enderman", new int[]{0, 10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
			"blaze", new int[]{0, 10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
			"vampire", new int[]{0, 20, 75, 240, 840, 2400});

	/** Hypixel's stat symbols (SkyCrypt's own symbols use a private-use font that Minecraft can't render). */
	private static final Map<String, String[]> STAT_STYLE = new LinkedHashMap<>();

	static {
		stat("health", "❤", "Health", "c");
		stat("defense", "❈", "Defense", "a");
		stat("strength", "❁", "Strength", "c");
		stat("speed", "✦", "Speed", "f");
		stat("critical_chance", "☣", "Crit Chance", "9");
		stat("critical_damage", "☠", "Crit Damage", "9");
		stat("bonus_attack_speed", "⚔", "Attack Speed", "e");
		stat("intelligence", "✎", "Intelligence", "b");
		stat("sea_creature_chance", "α", "SC Chance", "3");
		stat("magic_find", "✯", "Magic Find", "b");
		stat("pet_luck", "♣", "Pet Luck", "d");
		stat("ferocity", "⫽", "Ferocity", "c");
		stat("ability_damage", "๑", "Ability Damage", "c");
		stat("true_defense", "❂", "True Defense", "f");
		stat("health_regen", "❣", "Health Regen", "c");
		stat("vitality", "♨", "Vitality", "4");
		stat("mending", "☄", "Mending", "a");
		stat("swing_range", "Ⓢ", "Swing Range", "e");
		stat("mining_speed", "⸕", "Mining Speed", "6");
		stat("mining_fortune", "☘", "Mining Fortune", "6");
		stat("farming_fortune", "☘", "Farming Fortune", "6");
		stat("foraging_fortune", "☘", "Foraging Fortune", "6");
		stat("fishing_speed", "☂", "Fishing Speed", "b");
		stat("breaking_power", "Ⓟ", "Breaking Power", "2");
		stat("pristine", "✧", "Pristine", "5");
		stat("gemstone_fortune", "☘", "Gemstone Fortune", "6");
		stat("double_hook_chance", "⚓", "Double Hook", "9");
		stat("treasure_chance", "⛃", "Treasure Chance", "6");
		stat("trophy_chance", "♔", "Trophy Chance", "6");
		stat("heat_resistance", "♨", "Heat Resistance", "c");
		stat("cold_resistance", "❄", "Cold Resistance", "b");
		stat("pressure_resistance", "❍", "Pressure Res.", "9");
		stat("respiration", "⚶", "Respiration", "3");
		stat("sweep", "∮", "Sweep", "2");
		stat("pull", "ᛷ", "Pull", "b");
		stat("hunting_fortune", "☘", "Hunting Fortune", "d");
		stat("bonus_pest_chance", "ൠ", "Bonus Pest", "2");
	}

	private static void stat(String id, String symbol, String name, String color) {
		STAT_STYLE.put(id, new String[]{symbol, name, color});
	}

	private int statsScroll;
	private PlayerSkinWidget skinWidget;

	public BasicPage(ProfileViewerScreen screen) {
		super(screen, "Basic Info", Items.PAPER);
	}

	@Override
	public void addWidgets(int left, int top) {
		Minecraft mc = Minecraft.getInstance();
		skinWidget = new PlayerSkinWidget(66, 104, mc.getEntityModels(), () -> {
			UUID id = SkinLoader.parseUuid(profile().uuid);
			return id == null ? DefaultPlayerSkin.getDefaultSkin() : SkinLoader.skin(id).get();
		});
		skinWidget.setPosition(left + LEFT_X + (LEFT_W - 66) / 2, top + 37);
		screen.addPageWidget(skinWidget);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int left, int top, int mx, int my, float delta) {
		PlayerProfile p = profile();
		JsonObject stats = p.stats.data;
		if (stats == null) return;
		renderLeft(g, left, top, mx, my, p, stats);
		renderStats(g, left, top, mx, my, p);
		renderSkills(g, left, top, mx, my, p, stats);
	}

	private void renderLeft(GuiGraphicsExtractor g, int left, int top, int mx, int my, PlayerProfile p, JsonObject stats) {
		int x = left + LEFT_X;
		int cx = x + LEFT_W / 2;

		// Net worth
		String nw;
		JsonObject networth = p.networth.data;
		if (networth != null) nw = "§6" + Fmt.commas(J.num(networth, "normal", "networth"));
		else if (p.networth.error != null) nw = "§cunavailable";
		else nw = "§7...";
		String nwLine = "§6Net Worth: " + nw;
		if (networth != null && Ui.font().width(nwLine) > LEFT_W + 4) {
			// Full number doesn't fit the column: show 1.36B here, the exact value is in the tooltip.
			nwLine = "§6Net Worth: §6" + Fmt.shortNum(J.num(networth, "normal", "networth"));
		}
		Ui.text(g, Ui.fit(nwLine, LEFT_W + 4), x, top + 9);
		if (Ui.inside(mx, my, x, top + 7, LEFT_W, 11)) Ui.tooltip(g, networthTooltip(p), mx, my);

		// Rank + name
		Ui.textCenter(g, Ui.fit(rankedName(stats), LEFT_W + 6), cx, top + 23);

		// Model frame
		Ui.box(g, x, top + 33, LEFT_W, 112);

		// SkyBlock level + skill average
		int level = (int) J.num(stats, "skyblock_level", "level");
		String lvl = "§7SkyBlock Level " + levelColor(level) + level;
		Ui.textCenter(g, lvl, cx, top + 149);
		double avg = J.num(stats, "skills", "averageSkillLevelWithProgress");
		if (avg <= 0) avg = J.num(stats, "skills", "averageSkillLevel");
		Ui.textCenter(g, "§7Skill Avg §6" + Fmt.dec2(avg), cx, top + 161);
		if (Ui.inside(mx, my, x, top + 147, LEFT_W, 24)) {
			Ui.tooltip(g, Ui.lines(
					"§7SkyBlock Level: " + levelColor(level) + Fmt.dec2(J.num(stats, "skyblock_level", "levelWithProgress")),
					"§7Skill Average: §6" + Fmt.dec2(avg) + " §8(" + Fmt.dec2(J.num(stats, "skills", "averageSkillLevel")) + " without progress)",
					"§7Total Skill XP: §f" + Fmt.commas(J.num(stats, "skills", "totalSkillXp")),
					"§7Fairy Souls: §d" + (int) J.num(stats, "fairySouls", "found") + "§7/§d" + (int) J.num(stats, "fairySouls", "total"),
					"§7Joined: §f" + Fmt.date(J.num(stats, "joined"))), mx, my);
		}
		if (skinWidget != null && skinWidget.isHovered()) {
			Ui.tooltip(g, Ui.lines("§7Drag to rotate"), mx, my);
		}
	}

	private List<Component> networthTooltip(PlayerProfile p) {
		JsonObject nw = p.networth.data;
		if (nw == null) {
			return Ui.lines(p.networth.error != null ? "§c" + ProfileViewerScreen.describe(p.networth.error, p) : "§7Loading...");
		}
		JsonObject normal = J.obj(nw, "normal");
		List<String> lines = new ArrayList<>();
		lines.add("§6Net Worth: §f" + Fmt.commas(J.num(normal, "networth")) + " §8(" + Fmt.shortNum(J.num(normal, "networth")) + ")");
		lines.add("§7Unsoulbound: §f" + Fmt.shortNum(J.num(normal, "unsoulboundNetworth")));
		lines.add("§7Non-cosmetic: §f" + Fmt.shortNum(J.num(nw, "nonCosmetic", "networth")));
		lines.add("");
		lines.add("§7Purse: §6" + Fmt.shortNum(J.num(normal, "purse")));
		lines.add("§7Bank: §6" + Fmt.shortNum(J.num(normal, "bank")) + (J.num(normal, "personalBank") > 0 ? " §8+ §6" + Fmt.shortNum(J.num(normal, "personalBank")) + " §8personal" : ""));
		Map<String, JsonObject> types = J.entries(normal, "types");
		List<Map.Entry<String, JsonObject>> sorted = new ArrayList<>(types.entrySet());
		sorted.sort((a, b) -> Double.compare(J.num(b.getValue(), "total"), J.num(a.getValue(), "total")));
		for (Map.Entry<String, JsonObject> e : sorted) {
			double total = J.num(e.getValue(), "total");
			if (total <= 0) continue;
			lines.add("§7" + Fmt.titleCase(e.getKey()) + ": §f" + Fmt.shortNum(total));
		}
		return Ui.lines(lines);
	}

	private void renderStats(GuiGraphicsExtractor g, int left, int top, int mx, int my, PlayerProfile p) {
		int x = left + STATS_X;
		int y = top + CONTENT_Y;
		int h = CONTENT_H;
		Ui.box(g, x, y, STATS_W, h);
		if (placeholder(g, p.playerStats, x + STATS_W / 2, y + h / 2)) return;

		List<JsonObject> rows = orderedStats(p.playerStats.data);
		int visible = (h - 6) / ROW_H;
		statsScroll = Math.max(0, Math.min(statsScroll, rows.size() - visible));
		for (int i = 0; i < visible && i + statsScroll < rows.size(); i++) {
			JsonObject s = rows.get(i + statsScroll);
			int ry = y + 4 + i * ROW_H;
			String id = J.str(s, "id");
			String[] style = STAT_STYLE.get(id);
			String color = "§" + (style != null ? style[2] : J.strOr(s, "f", "color"));
			String symbol = style != null ? style[0] : safeSymbol(J.str(s, "symbol"));
			String name = style != null ? style[1] : J.strOr(s, Fmt.titleCase(id), "nameShort");
			double total = total(s);
			String value = Fmt.commas(total);
			int valueW = Ui.font().width(value);
			Ui.text(g, Ui.fit(color + symbol + " " + name, STATS_W - 8 - valueW - 2), x + 3, ry);
			Ui.textRight(g, "§f" + value, x + STATS_W - 3, ry);
			if (Ui.inside(mx, my, x, ry - 1, STATS_W, ROW_H)) {
				g.fill(x + 1, ry - 1, x + STATS_W - 1, ry + ROW_H - 2, Ui.ROW_HOVER);
				Ui.tooltip(g, statTooltip(s, color + symbol + " " + J.strOr(s, name, "name")), mx, my);
			}
		}
		if (rows.size() > visible) {
			// Scrollbar
			int trackH = h - 4;
			int barH = Math.max(10, trackH * visible / rows.size());
			int barY = y + 2 + (trackH - barH) * statsScroll / Math.max(1, rows.size() - visible);
			g.fill(x + STATS_W - 2, barY, x + STATS_W - 1, barY + barH, 0xFF8A8A90);
		}
	}

	private static List<JsonObject> orderedStats(JsonObject playerStats) {
		Map<String, JsonObject> byId = new LinkedHashMap<>();
		for (JsonObject s : J.objects(playerStats, "stats")) byId.put(J.str(s, "id"), s);
		List<JsonObject> out = new ArrayList<>();
		for (String id : STAT_STYLE.keySet()) {
			JsonObject s = byId.remove(id);
			if (s != null) out.add(s);
		}
		for (JsonObject s : byId.values()) if (total(s) != 0) out.add(s);
		return out;
	}

	private static double total(JsonObject s) {
		JsonObject info = J.obj(s, "statsInfo");
		if (info == null) return 0;
		if (info.has("total")) return J.num(info, "total");
		double sum = 0;
		for (Map.Entry<String, JsonElement> e : info.entrySet()) sum += J.num(e.getValue());
		return sum;
	}

	private static List<Component> statTooltip(JsonObject s, String title) {
		List<String> lines = new ArrayList<>();
		boolean percent = J.bool(s, "percent");
		String suffix = percent ? "%" : J.str(s, "suffix");
		lines.add(title + " §f" + Fmt.commas(total(s)) + suffix);
		JsonObject info = J.obj(s, "statsInfo");
		if (info != null) {
			for (Map.Entry<String, JsonElement> e : info.entrySet()) {
				if (e.getKey().equals("total")) continue;
				double v = J.num(e.getValue());
				if (v == 0) continue;
				lines.add("§7" + Fmt.titleCase(e.getKey()) + ": " + (v > 0 ? "§a+" : "§c") + Fmt.commas(v) + suffix);
			}
		}
		if (J.has(s, "cap")) lines.add("§8Cap: " + Fmt.commas(J.num(s, "cap")) + suffix);
		String desc = J.str(s, "description");
		if (!desc.isEmpty()) lines.add("§8" + Ui.fit(desc, 220));
		return Ui.lines(lines);
	}

	private static String safeSymbol(String symbol) {
		if (symbol == null || symbol.isEmpty()) return "•";
		char c = symbol.charAt(0);
		return c >= 0xE000 && c <= 0xF8FF ? "•" : symbol;
	}

	private void renderSkills(GuiGraphicsExtractor g, int left, int top, int mx, int my, PlayerProfile p, JsonObject stats) {
		int x = left + SKILLS_X;
		int y = top + CONTENT_Y;
		Ui.box(g, x, y, SKILLS_W, CONTENT_H);
		int colW = (SKILLS_W - 6) / 2;
		for (int col = 0; col < 2; col++) {
			List<Entry> entries = col == 0 ? COL_1 : COL_2;
			int cx = x + 3 + col * (colW + 1);
			for (int i = 0; i < entries.size(); i++) {
				int ry = y + 4 + i * SKILL_ROW_H;
				Entry e = entries.get(i);
				SkillRow row = e.slayer ? slayerRow(p, e) : skillRow(stats, e);
				g.pose().pushMatrix();
				g.pose().translate(cx, ry);
				g.pose().scale(0.75f, 0.75f);
				g.item(Ui.stack(e.icon), 0, 0);
				g.pose().popMatrix();
				String level = row == null ? "§8-" : (row.maxed ? "§6" : "§f") + row.level;
				int lw = Ui.font().width(level);
				Ui.text(g, Ui.fit(e.color + e.label, colW - 16 - lw - 2), cx + 14, ry + 1);
				Ui.textRight(g, level, cx + colW - 2, ry + 1);
				Ui.bar(g, cx + 14, ry + 11, colW - 16, 3, row == null ? 0 : row.progress, Fmt.codeRgb(e.color.charAt(1)), row != null && row.maxed);
				if (Ui.inside(mx, my, cx, ry, colW, SKILL_ROW_H)) {
					Ui.tooltip(g, row == null ? Ui.lines(e.color + e.label, p.combined.error != null && e.slayer ? "§cCouldn't load slayers" : "§7No data") : row.tooltip, mx, my);
				}
			}
		}
	}

	private record SkillRow(int level, double progress, boolean maxed, List<Component> tooltip) {}

	private static SkillRow skillRow(JsonObject stats, Entry e) {
		JsonObject s = J.obj(stats, "skills", "skills", e.key);
		if (s == null) return null;
		int level = (int) J.num(s, "level");
		double withProgress = J.num(s, level, "levelWithProgress");
		boolean maxed = J.bool(s, "maxed");
		double frac = withProgress - Math.floor(withProgress);
		double cur = J.num(s, "xpCurrent");
		double next = J.num(s, "xpForNext");
		List<String> lines = new ArrayList<>();
		lines.add(e.color + e.label + " " + level + (J.has(s, "levelCap") ? " §8/ " + (int) J.num(s, "levelCap") : ""));
		if (maxed) lines.add("§6§lMAXED");
		else if (next > 0) lines.add("§7Progress: §e" + Fmt.commas(cur) + "§6/§e" + Fmt.shortNum(next) + " §8(" + Fmt.dec(frac * 100) + "%)");
		lines.add("§7Total XP: §f" + Fmt.commas(J.num(s, "xp")));
		int uncapped = (int) J.num(s, level, "uncappedLevel");
		if (uncapped > level) lines.add("§8Uncapped level: " + uncapped);
		return new SkillRow(level, frac, maxed, Ui.lines(lines));
	}

	private static SkillRow slayerRow(PlayerProfile p, Entry e) {
		JsonObject slayer = J.obj(p.combined.data, "slayers", "data", e.key);
		if (slayer == null) return null;
		int level = (int) J.num(slayer, "level", "level");
		double xp = J.num(slayer, "level", "xp");
		boolean maxed = J.bool(slayer, "level", "maxed");
		int[] table = SLAYER_XP.get(e.key);
		double frac;
		double nextXp = J.num(slayer, "level", "xpForNext");
		if (table != null && level + 1 < table.length) {
			double prev = table[level];
			double nxt = table[level + 1];
			frac = (xp - prev) / Math.max(1, nxt - prev);
			if (nextXp <= 0) nextXp = nxt;
		} else {
			frac = nextXp > 0 ? xp / nextXp : 1;
		}
		List<String> lines = new ArrayList<>();
		lines.add(e.color + J.strOr(slayer, e.label, "name") + " §f" + level);
		lines.add("§7XP: §e" + Fmt.commas(xp) + (maxed ? " §6§lMAXED" : nextXp > 0 ? "§6/§e" + Fmt.shortNum(nextXp) : ""));
		JsonObject kills = J.obj(slayer, "kills");
		if (kills != null) {
			StringBuilder sb = new StringBuilder("§7Kills: ");
			for (int t = 1; t <= 5; t++) {
				double k = J.num(kills, String.valueOf(t));
				if (k > 0) sb.append("§8T").append(t).append(" §f").append(Fmt.commas(k)).append(' ');
			}
			lines.add(sb.toString().trim());
			lines.add("§7Total kills: §f" + Fmt.commas(J.num(kills, "total")));
		}
		return new SkillRow(level, frac, maxed, Ui.lines(lines));
	}

	public static String rankedName(JsonObject stats) {
		String name = J.strOr(stats, "?", "username");
		String rankText = J.str(stats, "rank", "rankText");
		if (rankText.isEmpty()) return "§7" + name;
		String rankColor = Fmt.colorCode(J.str(stats, "rank", "rankColor"));
		String plus = J.str(stats, "rank", "plusText");
		String plusColor = Fmt.colorCode(J.str(stats, "rank", "plusColor"));
		return rankColor + "[" + rankText + (plus.isEmpty() ? "" : plusColor + plus + rankColor) + "] " + name;
	}

	public static String levelColor(int level) {
		String[] colors = {"§7", "§f", "§e", "§a", "§2", "§b", "§3", "§9", "§d", "§5", "§6", "§c", "§4"};
		return colors[Math.max(0, Math.min(colors.length - 1, level / 40))];
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount, int left, int top) {
		if (Ui.inside(mx, my, left + STATS_X, top + CONTENT_Y, STATS_W, CONTENT_H)) {
			statsScroll -= (int) Math.signum(amount) * 2;
			if (statsScroll < 0) statsScroll = 0;
			return true;
		}
		return false;
	}
}
