package dev.skyprofile.ui.pages;

import com.google.gson.JsonObject;
import dev.skyprofile.data.J;
import dev.skyprofile.ui.Fmt;
import dev.skyprofile.ui.Icons;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Draws SkyCrypt "StrippedItem" objects as inventory slots with rarity borders and full tooltips. */
public final class ItemGrid {
	public static final int SLOT = 18;

	private ItemGrid() {}

	public static boolean isEmpty(JsonObject item) {
		return item == null || (J.str(item, "texture_path").isEmpty() && J.str(item, "display_name").isEmpty());
	}

	/** Draws one slot. Returns true if the mouse is over it. */
	public static boolean slot(GuiGraphicsExtractor g, JsonObject item, int x, int y, int mx, int my) {
		boolean hover = Ui.inside(mx, my, x, y, SLOT, SLOT);
		g.fill(x, y, x + SLOT, y + SLOT, 0xFF1A1A1E);
		int edge = 0xFF3A3A40;
		if (!isEmpty(item)) {
			String rarity = J.str(item, "rarity");
			if (!rarity.isEmpty()) edge = 0xFF000000 | darken(Fmt.rarityRgb(rarity));
		}
		g.outline(x, y, SLOT, SLOT, edge);
		if (!isEmpty(item)) {
			if (!Icons.draw(g, J.str(item, "texture_path"), x + 1, y + 1, 16)) {
				// Icon still loading: show the first letter of the name.
				String n = Fmt.stripCodes(J.strOr(item, "?", "display_name")).trim();
				Ui.textCenter(g, "§7" + (n.isEmpty() ? "?" : n.substring(0, 1)), x + SLOT / 2, y + 5);
			}
			int count = (int) J.num(item, 1, "Count");
			if (count > 1) {
				String c = String.valueOf(count);
				Ui.text(g, c, x + SLOT - 1 - Ui.font().width(c), y + SLOT - 8);
			}
			if (J.bool(item, "isInactive")) g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x80000000);
		}
		if (hover) g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x40FFFFFF);
		return hover;
	}

	public static List<Component> tooltip(JsonObject item) {
		List<Component> out = new ArrayList<>();
		String name = J.strOr(item, "", "display_name");
		if (name.isEmpty()) name = "§f?";
		else if (!name.startsWith("§")) name = Fmt.rarityColor(J.str(item, "rarity")) + name;
		out.add(Component.literal(name));
		for (String line : J.strings(item, "lore")) out.add(Component.literal(line.isEmpty() ? " " : line));
		if (!J.arr(item, "containsItems").isEmpty()) {
			out.add(Component.literal(""));
			out.add(Component.literal("§eClick to view contents"));
		}
		return out;
	}

	private static int darken(int rgb) {
		int r = ((rgb >> 16) & 0xFF) * 3 / 4;
		int g = ((rgb >> 8) & 0xFF) * 3 / 4;
		int b = (rgb & 0xFF) * 3 / 4;
		return (r << 16) | (g << 8) | b;
	}
}
