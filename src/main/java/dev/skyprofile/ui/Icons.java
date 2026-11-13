package dev.skyprofile.ui;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;
import dev.skyprofile.SkyProfileClient;
import dev.skyprofile.api.SkyCryptApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Item icons for SkyCrypt "texture_path" values.
 * <ul>
 *   <li>{@code /api/head/<hash>} → a real player head item; Minecraft fetches the skin from textures.minecraft.net.</li>
 *   <li>anything else ({@code /api/item/X}, potions, leather) → the rendered image from SkyCrypt (WebP or PNG),
 *       uploaded as a dynamic texture. These public image endpoints don't need (and never get) the API key.</li>
 * </ul>
 * Everything is kept in memory only and loaded lazily when an icon is first drawn.
 */
public final class Icons {
	private static final Object LOADING = new Object();
	private static final Object FAILED = new Object();
	private static final Map<String, Object> IMAGES = new ConcurrentHashMap<>();
	private static final Map<String, ItemStack> HEADS = new ConcurrentHashMap<>();
	private static final AtomicInteger COUNTER = new AtomicInteger();
	private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "SkyProfile-Icons");
		t.setDaemon(true);
		return t;
	});
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.executor(POOL)
			.build();

	private Icons() {}

	/** Draws the icon for a SkyCrypt texture path at 16x16 scaled to {@code size}. Returns false if nothing could be drawn yet. */
	public static boolean draw(GuiGraphicsExtractor g, String texturePath, int x, int y, int size) {
		if (texturePath == null || texturePath.isEmpty()) return false;
		String hash = headHash(texturePath);
		if (hash != null) {
			drawItem(g, head(hash), x, y, size);
			return true;
		}
		Identifier id = image(absolute(texturePath));
		if (id == null) return false;
		g.blit(id, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
		return true;
	}

	public static void drawItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
		if (size == 16) {
			g.item(stack, x, y);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 16f, size / 16f);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	public static String headHash(String texturePath) {
		int i = texturePath.indexOf("/api/head/");
		if (i < 0) return null;
		String hash = texturePath.substring(i + "/api/head/".length());
		int q = hash.indexOf('?');
		if (q >= 0) hash = hash.substring(0, q);
		return hash.matches("[0-9a-fA-F]{20,80}") ? hash : null;
	}

	public static ItemStack head(String hash) {
		ItemStack cached = HEADS.get(hash);
		if (cached != null) return cached;
		try {
			String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
			String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
			UUID id = UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8));
			PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", b64)));
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "", props)));
			HEADS.put(hash, stack);
			return stack;
		} catch (RuntimeException e) {
			// Item components aren't available outside a world (see Ui.stack).
			return ItemStack.EMPTY;
		}
	}

	private static String absolute(String path) {
		if (path.startsWith("http://") || path.startsWith("https://")) return path;
		return SkyCryptApi.BASE + (path.startsWith("/") ? path : "/" + path);
	}

	private static Identifier image(String url) {
		Object state = IMAGES.get(url);
		if (state instanceof Identifier id) return id;
		if (state != null) return null;
		if (!isAllowedHost(url)) {
			IMAGES.put(url, FAILED);
			return null;
		}
		IMAGES.put(url, LOADING);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "SkyProfileViewer/1.0 (Fabric mod)")
				.header("Accept", "image/png,image/webp")
				.GET().build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenAcceptAsync(res -> {
			try {
				if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
				NativeImage image = decode(res.body());
				Minecraft mc = Minecraft.getInstance();
				mc.execute(() -> {
					Identifier id = Identifier.fromNamespaceAndPath("skyprofile", "icon/" + COUNTER.incrementAndGet());
					mc.getTextureManager().register(id, new DynamicTexture(() -> "skyprofile icon " + url, image));
					IMAGES.put(url, id);
				});
			} catch (Exception e) {
				SkyProfileClient.LOGGER.debug("Icon failed: {}", url, e);
				IMAGES.put(url, FAILED);
			}
		}, POOL).exceptionally(t -> {
			IMAGES.put(url, FAILED);
			return null;
		});
		return null;
	}

	private static boolean isAllowedHost(String url) {
		try {
			String host = URI.create(url).getHost();
			return host != null && host.toLowerCase(Locale.ROOT).endsWith("shiiyu.moe");
		} catch (Exception e) {
			return false;
		}
	}

	private static NativeImage decode(byte[] bytes) throws Exception {
		boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
		if (png) return NativeImage.read(bytes);

		ImageReader reader = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance(null);
		BufferedImage img;
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			reader.setInput(in);
			img = reader.read(0);
		} finally {
			reader.dispose();
		}
		NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				out.setPixel(x, y, img.getRGB(x, y));
			}
		}
		return out;
	}
}
