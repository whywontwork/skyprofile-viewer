package dev.skyprofile.ui.pages;

import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.ProfileViewerScreen;
import dev.skyprofile.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;

/**
 * One tab of the viewer. Coordinates passed in are the panel's top-left corner; pages draw inside
 * the content area (8, 8) .. (412, 172) of the 420x202 panel.
 */
public abstract class Page {
	public static final int CONTENT_X = 8;
	public static final int CONTENT_Y = 8;
	public static final int CONTENT_W = 404;
	public static final int CONTENT_H = 164;

	protected final ProfileViewerScreen screen;
	public final String name;
	public final Item icon;

	protected Page(ProfileViewerScreen screen, String name, Item icon) {
		this.screen = screen;
		this.name = name;
		this.icon = icon;
	}

	protected PlayerProfile profile() {
		return screen.profile();
	}

	/** Add page-specific widgets (called from the screen's init). */
	public void addWidgets(int left, int top) {}

	public void onShow() {}

	public abstract void render(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY, float delta);

	public boolean mouseClicked(double mx, double my, int button, int left, int top) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double amount, int left, int top) {
		return false;
	}

	/** Shared "still loading / failed" placeholder for sections that come from a separate request. */
	protected boolean placeholder(GuiGraphicsExtractor g, PlayerProfile.Part<?> part, int cx, int cy) {
		if (part.data != null) return false;
		if (part.error != null) {
			Ui.textCenter(g, "§cCouldn't load this section", cx, cy - 6);
			Ui.textCenter(g, "§7" + Ui.fit(ProfileViewerScreen.describe(part.error, profile()), 380), cx, cy + 6);
		} else {
			Ui.textCenter(g, "§7Loading" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4)), cx, cy);
		}
		return true;
	}
}
