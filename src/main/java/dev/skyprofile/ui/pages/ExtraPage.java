package dev.skyprofile.ui.pages;

import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.Fmt;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Assorted numbers in three columns, like NEU's Extra Info page. */
public class ExtraPage extends Page {
	private static final int COL_W = 132;

	private record Row(String label, String value) {}

	public ExtraPage(ProfileViewerScreen screen) {
		super(screen, "Extra Info", Items.BOOK);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int left, int top, int mx, int my, float delta) {
		PlayerProfile p = profile();
		JsonObject s = p.stats.data;
		JsonObject c = p.combined.data;
		if (s == null) return;

		List<Row> general = new ArrayList<>();
		general.add(new Row("Purse", "§6" + Fmt.shortNum(J.num(s, "purse"))));
		general.add(new Row("Bank", J.has(s, "bank") ? "§6" + Fmt.shortNum(J.num(s, "bank")) : "§8API off"));
		general.add(new Row("Personal Bank", "§6" + Fmt.shortNum(J.num(s, "personalBank"))));
		general.add(new Row("Fairy Souls", "§d" + (int) J.num(s, "fairySouls", "found") + "§7/§d" + (int) J.num(s, "fairySouls", "total")));
		general.add(new Row("SkyBlock Level", BasicPage.levelColor((int) J.num(s, "skyblock_level", "level")) + Fmt.dec2(J.num(s, "skyblock_level", "levelWithProgress"))));
		general.add(new Row("Joined", "§f" + Fmt.date(J.num(s, "joined"))));
		general.add(new Row("Profile", "§a" + p.cuteName() + ProfileViewerScreen.modeIcon(p.gameMode())));
		int members = 0;
		for (JsonObject m : J.objects(s, "members")) if (!J.bool(m, "removed")) members++;
		general.add(new Row("Co-op members", "§f" + Math.max(1, members)));
		general.add(new Row("Total Skill XP", "§f" + Fmt.shortNum(J.num(s, "skills", "totalSkillXp"))));

		List<Row> combat = new ArrayList<>();
		List<Row> other = new ArrayList<>();
		if (c != null) {
			combat.add(new Row("Slayer XP", "§f" + Fmt.commas(J.num(c, "slayers", "totalSlayerExp"))));
			combat.add(new Row("Kills", "§f" + Fmt.commas(J.num(c, "misc", "kills", "total_kills"))));
			combat.add(new Row("Deaths", "§f" + Fmt.commas(J.num(c, "misc", "kills", "total_deaths"))));
			combat.add(new Row("Magical Power", "§6" + Fmt.commas(J.num(c, "accessories", "magicalPower", "total"))));
			String power = J.str(c, "accessories", "selectedPower");
			combat.add(new Row("Selected Power", power.isEmpty() ? "§8None" : "§a" + Fmt.titleCase(power)));
			combat.add(new Row("Accessories", "§f" + (int) J.num(c, "accessories", "unique") + " §8unique"));
			combat.add(new Row("Highest Crit", "§c" + Fmt.shortNum(J.num(c, "misc", "damage", "highest_critical_damage"))));
			combat.add(new Row("Bestiary Level", "§f" + Fmt.dec(J.num(c, "bestiary", "level"))));
			combat.add(new Row("Kuudra Kills", "§f" + Fmt.commas(J.num(c, "crimsonIsle", "kuudra", "totalKills"))));
			String faction = J.str(c, "crimsonIsle", "factions", "selectedFaction");
			combat.add(new Row("Faction", faction.isEmpty() ? "§8None" : (faction.toLowerCase().startsWith("mage") ? "§5" : "§c") + Fmt.titleCase(faction)));

			JsonObject mining = J.obj(c, "skills", "mining");
			other.add(new Row("HOTM Level", "§5" + (int) J.num(mining, "level", "level")));
			other.add(new Row("Mithril Powder", "§2" + Fmt.shortNum(J.num(mining, "powder", "mithril", "total"))));
			other.add(new Row("Gemstone Powder", "§d" + Fmt.shortNum(J.num(mining, "powder", "gemstone", "total"))));
			other.add(new Row("Glacite Powder", "§b" + Fmt.shortNum(J.num(mining, "powder", "glacite", "total"))));
			other.add(new Row("Minion Slots", "§f" + (int) J.num(c, "minions", "minionsSlots", "current")));
			other.add(new Row("Unique Minions", "§f" + (int) J.num(c, "minions", "totalTiers")));
			other.add(new Row("Maxed Collections", "§f" + (int) J.num(c, "collections", "maxedCollections") + "§7/" + (int) J.num(c, "collections", "totalCollections")));
			other.add(new Row("Pets", "§f" + (int) J.num(c, "pets", "amount") + "§7/" + (int) J.num(c, "pets", "total")));
			other.add(new Row("Pet Score", "§b" + (int) J.num(c, "pets", "petScore", "amount")));
			other.add(new Row("Rift Motes", "§d" + Fmt.shortNum(J.num(c, "rift", "motes", "lifetime")) + " §8lifetime"));
		}

		int y = top + CONTENT_Y;
		drawColumn(g, left + CONTENT_X, y, "§eGeneral", general);
		if (c == null) {
			int cx = left + CONTENT_X + COL_W + 4;
			Ui.box(g, cx, y, CONTENT_W - COL_W - 4, CONTENT_H);
			placeholder(g, p.combined, cx + (CONTENT_W - COL_W - 4) / 2, y + CONTENT_H / 2);
			return;
		}
		drawColumn(g, left + CONTENT_X + COL_W + 4, y, "§cCombat", combat);
		drawColumn(g, left + CONTENT_X + 2 * (COL_W + 4), y, "§bOther", other);
	}

	private void drawColumn(GuiGraphicsExtractor g, int x, int y, String title, List<Row> rows) {
		Ui.box(g, x, y, COL_W, CONTENT_H);
		Ui.text(g, title, x + 5, y + 5);
		g.fill(x + 4, y + 15, x + COL_W - 4, y + 16, Ui.BOX_EDGE);
		int ry = y + 20;
		for (Row r : rows) {
			if (ry > y + CONTENT_H - 10) break;
			int vw = Ui.font().width(r.value);
			Ui.text(g, Ui.fit("§7" + r.label, COL_W - 10 - vw - 3), x + 5, ry);
			Ui.textRight(g, r.value, x + COL_W - 5, ry);
			ry += 13;
		}
	}
}
