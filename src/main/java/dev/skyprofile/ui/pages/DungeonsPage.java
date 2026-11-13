package dev.skyprofile.ui.pages;

import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.Fmt;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DungeonsPage extends Page {
	private static final int LEFT_W = 196;
	private static final int RIGHT_X = 208;
	private static final int RIGHT_W = 204;

	private record DClass(String key, String label, String color, Item icon) {}

	private static final List<DClass> CLASSES = List.of(
			new DClass("healer", "Healer", "§d", Items.SPLASH_POTION),
			new DClass("mage", "Mage", "§b", Items.BLAZE_ROD),
			new DClass("berserk", "Berserk", "§c", Items.IRON_SWORD),
			new DClass("archer", "Archer", "§6", Items.BOW),
			new DClass("tank", "Tank", "§a", Items.LEATHER_CHESTPLATE));

	private boolean master;

	public DungeonsPage(ProfileViewerScreen screen) {
		super(screen, "Dungeons", Items.WITHER_SKELETON_SKULL);
	}

	@Override
	public void addWidgets(int left, int top) {
		Button toggle = Button.builder(Component.literal(master ? "§cMaster Mode" : "§aNormal"), b -> {
			master = !master;
			b.setMessage(Component.literal(master ? "§cMaster Mode" : "§aNormal"));
		}).bounds(left + RIGHT_X + RIGHT_W - 76, top + CONTENT_Y + 3, 72, 14).build();
		screen.addPageWidget(toggle);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int left, int top, int mx, int my, float delta) {
		PlayerProfile p = profile();
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		Ui.box(g, x, y, LEFT_W, CONTENT_H);
		Ui.box(g, left + RIGHT_X, y, RIGHT_W, CONTENT_H);
		if (placeholder(g, p.combined, left + CONTENT_X + CONTENT_W / 2, y + CONTENT_H / 2)) return;
		JsonObject d = J.obj(p.combined.data, "dungeons");
		if (d == null) {
			Ui.textCenter(g, "§7This profile hasn't played Dungeons.", left + CONTENT_X + CONTENT_W / 2, y + CONTENT_H / 2);
			return;
		}
		renderLeft(g, x, y, mx, my, d);
		renderFloors(g, left + RIGHT_X, y, mx, my, d);
	}

	private void renderLeft(GuiGraphicsExtractor g, int x, int y, int mx, int my, JsonObject d) {
		JsonObject cata = J.obj(d, "level");
		int level = (int) J.num(cata, "level");
		double withProgress = J.num(cata, level, "levelWithProgress");
		boolean maxed = J.bool(cata, "maxed");
		Ui.text(g, "§cThe Catacombs §f" + level, x + 6, y + 6);
		Ui.textRight(g, "§7" + Fmt.dec2(withProgress), x + LEFT_W - 6, y + 6);
		Ui.bar(g, x + 6, y + 17, LEFT_W - 12, 4, withProgress - Math.floor(withProgress), 0xFF5555, maxed);
		if (Ui.inside(mx, my, x, y + 4, LEFT_W, 20)) {
			Ui.tooltip(g, Ui.lines("§cCatacombs " + Fmt.dec2(withProgress),
					maxed ? "§6§lMAXED" : "§7Progress: §e" + Fmt.commas(J.num(cata, "xpCurrent")) + "§6/§e" + Fmt.shortNum(J.num(cata, "xpForNext")),
					"§7Total XP: §f" + Fmt.commas(J.num(cata, "xp"))), mx, my);
		}

		JsonObject classes = J.obj(d, "classes");
		String selected = J.str(classes, "selectedClass").toLowerCase(Locale.ROOT);
		int ry = y + 27;
		for (DClass c : CLASSES) {
			JsonObject s = J.obj(classes, "classes", c.key);
			int lv = (int) J.num(s, "level");
			double wp = J.num(s, lv, "levelWithProgress");
			boolean mx50 = J.bool(s, "maxed");
			g.pose().pushMatrix();
			g.pose().translate(x + 6, ry);
			g.pose().scale(0.75f, 0.75f);
			g.item(Ui.stack(c.icon), 0, 0);
			g.pose().popMatrix();
			boolean isSelected = selected.equals(c.key);
			Ui.text(g, c.color + c.label + (isSelected ? " §6★" : ""), x + 20, ry + 1);
			Ui.textRight(g, (mx50 ? "§6" : "§f") + lv, x + LEFT_W - 6, ry + 1);
			Ui.bar(g, x + 20, ry + 11, LEFT_W - 26, 3, wp - Math.floor(wp), Fmt.codeRgb(c.color.charAt(1)), mx50);
			if (Ui.inside(mx, my, x, ry, LEFT_W, 16)) {
				Ui.tooltip(g, Ui.lines(c.color + c.label + " " + Fmt.dec2(wp) + (isSelected ? " §6(selected)" : ""),
						"§7Total XP: §f" + Fmt.commas(J.num(s, "xp"))), mx, my);
			}
			ry += 17;
		}

		ry += 2;
		Ui.text(g, "§7Class Average: §f" + Fmt.dec2(J.num(classes, "classAverageWithProgress")), x + 6, ry);
		ry += 11;
		JsonObject st = J.obj(d, "stats");
		Ui.text(g, "§7Secrets: §f" + Fmt.commas(J.num(st, "secrets", "found")) + " §8(" + Fmt.dec2(J.num(st, "secrets", "secretsPerRun")) + "/run)", x + 6, ry);
		ry += 11;
		int hn = (int) J.num(st, -1, "highestFloorBeatenNormal");
		int hm = (int) J.num(st, -1, "highestFloorBeatenMaster");
		Ui.text(g, "§7Highest: §f" + (hn < 0 ? "-" : hn == 0 ? "Entrance" : "F" + hn) + " §8| §c" + (hm <= 0 ? "-" : "M" + hm), x + 6, ry);
		ry += 11;
		Ui.text(g, "§7Blood mob kills: §f" + Fmt.commas(J.num(st, "bloodMobKills")), x + 6, ry);
	}

	private void renderFloors(GuiGraphicsExtractor g, int x, int y, int mx, int my, JsonObject d) {
		Ui.text(g, master ? "§cMaster Catacombs" : "§aThe Catacombs", x + 6, y + 6);
		List<JsonObject> floors = J.objects(d, master ? "master_catacombs" : "catacombs");
		int[] cols = {x + 6, x + 56, x + 98, x + 136, x + 172};
		int hy = y + 24;
		Ui.text(g, "§8Floor", cols[0], hy);
		Ui.text(g, "§8Runs", cols[1], hy);
		Ui.text(g, "§8Best", cols[2], hy);
		Ui.text(g, "§8S", cols[3], hy);
		Ui.text(g, "§8S+", cols[4], hy);
		g.fill(x + 4, hy + 10, x + RIGHT_W - 4, hy + 11, Ui.BOX_EDGE);
		if (floors.isEmpty()) {
			Ui.textCenter(g, "§7No " + (master ? "Master Mode " : "") + "runs yet.", x + RIGHT_W / 2, y + 90);
			return;
		}
		int ry = hy + 15;
		for (JsonObject f : floors) {
			if (ry > y + CONTENT_H - 12) break;
			JsonObject s = J.obj(f, "stats");
			String name = floorName(J.strOr(f, "?", "name"));
			Ui.text(g, (master ? "§c" : "§a") + name, cols[0], ry);
			Ui.text(g, "§f" + Fmt.commas(J.num(s, "tier_completions")), cols[1], ry);
			double best = J.num(s, "best_score");
			Ui.text(g, scoreColor(best) + (best > 0 ? (int) best : "-"), cols[2], ry);
			Ui.text(g, "§f" + Fmt.time(J.num(s, "fastest_time_s")), cols[3], ry);
			Ui.text(g, "§6" + Fmt.time(J.num(s, "fastest_time_s_plus")), cols[4], ry);
			if (Ui.inside(mx, my, x + 2, ry - 2, RIGHT_W - 4, 13)) {
				g.fill(x + 2, ry - 2, x + RIGHT_W - 2, ry + 10, Ui.ROW_HOVER);
				Ui.tooltip(g, floorTooltip(f, name), mx, my);
			}
			ry += 14;
		}
	}

	private List<Component> floorTooltip(JsonObject f, String name) {
		JsonObject s = J.obj(f, "stats");
		JsonObject best = J.obj(f, "best_run");
		List<String> lines = new ArrayList<>();
		lines.add((master ? "§c" : "§a") + J.strOr(f, name, "name") + (master ? " (Master)" : ""));
		lines.add("§7Times played: §f" + Fmt.commas(J.num(s, "times_played")));
		lines.add("§7Completions: §f" + Fmt.commas(J.num(s, "tier_completions")));
		lines.add("§7Fastest: §f" + Fmt.time(J.num(s, "fastest_time")) + " §8| §7S: §f" + Fmt.time(J.num(s, "fastest_time_s")) + " §8| §7S+: §6" + Fmt.time(J.num(s, "fastest_time_s_plus")));
		lines.add("§7Mobs killed: §f" + Fmt.commas(J.num(s, "mobs_killed")) + " §8| §7Watcher kills: §f" + Fmt.commas(J.num(s, "watcher_kills")));
		double dmg = J.num(s, "most_damage", "value");
		if (dmg > 0) lines.add("§7Most damage: §c" + Fmt.shortNum(dmg) + " §8(" + Fmt.titleCase(J.str(s, "most_damage", "class")) + ")");
		if (best != null) {
			lines.add("");
			lines.add("§6Best run §8- " + Fmt.date(J.num(best, "timestamp")));
			lines.add("§7Grade: §f" + J.strOr(best, "?", "grade") + " §8| §7Time: §f" + Fmt.time(J.num(best, "elapsed_time")) + " §8| §7" + Fmt.titleCase(J.str(best, "dungeon_class")));
			lines.add("§7Secrets: §f" + (int) J.num(best, "secrets_found") + " §8| §7Deaths: §f" + (int) J.num(best, "deaths"));
		}
		return Ui.lines(lines);
	}

	private String floorName(String raw) {
		String r = raw.toLowerCase(Locale.ROOT);
		if (r.startsWith("entrance")) return "Entrance";
		String digits = r.replaceAll("[^0-9]", "");
		return digits.isEmpty() ? raw : (master ? "M" : "F") + digits;
	}

	private static String scoreColor(double score) {
		if (score >= 300) return "§6";
		if (score >= 270) return "§e";
		if (score >= 230) return "§a";
		if (score > 0) return "§7";
		return "§8";
	}
}
