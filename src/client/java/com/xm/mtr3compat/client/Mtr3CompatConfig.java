package com.xm.mtr3compat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * config/mtr3-compat.json：
 * <pre>
 * {
 *   "mtr3_packs": ["包名（不含 .zip）", ...],
 *   "script_car_offsets": {
 *     "包名（不含 .zip）": { "x": 0, "y": 0, "z": -2.5 }
 *   }
 * }
 * </pre>
 * mtr3_packs：被本 mod 拦截 MTR4/JCM 解析、改以 MTR3 模式加载的资源包。
 * script_car_offsets：按资源包给脚本“车厢空间”绘制加的平移量，x/y/z 任意组合、缺省为 0。
 * 文件被修改后 ≤0.5 秒内自动生效，无需重载资源包或重启游戏。
 */
public final class Mtr3CompatConfig {

	private static final Set<String> MTR3_PACKS = new LinkedHashSet<>();
	/** 包名（已归一化）-> 平移量 [x, y, z]。 */
	private static final Map<String, float[]> SCRIPT_CAR_OFFSETS = new HashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long RELOAD_INTERVAL_NANOS = 500_000_000L;

	private static long lastModified = -1;
	private static long lastCheckNanos = Long.MIN_VALUE;

	private Mtr3CompatConfig() {
	}

	/** 去掉 .zip 后缀（大小写不敏感判断，保留原名）。 */
	public static String normalize(String packName) {
		return packName.toLowerCase(Locale.ENGLISH).endsWith(".zip") ? packName.substring(0, packName.length() - 4) : packName;
	}

	public static boolean isIntercepted(String packName) {
		return MTR3_PACKS.contains(normalize(packName));
	}

	public static boolean hasIntercepted() {
		return !MTR3_PACKS.isEmpty();
	}

	public static Set<String> getPacks() {
		return MTR3_PACKS;
	}

	/** 该资源包的脚本车厢空间平移量 [x, y, z]；未配置或全为 0 时返回 null。 */
	public static float[] scriptCarOffset(String packName) {
		if (packName == null) {
			return null;
		}
		reloadIfModified();
		final float[] offset = SCRIPT_CAR_OFFSETS.get(normalize(packName));
		if (offset == null || (offset[0] == 0 && offset[1] == 0 && offset[2] == 0)) {
			return null;
		}
		return offset;
	}

	public static void toggle(String packName) {
		final String key = normalize(packName);
		if (!MTR3_PACKS.remove(key)) {
			MTR3_PACKS.add(key);
		}
		save();
	}

	/** 配置文件在外部被修改后自动重新读取（最多每 0.5 秒检查一次）。 */
	private static void reloadIfModified() {
		final long now = System.nanoTime();
		if (lastCheckNanos != Long.MIN_VALUE && now - lastCheckNanos < RELOAD_INTERVAL_NANOS) {
			return;
		}
		lastCheckNanos = now;
		try {
			final Path path = file();
			if (!Files.isRegularFile(path)) {
				return;
			}
			final long modified = Files.getLastModifiedTime(path).toMillis();
			if (modified == lastModified) {
				return;
			}
			load();
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 检查配置变更失败", e);
		}
	}

	public static void load() {
		try {
			final Path path = file();
			if (!Files.isRegularFile(path)) {
				return;
			}
			lastModified = Files.getLastModifiedTime(path).toMillis();
			final JsonElement root = JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			final JsonObject object = root.getAsJsonObject();
			if (object.has("mtr3_packs")) {
				MTR3_PACKS.clear();
				for (final JsonElement element : object.getAsJsonArray("mtr3_packs")) {
					MTR3_PACKS.add(normalize(element.getAsString()));
				}
			}
			SCRIPT_CAR_OFFSETS.clear();
			if (object.has("script_car_offsets") && object.get("script_car_offsets").isJsonObject()) {
				for (final Map.Entry<String, JsonElement> entry : object.getAsJsonObject("script_car_offsets").entrySet()) {
					if (!entry.getValue().isJsonObject()) {
						continue;
					}
					final JsonObject offset = entry.getValue().getAsJsonObject();
					SCRIPT_CAR_OFFSETS.put(normalize(entry.getKey()), new float[]{
							number(offset, "x"), number(offset, "y"), number(offset, "z")
					});
				}
			}
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 读取配置失败", e);
		}
	}

	private static float number(JsonObject object, String key) {
		try {
			return object.has(key) ? object.get(key).getAsFloat() : 0;
		} catch (Exception ignored) {
			return 0;
		}
	}

	public static void save() {
		try {
			final Path path = file();
			Files.createDirectories(path.getParent());
			final JsonObject root = new JsonObject();
			final JsonArray packs = new JsonArray();
			MTR3_PACKS.forEach(packs::add);
			root.add("mtr3_packs", packs);
			final JsonObject offsets = new JsonObject();
			SCRIPT_CAR_OFFSETS.forEach((pack, offset) -> {
				final JsonObject object = new JsonObject();
				object.addProperty("x", offset[0]);
				object.addProperty("y", offset[1]);
				object.addProperty("z", offset[2]);
				offsets.add(pack, object);
			});
			root.add("script_car_offsets", offsets);
			Files.write(path, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
			lastModified = Files.getLastModifiedTime(path).toMillis();
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 保存配置失败", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mtr3-compat.json");
	}
}
