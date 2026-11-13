package dev.skyprofile.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared drawing helpers and the viewer's color palette. Colors are ARGB. */
public final class Ui {
	public static final int PANEL_BG = 0xE8121214;
	public static final int PANEL_EDGE_OUTER = 0xFF000000;
	public static final int PANEL_EDGE_INNER = 0xFF7A7A80;
	public static final int BOX_BG = 0xB0242428;
	public static final int BOX_EDGE = 0xFF505058;
	public static final int ROW_HOVER = 0x30FFFFFF;
	public static final int ROW_SELECTED = 0x50FFFFFF;
	public static final int BAR_BG = 0xFF2E2E34;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK_GRAY = 0xFF666666;

	private Ui() {}

	private static final Map<Item, ItemStack> STACKS = new ConcurrentHashMap<>();

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/**
	 * Item stack for an icon. In 26.x item components only exist once a world's registries are loaded,
	 * so outside a world this returns EMPTY (draws nothing) instead of crashing.
	 */
	public static ItemStack stack(Item item) {
		ItemStack s = STACKS.get(item);
		if (s != null) return s;
		try {
			s = new ItemStack(item);
			STACKS.put(item, s);
			return s;
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, BOX_BG);
		g.outline(x, y, w, h, BOX_EDGE);
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_BG);
		g.outline(x - 1, y - 1, w + 2, h + 2, PANEL_EDGE_OUTER);
		g.outline(x, y, w, h, PANEL_EDGE_INNER);
	}

	/** Progress bar; maxed bars are drawn gold. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double frac, int rgb, boolean maxed) {
		g.fill(x, y, x + w, y + h, BAR_BG);
		double f = maxed ? 1 : Math.max(0, Math.min(1, frac));
		int fw = (int) Math.round(w * f);
		if (fw > 0) {
			int color = maxed ? 0xFFFFAA00 : 0xFF000000 | rgb;
			int light = maxed ? 0xFFFFE070 : 0xFF000000 | brighten(rgb);
			g.fillGradient(x, y, x + fw, y + h, light, color);
		}
	}

	public static int brighten(int rgb) {
		int r = Math.min(255, ((rgb >> 16) & 0xFF) + 60);
		int gr = Math.min(255, ((rgb >> 8) & 0xFF) + 60);
		int b = Math.min(255, (rgb & 0xFF) + 60);
		return (r << 16) | (gr << 8) | b;
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y) {
		g.text(font(), s, x, y, WHITE, true);
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(font(), s, x, y, color, true);
	}

	public static void textRight(GuiGraphicsExtractor g, String s, int right, int y) {
		g.text(font(), s, right - font().width(s), y, WHITE, true);
	}

	public static void textCenter(GuiGraphicsExtractor g, String s, int cx, int y) {
		g.text(font(), s, cx - font().width(s) / 2, y, WHITE, true);
	}

	public static void textCenter(GuiGraphicsExtractor g, Component c, int cx, int y) {
		g.text(font(), c, cx - font().width(c) / 2, y, WHITE, true);
	}

	/** Draws text scaled down (e.g. 0.75) at the given position. */
	public static void textScaled(GuiGraphicsExtractor g, String s, float x, float y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font(), s, 0, 0, WHITE, true);
		g.pose().popMatrix();
	}

	/** Cuts a (possibly § formatted) string to fit a pixel width, adding "…". */
	public static String fit(String s, int width) {
		Font f = font();
		if (f.width(s) <= width) return s;
		String ell = "…";
		int ew = f.width(ell);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				sb.append(c).append(s.charAt(++i));
				continue;
			}
			if (f.width(sb.toString() + c) + ew > width) break;
			sb.append(c);
		}
		return sb + ell;
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static List<Component> lines(String... lines) {
		List<Component> out = new ArrayList<>();
		for (String l : lines) if (l != null) out.add(Component.literal(l));
		return out;
	}

	public static List<Component> lines(List<String> lines) {
		List<Component> out = new ArrayList<>();
		for (String l : lines) out.add(Component.literal(l));
		return out;
	}

	public static void tooltip(GuiGraphicsExtractor g, List<Component> lines, int mx, int my) {
		if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font(), lines, mx, my);
	}
}
