package dev.skyprofile.ui.pages;

import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.Fmt;
import dev.skyprofile.ui.Icons;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CollectionsPage extends Page {
	private static final int LIST_W = 96;
	private static final int GRID_X = 108;
	private static final int GRID_W = 304;
	private static final int ROW_H = 13;
	private static final int CELL_H = 21;
	private static final int COLS = 3;

	private int selected;
	private int scroll;

	public CollectionsPage(ProfileViewerScreen screen) {
		super(screen, "Collections", Items.PAINTING);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int left, int top, int mx, int my, float delta) {
		PlayerProfile p = profile();
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		Ui.box(g, x, y, LIST_W, CONTENT_H);
		Ui.box(g, left + GRID_X, y, GRID_W, CONTENT_H);
		if (placeholder(g, p.combined, left + CONTENT_X + CONTENT_W / 2, y + CONTENT_H / 2)) return;

		List<JsonObject> categories = new ArrayList<>(J.entries(p.combined.data, "collections", "categories").values());
		if (categories.isEmpty()) {
			Ui.textCenter(g, "§7No collection data (the player may have collections API off).", left + CONTENT_X + CONTENT_W / 2, y + CONTENT_H / 2);
			return;
		}
		selected = Math.min(selected, categories.size() - 1);

		Ui.text(g, "§7Maxed: §6" + (int) J.num(p.combined.data, "collections", "maxedCollections") + "§7/" + (int) J.num(p.combined.data, "collections", "totalCollections"), x + 4, y + 4);
		g.fill(x + 3, y + 14, x + LIST_W - 3, y + 15, Ui.BOX_EDGE);
		for (int i = 0; i < categories.size(); i++) {
			JsonObject cat = categories.get(i);
			int ry = y + 18 + i * ROW_H;
			if (ry > y + CONTENT_H - ROW_H) break;
			boolean hover = Ui.inside(mx, my, x + 1, ry, LIST_W - 2, ROW_H);
			if (i == selected) g.fill(x + 1, ry, x + LIST_W - 1, ry + ROW_H, Ui.ROW_SELECTED);
			else if (hover) g.fill(x + 1, ry, x + LIST_W - 1, ry + ROW_H, Ui.ROW_HOVER);
			int maxTiers = (int) J.num(cat, "maxTiers");
			int totalTiers = (int) J.num(cat, "totalTiers");
			boolean done = maxTiers > 0 && maxTiers >= totalTiers;
			int tx = x + 4;
			if (Icons.draw(g, J.str(cat, "texture"), x + 3, ry + 1, 10)) tx = x + 16;
			String counter = (done ? "§6" : "§8") + maxTiers + "/" + totalTiers;
			int cw = Ui.font().width(counter);
			Ui.text(g, Ui.fit((i == selected ? "§f" : "§7") + J.strOr(cat, "?", "name"), LIST_W - (tx - x) - cw - 6), tx, ry + 3);
			Ui.textRight(g, counter, x + LIST_W - 4, ry + 3);
		}

		JsonObject cat = categories.get(selected);
		List<JsonObject> items = J.objects(cat, "items");
		int gx = left + GRID_X;
		int cellW = (GRID_W - 8) / COLS;
		int rows = (items.size() + COLS - 1) / COLS;
		int visibleRows = (CONTENT_H - 6) / CELL_H;
		scroll = Math.max(0, Math.min(scroll, rows - visibleRows));
		JsonObject hovered = null;
		for (int r = 0; r < visibleRows && r + scroll < rows; r++) {
			for (int c = 0; c < COLS; c++) {
				int index = (r + scroll) * COLS + c;
				if (index >= items.size()) break;
				JsonObject it = items.get(index);
				int cx = gx + 4 + c * cellW;
				int cy = y + 4 + r * CELL_H;
				boolean hover = Ui.inside(mx, my, cx, cy, cellW - 2, CELL_H - 1);
				g.fill(cx, cy, cx + cellW - 2, cy + CELL_H - 1, hover ? 0x40FFFFFF : 0x30000000);
				int tier = (int) J.num(it, "tier");
				int maxTier = (int) J.num(it, "maxTier");
				boolean maxed = maxTier > 0 && tier >= maxTier;
				boolean locked = tier <= 0 && J.num(it, "totalAmount") <= 0 && J.num(it, "amount") <= 0;
				if (!Icons.draw(g, J.str(it, "texture"), cx + 2, cy + 2, 16)) g.fill(cx + 2, cy + 2, cx + 18, cy + 18, 0x40FFFFFF);
				String tierText = locked ? "§8-" : maxed ? "§6" + Fmt.roman(tier) : "§e" + Fmt.roman(tier);
				int tw = Ui.font().width(tierText);
				Ui.text(g, Ui.fit((locked ? "§8" : "§f") + J.strOr(it, "?", "name"), cellW - 24 - tw - 4), cx + 21, cy + 2);
				Ui.textRight(g, tierText, cx + cellW - 5, cy + 2);
				double amount = J.num(it, "totalAmount");
				if (amount <= 0) amount = J.num(it, "amount");
				Ui.textScaled(g, "§7" + Fmt.shortNum(amount), cx + 21, cy + 12.5f, 0.75f);
				if (hover) hovered = it;
			}
		}
		if (hovered != null) {
			List<String> lines = new ArrayList<>();
			int tier = (int) J.num(hovered, "tier");
			int maxTier = (int) J.num(hovered, "maxTier");
			lines.add("§f" + J.strOr(hovered, "?", "name") + " " + (tier >= maxTier && maxTier > 0 ? "§6" : "§e") + Fmt.roman(tier));
			lines.add("§7Tier: §f" + tier + "§7/" + maxTier + (tier >= maxTier && maxTier > 0 ? " §6§lMAXED" : ""));
			lines.add("§7Collected: §f" + Fmt.commas(J.num(hovered, "amount")) + " §8(you)");
			double total = J.num(hovered, "totalAmount");
			if (total > J.num(hovered, "amount")) lines.add("§7Co-op total: §f" + Fmt.commas(total));
			List<JsonObject> amounts = J.objects(hovered, "amounts");
			if (amounts.size() > 1) {
				lines.add("");
				for (JsonObject a : amounts) lines.add("§7" + J.strOr(a, "?", "username") + ": §f" + Fmt.commas(J.num(a, "amount")));
			}
			Ui.tooltip(g, Ui.lines(lines), mx, my);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, int left, int top) {
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		if (Ui.inside(mx, my, x, y + 18, LIST_W, CONTENT_H - 18)) {
			int index = (int) ((my - y - 18) / ROW_H);
			Map<String, JsonObject> cats = J.entries(profile().combined.data, "collections", "categories");
			if (index >= 0 && index < cats.size()) {
				selected = index;
				scroll = 0;
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount, int left, int top) {
		if (Ui.inside(mx, my, left + GRID_X, top + CONTENT_Y, GRID_W, CONTENT_H)) {
			scroll = Math.max(0, scroll - (int) Math.signum(amount));
			return true;
		}
		return false;
	}
}
