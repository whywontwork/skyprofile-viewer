package dev.skyprofile.ui;

import dev.skyprofile.api.ApiException;
import dev.skyprofile.api.SkyCryptApi;
import dev.skyprofile.config.SkyProfileConfig;
import dev.skyprofile.data.PlayerProfile;
import dev.skyprofile.ui.pages.BasicPage;
import dev.skyprofile.ui.pages.CollectionsPage;
import dev.skyprofile.ui.pages.DungeonsPage;
import dev.skyprofile.ui.pages.ExtraPage;
import dev.skyprofile.ui.pages.InventoryPage;
import dev.skyprofile.ui.pages.Page;
import dev.skyprofile.ui.pages.PetsPage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.List;

/** NEU-style profile viewer: tabs across the top, a 420x202 panel, and a shared bottom bar. */
public class ProfileViewerScreen extends Screen {
	public static final int W = 420;
	public static final int H = 202;
	private static final int TAB_W = 28;
	private static final int TAB_H = 25;
	private static final int TAB_GAP = 2;
	private static final String KEY_URL = SkyCryptApi.WEBSITE + "/dashboard/developer";

	private enum View { KEY_PROMPT, LOADING, ERROR, LOADED }

	private final @Nullable Screen parent;
	private PlayerProfile profile;
	private final List<Page> pages;
	private int tab;
	private View view;
	private boolean forceKeyPrompt;

	private EditBox searchBox;
	private String searchText = "";
	private EditBox keyBox;
	private int left;
	private int top;

	public ProfileViewerScreen(PlayerProfile profile, @Nullable Screen parent) {
		super(Component.literal("SkyBlock Profile Viewer"));
		this.parent = parent;
		this.profile = profile;
		this.pages = List.of(
				new BasicPage(this),
				new DungeonsPage(this),
				new ExtraPage(this),
				new InventoryPage(this),
				new CollectionsPage(this),
				new PetsPage(this));
		this.view = currentView();
		if (view != View.KEY_PROMPT && profile.stats.data == null && profile.stats.error == null) profile.load();
	}

	/** Opens the key prompt even when a key is already saved (from /skyprofile key or the ⚙ button). */
	public static ProfileViewerScreen keyPrompt(PlayerProfile profile, @Nullable Screen parent) {
		ProfileViewerScreen s = new ProfileViewerScreen(profile, parent);
		s.forceKeyPrompt = true;
		s.view = View.KEY_PROMPT;
		return s;
	}

	public PlayerProfile profile() {
		return profile;
	}

	public int panelLeft() {
		return left;
	}

	public int panelTop() {
		return top;
	}

	private View currentView() {
		boolean mock = System.getProperty("skyprofile.mockDir") != null;
		if (forceKeyPrompt || (!mock && !SkyProfileConfig.get().hasApiKey())) return View.KEY_PROMPT;
		if (profile.stats.data != null) return View.LOADED;
		if (profile.stats.error != null) return View.ERROR;
		return View.LOADING;
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = Math.max(TAB_H + 4, (height - H) / 2 + TAB_H / 2);
		view = currentView();
		switch (view) {
			case KEY_PROMPT -> initKeyPrompt();
			case ERROR -> initError();
			case LOADED -> initLoaded();
			case LOADING -> addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
					.bounds(left + W / 2 - 40, top + H / 2 + 20, 80, 20).build());
		}
	}

	private void initKeyPrompt() {
		int cx = left + W / 2;
		keyBox = new EditBox(font, cx - 150, top + 104, 300, 18, Component.literal("SkyCrypt API key"));
		keyBox.setMaxLength(200);
		keyBox.setHint(Component.literal("skycrypt_...").withStyle(s -> s.withColor(0x777777)));
		keyBox.setValue(SkyProfileConfig.get().apiKey);
		addRenderableWidget(keyBox);
		setInitialFocus(keyBox);

		addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveKey())
				.bounds(cx - 150, top + 130, 96, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Get a key ↗"), ConfirmLinkScreen.confirmLink(this, KEY_URL))
				.bounds(cx - 48, top + 130, 96, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
			if (forceKeyPrompt && SkyProfileConfig.get().hasApiKey()) {
				forceKeyPrompt = false;
				rebuildWidgets();
			} else {
				onClose();
			}
		}).bounds(cx + 54, top + 130, 96, 20).build());
	}

	private void saveKey() {
		String key = keyBox.getValue().trim();
		SkyProfileConfig.get().apiKey = key;
		SkyProfileConfig.save();
		forceKeyPrompt = false;
		if (key.isEmpty()) {
			rebuildWidgets();
			return;
		}
		reload(new PlayerProfile(profile.uuid != null ? profile.uuid : profile.query, profile.profileId));
	}

	private void initError() {
		int cx = left + W / 2;
		int y = top + H / 2 + 14;
		Throwable err = profile.stats.error;
		String code = err instanceof ApiException api ? api.code : "";
		addRenderableWidget(Button.builder(Component.literal("Retry"), b -> reload(new PlayerProfile(profile.query, profile.profileId)))
				.bounds(cx - 154, y, 100, 20).build());
		if (code.contains("API_KEY") || code.contains("API_TOKEN")) {
			addRenderableWidget(Button.builder(Component.literal("Set API key"), b -> {
				forceKeyPrompt = true;
				rebuildWidgets();
			}).bounds(cx - 50, y, 100, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal("Open on SkyCrypt ↗"), ConfirmLinkScreen.confirmLink(this, websiteUrl()))
					.bounds(cx - 50, y, 100, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(cx + 54, y, 100, 20).build());
	}

	private void initLoaded() {
		searchBox = new EditBox(font, left + 8, top + 179, 106, 16, Component.literal("Search player"));
		searchBox.setMaxLength(16);
		searchBox.setHint(Component.literal("Search player...").withStyle(s -> s.withColor(0x777777)));
		searchBox.setValue(searchText);
		searchBox.setResponder(s -> searchText = s);
		addRenderableWidget(searchBox);

		List<PlayerProfile.ProfileRef> profiles = profile.profiles();
		int index = 0;
		for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(profile.profileId)) index = i;
		final int current = index;
		String label = profileLabel(profiles.isEmpty() ? null : profiles.get(current)) + (profiles.size() > 1 ? " §7(" + (current + 1) + "/" + profiles.size() + ")" : "");
		Button profileButton = Button.builder(Component.literal(label), b -> {
			if (profiles.size() < 2) return;
			PlayerProfile.ProfileRef next = profiles.get((current + 1) % profiles.size());
			PlayerProfile p = new PlayerProfile(profile.uuid, next.id());
			reload(p);
		}).bounds(left + 226, top + 177, 91, 20).build();
		StringBuilder tip = new StringBuilder("Click to switch profile");
		for (PlayerProfile.ProfileRef p : profiles) {
			tip.append('\n').append(p.id().equals(profile.profileId) ? "§a▶ " : "§7  ").append(p.name()).append(modeIcon(p.gameMode()));
		}
		profileButton.setTooltip(Tooltip.create(Component.literal(tip.toString())));
		addRenderableWidget(profileButton);

		addRenderableWidget(Button.builder(Component.literal("Open in SkyCrypt"), ConfirmLinkScreen.confirmLink(this, websiteUrl()))
				.bounds(left + 320, top + 177, 92, 20).build());

		Button key = Button.builder(Component.literal("⚙"), b -> {
			forceKeyPrompt = true;
			rebuildWidgets();
		}).bounds(left + W - 16, top - 18, 16, 16).build();
		key.setTooltip(Tooltip.create(Component.literal("Change SkyCrypt API key")));
		addRenderableWidget(key);

		pages.get(tab).addWidgets(left, top);
	}

	/** Lets pages register their own widgets. */
	public <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addPageWidget(T widget) {
		return addRenderableWidget(widget);
	}

	private String profileLabel(PlayerProfile.@Nullable ProfileRef p) {
		if (p == null) return "§7No profile";
		return "§a" + p.name() + modeIcon(p.gameMode());
	}

	public static String modeIcon(String mode) {
		if (mode == null) return "";
		return switch (mode) {
			case "ironman" -> " §7♲";
			case "island" -> " §a☀";
			case "bingo" -> " §bⒷ";
			default -> "";
		};
	}

	public String websiteUrl() {
		String name = profile.username != null ? profile.username : profile.query;
		String url = SkyCryptApi.WEBSITE + "/stats/" + SkyCryptApi.enc(name);
		if (profile.stats.data != null) url += "/" + SkyCryptApi.enc(profile.cuteName());
		return url;
	}

	public void reload(PlayerProfile next) {
		this.profile = next;
		next.load();
		rebuildWidgets();
	}

	public void selectTab(int index) {
		if (index == tab || index < 0 || index >= pages.size()) return;
		tab = index;
		pages.get(tab).onShow();
		rebuildWidgets();
	}

	public int currentTab() {
		return tab;
	}

	@Override
	public void tick() {
		if (currentView() != view) rebuildWidgets();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (minecraft.level != null) extractTransparentBackground(g);
		else super.extractBackground(g, mouseX, mouseY, a);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (view == View.LOADED) drawTabs(g, mouseX, mouseY);
		Ui.panel(g, left, top, W, H);
		if (view == View.LOADED) {
			// Make the selected tab flow into the panel.
			int tx = left + tab * (TAB_W + TAB_GAP);
			g.fill(tx + 1, top - 1, tx + TAB_W - 1, top + 1, 0xFF141416);
		}

		switch (view) {
			case KEY_PROMPT -> drawKeyPrompt(g);
			case LOADING -> {
				Ui.textCenter(g, "§7Loading §f" + profile.query + "§7's profile" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4)), left + W / 2, top + H / 2 - 10);
			}
			case ERROR -> drawError(g);
			case LOADED -> {
				pages.get(tab).render(g, left, top, mouseX, mouseY, delta);
				drawCredit(g, mouseX, mouseY);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (int i = 0; i < pages.size(); i++) {
			int x = left + i * (TAB_W + TAB_GAP);
			int y = top - TAB_H;
			boolean selected = i == tab;
			boolean hover = Ui.inside(mouseX, mouseY, x, y, TAB_W, TAB_H);
			int h = TAB_H + (selected ? 1 : -2);
			int yy = selected ? y : y + 2;
			g.fill(x, yy, x + TAB_W, yy + h, selected ? Ui.PANEL_BG : (hover ? 0xE0303036 : 0xE01C1C20));
			g.fill(x, yy, x + TAB_W, yy + 1, selected ? Ui.PANEL_EDGE_INNER : Ui.BOX_EDGE);
			g.fill(x, yy, x + 1, yy + h, selected ? Ui.PANEL_EDGE_INNER : Ui.BOX_EDGE);
			g.fill(x + TAB_W - 1, yy, x + TAB_W, yy + h, selected ? Ui.PANEL_EDGE_INNER : Ui.BOX_EDGE);
			g.item(Ui.stack(pages.get(i).icon), x + 6, yy + 5);
			if (hover) g.setTooltipForNextFrame(font, Component.literal(pages.get(i).name), mouseX, mouseY);
		}
	}

	private void drawCredit(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = left + 118;
		int w = 104;
		String s = "§8Data from §7SkyCrypt";
		Ui.textCenter(g, s, x + w / 2, top + 183);
		if (Ui.inside(mouseX, mouseY, x, top + 178, w, 16)) {
			Ui.tooltip(g, Ui.lines(
					"§7Data from §fSkyCrypt §8(sky.shiiyu.moe)",
					"§7Only players someone looked up on",
					"§7SkyCrypt recently are available.",
					"§8Not affiliated with or endorsed by",
					"§8Hypixel or SkyCrypt."), mouseX, mouseY);
		}
	}

	private void drawKeyPrompt(GuiGraphicsExtractor g) {
		int cx = left + W / 2;
		Ui.textCenter(g, "§6§lSkyCrypt API key", cx, top + 16);
		String[] lines = {
				"§7This viewer gets its data from the SkyCrypt API, which needs",
				"§7a personal API key. Log in to §fsky.shiiyu.moe§7, open the",
				"§7developer page of your dashboard and request a key, then paste",
				"§7it below. It's saved in §fconfig/skyprofile.json§7 and only sent",
				"§7to SkyCrypt. Keep it private and don't share it.",
		};
		for (int i = 0; i < lines.length; i++) Ui.textCenter(g, lines[i], cx, top + 34 + i * 11);
		Ui.text(g, "§7API key:", cx - 150, top + 94);
		Ui.textCenter(g, "§8Not affiliated with or endorsed by Hypixel or SkyCrypt.", cx, top + H - 16);
	}

	private void drawError(GuiGraphicsExtractor g) {
		int cx = left + W / 2;
		Ui.textCenter(g, "§c§lCouldn't load " + profile.query, cx, top + 40);
		String msg = describe(profile.stats.error, profile);
		List<FormattedCharSequence> wrapped = font.split(Component.literal("§7" + msg), W - 60);
		int y = top + 60;
		for (FormattedCharSequence line : wrapped) {
			g.centeredText(font, line, cx, y, Ui.WHITE);
			y += 11;
		}
	}

	/** Human-readable explanation for an API failure. */
	public static String describe(Throwable t, PlayerProfile profile) {
		String name = profile.username != null ? profile.username : profile.query;
		if (t instanceof ApiException e) {
			return switch (e.code) {
				case "MISSING_API_KEY" -> "No SkyCrypt API key is set yet.";
				case "INVALID_API_KEY", "INVALID_API_TOKEN" -> "SkyCrypt rejected your API key. Check it on your SkyCrypt dashboard and set it again.";
				case "NOT_CACHED" -> "SkyCrypt doesn't have " + name + " cached yet. Its API only serves players someone looked up on the website recently. Open their page on SkyCrypt once, wait a moment, then press Retry.";
				case "PLAYER_NOT_FOUND" -> "There's no Minecraft player called " + name + ".";
				case "PROFILE_NOT_FOUND" -> name + " doesn't have that SkyBlock profile (or any profiles).";
				case "RATE_LIMITED" -> "SkyCrypt's rate limit for your key is used up. Try again in " + Math.max(1, e.retryAfterSeconds) + "s.";
				case "UPSTREAM_ERROR", "UPSTREAM_TIMEOUT" -> "SkyCrypt couldn't reach Hypixel or Mojang right now. Try again in a bit.";
				default -> e.getMessage() + (e.code.isEmpty() ? "" : " (" + e.code + ")");
			};
		}
		if (t instanceof ConnectException || t instanceof HttpTimeoutException) return "Couldn't reach SkyCrypt. Check your connection and try again.";
		return t == null ? "Unknown error." : t.getClass().getSimpleName() + ": " + t.getMessage();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (view == View.LOADED) {
			for (int i = 0; i < pages.size(); i++) {
				int x = left + i * (TAB_W + TAB_GAP);
				if (Ui.inside(mx, my, x, top - TAB_H, TAB_W, TAB_H)) {
					selectTab(i);
					return true;
				}
			}
			if (Ui.inside(mx, my, left + 118, top + 178, 104, 16)) {
				ConfirmLinkScreen.confirmLinkNow(this, SkyCryptApi.WEBSITE);
				return true;
			}
		}
		if (super.mouseClicked(event, doubleClick)) return true;
		return view == View.LOADED && pages.get(tab).mouseClicked(mx, my, event.button(), left, top);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (view == View.LOADED && pages.get(tab).mouseScrolled(x, y, scrollY, left, top)) return true;
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			if (view == View.LOADED && searchBox != null && searchBox.isFocused()) {
				String name = searchBox.getValue().trim();
				if (name.matches("[A-Za-z0-9_]{1,16}")) {
					searchText = "";
					tab = 0;
					reload(new PlayerProfile(name, null));
				}
				return true;
			}
			if (view == View.KEY_PROMPT && keyBox != null && keyBox.isFocused()) {
				saveKey();
				return true;
			}
		}
		if (view == View.LOADED && searchBox != null && !searchBox.isFocused() && !event.isEscape()) {
			// Number keys 1-6 switch tabs, like hotbar slots.
			int k = event.key();
			if (k >= 49 && k <= 54) {
				selectTab(k - 49);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
