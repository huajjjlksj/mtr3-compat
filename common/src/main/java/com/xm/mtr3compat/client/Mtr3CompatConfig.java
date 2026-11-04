package com.xm.mtr3compat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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
 *   },
 *   "script_lr_swap": {
 *     "车型 id 或包名（不含 .zip）": true
 *   }
 * }
 * </pre>
 * mtr3_packs：被本 mod 拦截 MTR4/JCM 解析、改以 MTR3 模式加载的资源包。
 * script_car_offsets：按资源包给脚本“车厢空间”绘制加的平移量，x/y/z 任意组合、缺省为 0。
 * script_lr_swap：按车型 id（或资源包名）让脚本看到的 isReversed 取反、
 * doorLeftOpen/doorRightOpen 互换，用于修正脚本左右约定与车体不一致的资源包。
 * 文件被修改后 ≤0.5 秒内自动生效，无需重载资源包或重启游戏。
 */
public final class Mtr3CompatConfig {

	/** 配置目录（gameDir/config），由 loader 引导类在 {@link Mtr3CompatClient#init()} 前注入。 */
	public static java.nio.file.Path configDir;

	private static final Set<String> MTR3_PACKS = new LinkedHashSet<>();
	/** 包名（已归一化）-> 平移量 [x, y, z]。 */
	private static final Map<String, float[]> SCRIPT_CAR_OFFSETS = new HashMap<>();
	/** 小写的车型 id 或包名（已归一化）-> 是否启用脚本左右互换。 */
	private static final Set<String> SCRIPT_LR_SWAP = new LinkedHashSet<>();
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

	/** 配置键归一化：去掉 .zip 并转小写（车型 id 与包名共用同一套键）。 */
	public static String key(String name) {
		return name == null ? null : normalize(name).toLowerCase(Locale.ENGLISH);
	}

	/** 该车型（按 id）或所属资源包（按包名）的脚本车厢空间平移量 [x, y, z]；未配置或全为 0 时返回 null。 */
	public static float[] scriptCarOffset(String vehicleId, String packName) {
		reloadIfModified();
		float[] offset = vehicleId == null ? null : SCRIPT_CAR_OFFSETS.get(key(vehicleId));
		if (offset == null && packName != null) {
			offset = SCRIPT_CAR_OFFSETS.get(key(packName));
		}
		if (offset == null || (offset[0] == 0 && offset[1] == 0 && offset[2] == 0)) {
			return null;
		}
		return offset;
	}

	public static float[] scriptCarOffset(String packName) {
		return scriptCarOffset(null, packName);
	}

	/** 该车型（按 id）或所属资源包（按包名）是否启用脚本左右互换。 */
	public static boolean scriptLrSwap(String vehicleId, String packName) {
		reloadIfModified();
		if (vehicleId != null && SCRIPT_LR_SWAP.contains(key(vehicleId))) {
			return true;
		}
		return packName != null && SCRIPT_LR_SWAP.contains(key(packName));
	}

	/** 左右互换配置的键集合（副本，键已小写）。 */
	public static Set<String> getScriptLrSwapKeys() {
		reloadIfModified();
		return new LinkedHashSet<>(SCRIPT_LR_SWAP);
	}

	/** 车厢平移配置（副本）。 */
	public static Map<String, float[]> getScriptCarOffsets() {
		reloadIfModified();
		return new HashMap<>(SCRIPT_CAR_OFFSETS);
	}

	/** 用界面草稿替换这些车型的左右互换设置（包名键保留）。 */
	public static void applyVehicleLrSwap(Collection<String> allVehicleIds, Collection<String> enabled) {
		for (final String id : allVehicleIds) {
			SCRIPT_LR_SWAP.remove(key(id));
		}
		for (final String id : enabled) {
			SCRIPT_LR_SWAP.add(key(id));
		}
		save();
	}

	/** 用界面草稿替换这些车型的车厢平移设置（包名键保留）。 */
	public static void applyVehicleOffsets(Collection<String> allVehicleIds, Map<String, float[]> offsets) {
		for (final String id : allVehicleIds) {
			SCRIPT_CAR_OFFSETS.remove(key(id));
		}
		for (final Map.Entry<String, float[]> entry : offsets.entrySet()) {
			SCRIPT_CAR_OFFSETS.put(key(entry.getKey()), entry.getValue());
		}
		save();
	}

	public static void toggle(String packName) {
		final String packKey = normalize(packName);
		if (!MTR3_PACKS.remove(packKey)) {
			MTR3_PACKS.add(packKey);
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
					SCRIPT_CAR_OFFSETS.put(key(entry.getKey()), new float[]{
							number(offset, "x"), number(offset, "y"), number(offset, "z")
					});
				}
			}
			SCRIPT_LR_SWAP.clear();
			if (object.has("script_lr_swap") && object.get("script_lr_swap").isJsonObject()) {
				for (final Map.Entry<String, JsonElement> entry : object.getAsJsonObject("script_lr_swap").entrySet()) {
					try {
						if (entry.getValue().getAsBoolean()) {
							SCRIPT_LR_SWAP.add(key(entry.getKey()));
						}
					} catch (Exception ignored) {
					}
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
			final JsonObject lrSwap = new JsonObject();
			SCRIPT_LR_SWAP.forEach(key -> lrSwap.addProperty(key, true));
			root.add("script_lr_swap", lrSwap);
			Files.write(path, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
			lastModified = Files.getLastModifiedTime(path).toMillis();
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 保存配置失败", e);
		}
	}

	private static Path file() {
		return configDir.resolve("mtr3-compat.json");
	}
}
