package dev.skyprofile.ui.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.Icons;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Armor/equipment, wardrobe and every inventory SkyCrypt exposes (inventory, ender chest, backpacks, bags...). */
public class InventoryPage extends Page {
	private static final int LIST_W = 96;
	private static final int GRID_X = 108;
	private static final int GRID_W = 304;
	private static final int COLS = 9;
	private static final int ROW_H = 12;

	private record Tab(String name, String icon, List<JsonObject> items) {}

	private int selected;
	private int listScroll;
	private int rowScroll;
	private final Deque<Tab> opened = new ArrayDeque<>();

	public InventoryPage(ProfileViewerScreen screen) {
		super(screen, "Inventories", Items.ENDER_CHEST);
	}

	@Override
	public void onShow() {
		profile().requestInventory();
	}

	private List<Tab> tabs(PlayerProfile p) {
		List<Tab> tabs = new ArrayList<>();
		JsonObject gear = J.obj(p.combined.data, "gear");
		if (gear != null) {
			List<JsonObject> equipped = new ArrayList<>(J.objects(gear, "armor", "armor"));
			while (equipped.size() < 4) equipped.add(null);
			equipped.add(null);
			equipped.addAll(J.objects(gear, "equipment", "equipment"));
			while (equipped.size() < COLS) equipped.add(null);
			List<JsonObject> weapons = J.objects(gear, "weapons", "weapons");
			equipped.addAll(weapons);
			tabs.add(new Tab("Armor & Gear", "", equipped));

			List<JsonElement> sets = new ArrayList<>();
			J.arr(gear, "wardrobe").forEach(sets::add);
			if (!sets.isEmpty()) {
				List<JsonObject> wardrobe = new ArrayList<>();
				for (int page = 0; page * COLS < sets.size(); page++) {
					if (page > 0) for (int i = 0; i < COLS; i++) wardrobe.add(null);
					for (int piece = 0; piece < 4; piece++) {
						for (int col = 0; col < COLS; col++) {
							int index = page * COLS + col;
							JsonObject item = null;
							if (index < sets.size()) {
								JsonElement set = sets.get(index);
								item = J.obj(set, String.valueOf(piece));
							}
							wardrobe.add(item);
						}
					}
				}
				tabs.add(new Tab("Wardrobe", "", wardrobe));
			}
		}
		JsonElement inv = p.inventory.data;
		if (inv != null && inv.isJsonArray()) {
			for (JsonElement e : inv.getAsJsonArray()) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				List<JsonObject> items = new ArrayList<>();
				for (JsonElement it : J.arr(o, "items")) items.add(it != null && it.isJsonObject() ? it.getAsJsonObject() : null);
				tabs.add(new Tab(J.strOr(o, "?", "name"), J.str(o, "texture"), items));
			}
		}
		return tabs;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int left, int top, int mx, int my, float delta) {
		PlayerProfile p = profile();
		p.requestInventory();
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		Ui.box(g, x, y, LIST_W, CONTENT_H);
		Ui.box(g, left + GRID_X, y, GRID_W, CONTENT_H);

		List<Tab> tabs = tabs(p);
		int visible = (CONTENT_H - 4) / ROW_H;
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, tabs.size() + 1 - visible)));
		for (int i = 0; i < visible && i + listScroll < tabs.size(); i++) {
			int index = i + listScroll;
			Tab t = tabs.get(index);
			int ry = y + 2 + i * ROW_H;
			boolean hover = Ui.inside(mx, my, x + 1, ry, LIST_W - 2, ROW_H);
			if (index == selected && opened.isEmpty()) g.fill(x + 1, ry, x + LIST_W - 1, ry + ROW_H, Ui.ROW_SELECTED);
			else if (hover) g.fill(x + 1, ry, x + LIST_W - 1, ry + ROW_H, Ui.ROW_HOVER);
			int tx = x + 4;
			if (!t.icon.isEmpty() && Icons.draw(g, t.icon, x + 3, ry + 1, 10)) tx = x + 16;
			Ui.text(g, Ui.fit((index == selected ? "§f" : "§7") + t.name, LIST_W - (tx - x) - 3), tx, ry + 2);
		}
		int after = tabs.size() - listScroll;
		if (p.inventory.loading() && after < visible) {
			Ui.text(g, "§8Loading" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4)), x + 4, y + 4 + after * ROW_H);
		} else if (p.inventory.error != null && after < visible) {
			Ui.text(g, "§cInventories failed", x + 4, y + 4 + after * ROW_H);
		}

		Tab current = !opened.isEmpty() ? opened.peek() : (selected < tabs.size() ? tabs.get(selected) : null);
		int gx = left + GRID_X;
		if (current == null) {
			if (!placeholder(g, p.combined, gx + GRID_W / 2, y + CONTENT_H / 2)) {
				Ui.textCenter(g, "§7Nothing to show.", gx + GRID_W / 2, y + CONTENT_H / 2);
			}
			return;
		}

		String header = (opened.isEmpty() ? "§f" : "§e◀ Back §8| §f") + current.name;
		Ui.text(g, Ui.fit(header, GRID_W - 10), gx + 5, y + 5);
		if (current.items.isEmpty()) {
			Ui.textCenter(g, "§7Empty or hidden by the player's API settings.", gx + GRID_W / 2, y + CONTENT_H / 2);
			return;
		}
		int rows = (current.items.size() + COLS - 1) / COLS;
		int visibleRows = (CONTENT_H - 20) / ItemGrid.SLOT;
		rowScroll = Math.max(0, Math.min(rowScroll, rows - visibleRows));
		int startX = gx + (GRID_W - COLS * ItemGrid.SLOT) / 2;
		int startY = y + 17;
		JsonObject hovered = null;
		for (int r = 0; r < visibleRows && r + rowScroll < rows; r++) {
			for (int c = 0; c < COLS; c++) {
				int index = (r + rowScroll) * COLS + c;
				if (index >= current.items.size()) break;
				JsonObject item = current.items.get(index);
				if (ItemGrid.slot(g, item, startX + c * ItemGrid.SLOT, startY + r * ItemGrid.SLOT, mx, my) && !ItemGrid.isEmpty(item)) {
					hovered = item;
				}
			}
		}
		if (rows > visibleRows) {
			Ui.textRight(g, "§8" + (rowScroll + 1) + "-" + Math.min(rows, rowScroll + visibleRows) + "/" + rows + " rows", gx + GRID_W - 5, y + 5);
		}
		if (hovered != null) Ui.tooltip(g, ItemGrid.tooltip(hovered), mx, my);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button, int left, int top) {
		int x = left + CONTENT_X;
		int y = top + CONTENT_Y;
		List<Tab> tabs = tabs(profile());
		if (Ui.inside(mx, my, x, y, LIST_W, CONTENT_H)) {
			int index = (int) ((my - y - 2) / ROW_H) + listScroll;
			if (index >= 0 && index < tabs.size()) {
				selected = index;
				rowScroll = 0;
				opened.clear();
			}
			return true;
		}
		int gx = left + GRID_X;
		if (!opened.isEmpty() && Ui.inside(mx, my, gx, y, 60, 15)) {
			opened.pop();
			rowScroll = 0;
			return true;
		}
		Tab current = !opened.isEmpty() ? opened.peek() : (selected < tabs.size() ? tabs.get(selected) : null);
		if (current == null) return false;
		int startX = gx + (GRID_W - COLS * ItemGrid.SLOT) / 2;
		int startY = y + 17;
		if (mx >= startX && mx < startX + COLS * ItemGrid.SLOT && my >= startY) {
			int c = (int) ((mx - startX) / ItemGrid.SLOT);
			int r = (int) ((my - startY) / ItemGrid.SLOT) + rowScroll;
			int index = r * COLS + c;
			if (index >= 0 && index < current.items.size()) {
				JsonObject item = current.items.get(index);
				if (item != null && !J.arr(item, "containsItems").isEmpty()) {
					List<JsonObject> inner = new ArrayList<>();
					for (JsonElement e : J.arr(item, "containsItems")) inner.add(e != null && e.isJsonObject() ? e.getAsJsonObject() : null);
					opened.push(new Tab(dev.skyprofile.ui.Fmt.stripCodes(J.strOr(item, "Container", "display_name")), "", inner));
					rowScroll = 0;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount, int left, int top) {
		if (Ui.inside(mx, my, left + CONTENT_X, top + CONTENT_Y, LIST_W, CONTENT_H)) {
			listScroll = Math.max(0, listScroll - (int) Math.signum(amount));
			return true;
		}
		if (Ui.inside(mx, my, left + GRID_X, top + CONTENT_Y, GRID_W, CONTENT_H)) {
			rowScroll = Math.max(0, rowScroll - (int) Math.signum(amount));
			return true;
		}
		return false;
	}
}
