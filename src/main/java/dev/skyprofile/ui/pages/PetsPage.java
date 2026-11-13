package dev.skyprofile.ui.pages;

import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.Fmt;
import dev.skyprofile.ui.Icons;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PetsPage extends Page {
	private static final int GRID_W = 276;
	private static final int DETAIL_X = 288;
	private static final int DETAIL_W = 124;
	private static final int CELL = 20;

	private int scroll;
	private int selected = -1;

	public PetsPage(ProfileViewerScreen screen) {
		super(screen, "Pets", Items.BONE);
	}

	private List<JsonObject> pets(PlayerProfile p) {
		List<JsonObject> pets = J.objects(p.combined.data, "pets", "pets");
		pets.sort(Comparator.comparing((JsonObject o) -> !J.bool(o, "active"))
				.thenComparing(o -> -rarityRank(J.str(o, "rarity")))
				.thenComparing(o -> -J.num(o, "level")));
		return pets;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int left, int top, int mx, int my, float delta) {
		PlayerProfile p = profile();
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		Ui.box(g, x, y, GRID_W, CONTENT_H);
		Ui.box(g, left + DETAIL_X, y, DETAIL_W, CONTENT_H);
		if (placeholder(g, p.combined, left + CONTENT_X + CONTENT_W / 2, y + CONTENT_H / 2)) return;

		JsonObject petsData = J.obj(p.combined.data, "pets");
		List<JsonObject> pets = pets(p);
		Ui.text(g, "§7Pets §f" + (int) J.num(petsData, pets.size(), "amount") + "§7/" + (int) J.num(petsData, "total")
				+ "  §7Score §b" + (int) J.num(petsData, "petScore", "amount")
				+ "  §7Candy §f" + (int) J.num(petsData, "totalCandyUsed"), x + 5, y + 5);
		if (pets.isEmpty()) {
			Ui.textCenter(g, "§7No pets.", x + GRID_W / 2, y + CONTENT_H / 2);
			return;
		}

		int cols = (GRID_W - 8) / CELL;
		int rows = (pets.size() + cols - 1) / cols;
		int visibleRows = (CONTENT_H - 22) / CELL;
		scroll = Math.max(0, Math.min(scroll, rows - visibleRows));
		int startX = x + (GRID_W - cols * CELL) / 2;
		int startY = y + 17;
		int hovered = -1;
		for (int r = 0; r < visibleRows && r + scroll < rows; r++) {
			for (int c = 0; c < cols; c++) {
				int index = (r + scroll) * cols + c;
				if (index >= pets.size()) break;
				JsonObject pet = pets.get(index);
				int cx = startX + c * CELL;
				int cy = startY + r * CELL;
				boolean hover = Ui.inside(mx, my, cx, cy, CELL - 1, CELL - 1);
				if (hover) hovered = index;
				g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF1A1A1E);
				int edge = J.bool(pet, "active") ? 0xFF55FF55 : 0xFF000000 | dim(Fmt.rarityRgb(J.str(pet, "rarity")));
				if (index == selected) edge = 0xFFFFFFFF;
				g.outline(cx, cy, CELL - 1, CELL - 1, edge);
				if (!Icons.draw(g, J.str(pet, "texture_path"), cx + 1, cy + 1, 16)) {
					Ui.textCenter(g, "§7?", cx + CELL / 2, cy + 6);
				}
				int level = (int) J.num(pet, "level");
				Ui.textScaled(g, (level >= J.num(pet, 100, "maxLevel") ? "§6" : "§f") + level, cx + 1.5f, cy + 13.5f, 0.6f);
				if (hover) g.fill(cx + 1, cy + 1, cx + CELL - 2, cy + CELL - 2, 0x30FFFFFF);
			}
		}

		int show = hovered >= 0 ? hovered : selected >= 0 && selected < pets.size() ? selected : 0;
		renderDetail(g, left + DETAIL_X, y, pets.get(show));
	}

	private void renderDetail(GuiGraphicsExtractor g, int x, int y, JsonObject pet) {
		String rarity = J.str(pet, "rarity");
		String color = Fmt.rarityColor(rarity);
		String name = J.strOr(pet, "", "display_name");
		if (name.isEmpty()) name = Fmt.titleCase(J.strOr(pet, "?", "type"));
		if (!name.startsWith("§")) name = color + name;
		Icons.draw(g, J.str(pet, "texture_path"), x + 4, y + 4, 16);
		Ui.text(g, Ui.fit(name, DETAIL_W - 26), x + 23, y + 4);
		Ui.text(g, "§7Lvl §f" + (int) J.num(pet, "level") + " " + color + Fmt.titleCase(rarity) + (J.bool(pet, "active") ? " §a●" : ""), x + 23, y + 14);
		g.fill(x + 3, y + 25, x + DETAIL_W - 3, y + 26, Ui.BOX_EDGE);

		// Lore, word-wrapped and scaled down so more of it fits.
		float scale = 0.75f;
		int wrapWidth = (int) ((DETAIL_W - 8) / scale);
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (String l : J.strings(pet, "lore")) {
			if (l.isEmpty()) {
				lines.add(FormattedCharSequence.EMPTY);
				continue;
			}
			lines.addAll(Ui.font().split(Component.literal(l), wrapWidth));
		}
		g.pose().pushMatrix();
		g.pose().translate(x + 4, y + 29);
		g.pose().scale(scale, scale);
		int ly = 0;
		int maxY = (int) ((y + 160 - (y + 29)) / scale);
		for (FormattedCharSequence line : lines) {
			if (ly + 9 > maxY) break;
			g.text(Ui.font(), line, 0, ly, Ui.WHITE, true);
			ly += 10;
		}
		g.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, int left, int top) {
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		int cols = (GRID_W - 8) / CELL;
		int startX = x + (GRID_W - cols * CELL) / 2;
		int startY = y + 17;
		if (mx >= startX && mx < startX + cols * CELL && my >= startY && my < y + CONTENT_H) {
			int c = (int) ((mx - startX) / CELL);
			int r = (int) ((my - startY) / CELL) + scroll;
			int index = r * cols + c;
			if (index < pets(profile()).size()) selected = index;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount, int left, int top) {
		if (Ui.inside(mx, my, left + CONTENT_X, top + CONTENT_Y, GRID_W, CONTENT_H)) {
			scroll = Math.max(0, scroll - (int) Math.signum(amount));
			return true;
		}
		return false;
	}

	private static int rarityRank(String r) {
		return switch (r.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0;
			case "UNCOMMON" -> 1;
			case "RARE" -> 2;
			case "EPIC" -> 3;
			case "LEGENDARY" -> 4;
			case "MYTHIC" -> 5;
			default -> -1;
		};
	}

	private static int dim(int rgb) {
		int r = ((rgb >> 16) & 0xFF) * 3 / 4;
		int gg = ((rgb >> 8) & 0xFF) * 3 / 4;
		int b = (rgb & 0xFF) * 3 / 4;
		return (r << 16) | (gg << 8) | b;
	}
}
