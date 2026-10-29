package com.xm.mtr3compat.client;

import org.apache.commons.io.IOUtils;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.resource.ResourceProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * NTE（mtrsteamloco）轨道加载：NTE 扫描 {@code assets/mtrsteamloco/rails/*.json} 注册自己的轨道渲染，
 * MTR4 只读 {@code mtr_custom_resources.json} 的 rails 数组，因此这些轨道在 MTR4 里完全不存在。
 * 这里在资源重载时把同样的定义注册成 MTR4 的 {@link RailResource}，字段对应关系：
 * id→id、name→name、model→modelResource、textureId→textureResource、flipV→flipTextureV、
 * repeatInterval→repeatInterval（缺省 0.5，与 NTE 一致）、yOffset→modelYOffset。
 * NTE 额外支持 Sowcer CSV 模型，MTR4 只认 obj/mqo/mqoz：CSV 在注册时转换成 OBJ/MTL 文本，
 * 由本类的 ResourceProvider 以同目录同名 .obj/.mtl 的形式提供给 MTR4。
 */
public final class NativeRailLoader {

	private static final String RAILS_PREFIX = "assets/mtrsteamloco/rails/";
	private static final String DEFAULT_COLOR = "777777";

	/** 虚拟模型标识符（小写）→ 文本内容，供 MTR4 解析 CSV 转换结果。 */
	private static final Map<String, String> VIRTUAL = new HashMap<>();

	private static final class RailEntry {
		private final String id;
		private final JsonObject json;
		private final String modelResource;

		private RailEntry(String id, JsonObject json, String modelResource) {
			this.id = id;
			this.json = json;
			this.modelResource = modelResource;
		}
	}

	private static final java.util.List<RailEntry> ENTRIES = new java.util.ArrayList<>();

	/** 先查 CSV 转换出的虚拟文件，再落到资源包真实文件。 */
	private static final ResourceProvider RESOURCE_PROVIDER = new ResourceProvider() {
		@Override
		public String get(Identifier identifier) {
			final String virtual = VIRTUAL.get(identifier.data.toString());
			return virtual == null ? ResourceManagerHelper.readResource(identifier) : virtual;
		}

		@Override
		public byte[] getBytes(Identifier identifier) {
			final String virtual = VIRTUAL.get(identifier.data.toString());
			if (virtual != null) {
				return virtual.getBytes(StandardCharsets.UTF_8);
			}
			final byte[][] bytes = new byte[1][];
			ResourceManagerHelper.readResource(identifier, inputStream -> {
				try {
					bytes[0] = IOUtils.toByteArray(inputStream);
				} catch (Exception e) {
					Mtr3CompatClient.LOGGER.warn("[mtr3compat] 读取资源失败: " + identifier, e);
				}
			});
			return bytes[0] == null ? new byte[0] : bytes[0];
		}
	};

	private NativeRailLoader() {
	}

	/** CustomResourceLoader.reload HEAD：收集勾选（MTR3 模式）资源包内的 NTE 轨道定义。 */
	public static void scan() {
		ENTRIES.clear();
		VIRTUAL.clear();
		final Set<String> loadedPacks = Mtr3CompatPacks.loadedPackNames();
		for (final Path packPath : Mtr3CompatPacks.mtrPacks()) {
			if (!Mtr3CompatConfig.isIntercepted(packPath.getFileName().toString()) || !loadedPacks.contains(Mtr3CompatPacks.packBaseName(packPath))) {
				continue;
			}
			for (final String jsonPath : Mtr3CompatPacks.listPackFiles(packPath, RAILS_PREFIX, ".json")) {
				final byte[] bytes = Mtr3CompatPacks.readPackFile(packPath, jsonPath);
				if (bytes == null) {
					continue;
				}
				addFile(packPath, jsonPath, bytes);
			}
		}
	}

	private static void addFile(Path packPath, String jsonPath, byte[] bytes) {
		try {
			final JsonObject root = Utilities.parseJson(new String(bytes, StandardCharsets.UTF_8));
			if (root == null) {
				return;
			}
			if (root.has("model")) {
				// 单模型文件：id 取文件名，与 NTE 一致
				final String fileName = jsonPath.substring(jsonPath.lastIndexOf('/') + 1, jsonPath.length() - ".json".length());
				addEntry(packPath, fileName.toLowerCase(Locale.ENGLISH), root);
			} else {
				for (final Map.Entry<String, JsonElement> entry : root.entrySet()) {
					if (entry.getValue().isJsonObject()) {
						addEntry(packPath, entry.getKey().toLowerCase(Locale.ENGLISH), entry.getValue().getAsJsonObject());
					}
				}
			}
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 解析 NTE 轨道定义失败: " + jsonPath, e);
		}
	}

	private static void addEntry(Path packPath, String id, JsonObject railObject) {
		if (!railObject.has("model")) {
			return;
		}
		final String model = formatModelIdentifier(railObject.get("model").getAsString());
		String modelResource = model;
		if (model.toLowerCase(Locale.ENGLISH).endsWith(".csv")) {
			final byte[] csvBytes = Mtr3CompatPacks.readPackFile(packPath, toPackPath(model));
			if (csvBytes == null) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] NTE 轨道 CSV 缺失，已跳过: {} -> {}", id, model);
				return;
			}
			final String fileName = model.substring(model.lastIndexOf('/') + 1, model.length() - ".csv".length());
			final CsvModelConverter.Result converted = CsvModelConverter.convert(new String(csvBytes, StandardCharsets.UTF_8), fileName);
			modelResource = model.substring(0, model.length() - ".csv".length()) + ".obj";
			VIRTUAL.put(modelResource.toLowerCase(Locale.ENGLISH), converted.objContent);
			VIRTUAL.put(modelResource.substring(0, modelResource.length() - ".obj".length()).toLowerCase(Locale.ENGLISH) + ".mtl", converted.mtlContent);
		} else if (Mtr3CompatPacks.readPackFile(packPath, toPackPath(model)) == null) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] NTE 轨道模型缺失，已跳过: {} -> {}", id, model);
			return;
		}
		ENTRIES.add(new RailEntry(id, railObject, modelResource));
	}

	/** CustomResourceLoader.reload TAIL：注册为 MTR4 轨道（id 已存在时跳过，不覆盖原版/MTR 包轨道）。 */
	public static void register(BiConsumer<String, RailResource> adder, java.util.function.Predicate<String> idExists) {
		final int[] count = {0};
		for (final RailEntry entry : ENTRIES) {
			if (idExists.test(entry.id)) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] NTE 轨道 id 与已有轨道重复，已跳过: {}", entry.id);
				continue;
			}
			try {
				adder.accept(entry.id, new RailResource(new JsonReader(buildRailObject(entry)), RESOURCE_PROVIDER));
				count[0]++;
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] NTE 轨道注册失败: " + entry.id, e);
			}
		}
		if (count[0] > 0) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] 注册 NTE 轨道 {} 条（其中 CSV 转换 {} 条）", count[0], VIRTUAL.size() / 2);
		}
	}

	private static JsonObject buildRailObject(RailEntry entry) {
		final JsonObject json = new JsonObject();
		json.addProperty("id", entry.id);
		json.addProperty("name", entry.json.has("name") ? entry.json.get("name").getAsString() : entry.id);
		json.addProperty("color", DEFAULT_COLOR);
		json.addProperty("modelResource", entry.modelResource);
		json.addProperty("textureResource", entry.json.has("textureId") ? entry.json.get("textureId").getAsString() : "");
		json.addProperty("flipTextureV", entry.json.has("flipV") && entry.json.get("flipV").getAsBoolean());
		json.addProperty("repeatInterval", entry.json.has("repeatInterval") ? entry.json.get("repeatInterval").getAsDouble() : 0.5);
		json.addProperty("modelYOffset", entry.json.has("yOffset") ? entry.json.get("yOffset").getAsDouble() : 0);
		return json;
	}

	/** 补全命名空间（NTE 用 ResourceLocation，无命名空间即 minecraft）。 */
	private static String formatModelIdentifier(String model) {
		return model.contains(":") ? model : "minecraft:" + model;
	}

	private static String toPackPath(String identifier) {
		return "assets/" + identifier.replace(':', '/');
	}
}
