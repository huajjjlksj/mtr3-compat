package com.xm.mtr3compat.client;

import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.CustomResourceTools;
import org.mtr.mod.resource.ModelResourceLoader;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.PartCondition;
import org.mtr.mod.resource.RenderStage;
import org.mtr.mod.resource.ResourceProvider;
import org.mtr.mod.resource.VehicleResource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTR3 原生车型的合批渲染。
 * <p>
 * MTR3 一个车型只有一套模型文件，靠 parts 的 {@code whitelisted_cars}/{@code blacklisted_cars} 在渲染期逐节过滤。
 * MTR4 的 {@link org.mtr.mod.resource.ModelPropertiesPart} 没有运行期过滤字段，只会在构建期按节裁剪，
 * 于是同一份巨型 OBJ 会被每节车厢分别复制、烘焙、上传。
 * <p>
 * 这里把「静态 NORMAL 部件」（不含门、显示、地板、门洞）按
 * （模型文件 × 渲染条件 × 白/黑名单）合批，整个车型只烘焙一次、每批只上传一次；
 * 每节车的动态差异在 {@link #queue} 里用 {@link NativeTrainLoader#matchesFilter} 与
 * {@link VehicleResource#matchesCondition} 复现，行为与 MTR3 一致。
 * <p>
 * 门部件仍由每节车的 {@code DynamicVehicleModel} 动态渲染（开/关需要按门洞映射动画），
 * 因此调用方在合批车型上会把非门静态部件从每节车模型里剔除，避免重复烘焙。
 */
public final class NativeVehicleRenderer {

	/** OBJ 部件烘焙时的 Y 偏移，与 NativeVehicleLoader 写入模型属性的 modelYOffset 保持一致。 */
	private static final double MODEL_Y_OFFSET = 1;

	private static final Map<String, Plan> PLANS = new ConcurrentHashMap<>();

	private NativeVehicleRenderer() {
	}

	/** 车型是否已启用合批（仅当车型的所有模型文件都是 .obj，且 GPU 支持优化渲染路径时启用）。 */
	public static boolean isBatchable(String vehicleId) {
		return vehicleId != null && OptimizedRenderer.hasOptimizedRendering() && PLANS.containsKey(vehicleId);
	}

	public static void clear() {
		PLANS.clear();
	}

	/** 在注册原生车型时调用：解析模型属性，生成合批计划（不做实际烘焙）。 */
	static void buildPlan(String vehicleId, LegacyEntry legacy, JsonObject propertiesObject, double doorMax) {
		try {
			final String model = legacy.model();
			if (model.isEmpty() || !propertiesObject.has("parts")) {
				PLANS.remove(vehicleId);
				return;
			}

			final String[] modelSplit = NativeTrainLoader.splitWithEmptyStrings(model, '|');
			for (int i = 0; i < modelSplit.length; i += 2) {
				if (modelSplit[i].isEmpty() || !modelSplit[i].endsWith(".obj")) {
					// 含 bbmodel/mqo 的车型仍走 MTR4 原有逐节构建，避免破坏非 OBJ 的显示/地板逻辑。
					PLANS.remove(vehicleId);
					return;
				}
			}

			final Plan plan = new Plan(
					CustomResourceTools.formatIdentifierWithDefault(legacy.textureId(), "png"),
					legacy.flipV(),
					NativeTrainLoader.resourceProvider()
			);

			for (int i = 0; i < modelSplit.length; i += 2) {
				final String[] conditions = i + 1 < modelSplit.length ? modelSplit[i + 1].split(";") : new String[]{"", "", ""};
				plan.entries.add(buildEntry(legacy, propertiesObject, doorMax, modelSplit[i], conditions));
			}

			PLANS.put(vehicleId, plan);
			final int batchCount = plan.entries.stream().mapToInt(entry -> entry.batches.size()).sum();
			Mtr3CompatClient.LOGGER.info("[mtr3compat] 合批计划 {} 模型={} 批次={}", vehicleId, plan.entries.size(), batchCount);
		} catch (Exception e) {
			PLANS.remove(vehicleId);
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 合批计划构建失败，退回逐节构建: " + vehicleId, e);
		}
	}

	private static Entry buildEntry(LegacyEntry legacy, JsonObject propertiesObject, double doorMax, String modelResource, String[] conditions) {
		final boolean reversed = conditions.length >= 3 && "reversed".equals(conditions[2]);
		final JsonArray positionDefinitionsArray = new JsonArray();
		final JsonArray partsArray = new JsonArray();
		NativeTrainLoader.processModel(legacy, 0, 1, true, reversed, propertiesObject.getAsJsonArray("parts"), positionDefinitionsArray, partsArray, doorMax, true);

		final Map<String, JsonObject> definitions = new HashMap<>();
		for (final JsonElement element : positionDefinitionsArray) {
			final JsonObject definition = element.getAsJsonObject();
			definitions.put(NativeTrainLoader.tryGet(definition, "name"), definition);
		}

		final Map<String, Batch> batches = new HashMap<>();
		for (final JsonElement element : partsArray) {
			final JsonObject part = element.getAsJsonObject();
			if (!"NORMAL".equals(NativeTrainLoader.tryGet(part, "type")) || NativeTrainLoader.tryGetNumber(part, "doorXMultiplier") != 0 || NativeTrainLoader.tryGetNumber(part, "doorZMultiplier") != 0) {
				continue;
			}

			final String[] names = toStringArray(part.getAsJsonArray("names"));
			final String[] positionDefinitionNames = toStringArray(part.getAsJsonArray("positionDefinitions"));
			if (names.length == 0 || positionDefinitionNames.length == 0) {
				continue;
			}

			final JsonObject definition = definitions.get(positionDefinitionNames[0]);
			if (definition == null) {
				continue;
			}

			final String whitelist = NativeTrainLoader.tryGet(part, NativeTrainLoader.META_WHITELIST);
			final String blacklist = NativeTrainLoader.tryGet(part, NativeTrainLoader.META_BLACKLIST);
			final PartCondition condition = parseCondition(NativeTrainLoader.tryGet(part, "condition"));
			final RenderStage stage = parseRenderStage(NativeTrainLoader.tryGet(part, "renderStage"));
			final String key = condition.name() + '|' + whitelist + '|' + blacklist;

			final Batch batch = batches.computeIfAbsent(key, ignored -> new Batch(condition, whitelist.split(","), blacklist.split(",")));
			final ObjectArrayList<Position> positions = new ObjectArrayList<>();
			addPositions(positions, definition.getAsJsonArray("positions"), false);
			addPositions(positions, definition.getAsJsonArray("positionsFlipped"), true);

			for (final String name : names) {
				if (!name.isEmpty()) {
					batch.items.add(new GroupItem(name, stage, positions));
				}
			}
		}

		return new Entry(modelResource, conditions, new ObjectArrayList<>(batches.values()));
	}

	private static void addPositions(ObjectArrayList<Position> positions, JsonArray array, boolean flipped) {
		if (array == null) {
			return;
		}
		for (final JsonElement element : array) {
			try {
				// processPositions 生成的是 {"x":..., "z":...} 对象，不是数组。
				final JsonObject position = element.getAsJsonObject();
				final double x = position.get("x").getAsDouble();
				final double z = position.get("z").getAsDouble();
				positions.add(new Position(flipped ? -x : x, z, flipped));
			} catch (Exception ignored) {
			}
		}
	}

	private static String[] toStringArray(JsonArray array) {
		if (array == null) {
			return new String[0];
		}
		final String[] result = new String[array.size()];
		for (int i = 0; i < array.size(); i++) {
			result[i] = array.get(i).getAsString();
		}
		return result;
	}

	private static PartCondition parseCondition(String value) {
		try {
			return PartCondition.valueOf(value.toUpperCase(Locale.ENGLISH));
		} catch (Exception ignored) {
			return PartCondition.NORMAL;
		}
	}

	private static RenderStage parseRenderStage(String value) {
		try {
			return RenderStage.valueOf(value.toUpperCase(Locale.ENGLISH));
		} catch (Exception ignored) {
			return RenderStage.EXTERIOR;
		}
	}

	/**
	 * {@link VehicleResource#queue} 的替换实现：按节选批、按批渲染，只对已登记合批计划的车型生效。
	 */
	public static void queue(VehicleResource resource, StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int carNumber, int totalCars, int light, boolean noOpenDoorways) {
		if (resource == null) {
			return;
		}
		final Plan plan = PLANS.get(resource.getId());
		if (plan == null) {
			return;
		}
		plan.bake();

		for (final Entry entry : plan.entries) {
			if (!entry.matches(carNumber, totalCars)) {
				continue;
			}
			for (final Batch batch : entry.batches) {
				if (batch.wrapper == null || !batch.matches(carNumber, totalCars) || !VehicleResource.matchesCondition(vehicle, batch.condition, noOpenDoorways)) {
					continue;
				}
				MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> {
					storedMatrixTransformations.transform(graphicsHolder, offset);
					CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(batch.wrapper, graphicsHolder, light);
					graphicsHolder.pop();
				});
			}
		}
	}

	private static final class Plan {
		private final Identifier texture;
		private final boolean flipV;
		private final ResourceProvider provider;
		private final ObjectArrayList<Entry> entries = new ObjectArrayList<>();
		private volatile boolean baked;

		private Plan(Identifier texture, boolean flipV, ResourceProvider provider) {
			this.texture = texture;
			this.flipV = flipV;
			this.provider = provider;
		}

		private void bake() {
			if (baked) {
				return;
			}
			synchronized (this) {
				if (baked) {
					return;
				}
				try {
					CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.beginReload();
					for (final Entry entry : entries) {
						bakeEntry(entry);
					}
				} catch (Exception e) {
					Mtr3CompatClient.LOGGER.warn("[mtr3compat] 合批烘焙失败", e);
				} finally {
					try {
						CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.finishReload();
					} catch (Exception ignored) {
					}
					baked = true;
				}
			}
		}

		private void bakeEntry(Entry entry) {
			final Map<String, OptimizedModel.ObjModel> source;
			try {
				source = ModelResourceLoader.loadModel(entry.modelResource, texture, flipV, provider);
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 合批读取模型失败: " + entry.modelResource, e);
				return;
			}
			if (source == null) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 合批模型为空: {}", entry.modelResource);
				return;
			}
			int uploaded = 0;

			for (final Batch batch : entry.batches) {
				final Map<String, OptimizedModel.ObjModel> accumulated = new HashMap<>();
				for (final GroupItem item : batch.items) {
					if (item.positions.isEmpty()) {
						continue;
					}
					OptimizedModel.ObjModel objModel = accumulated.get(item.name);
					if (objModel == null) {
						final OptimizedModel.ObjModel original = source.get(item.name);
						if (original == null) {
							continue;
						}
						// 共享解析结果的 rawMeshes，仅本批自己的 rawModel 会被追加变换。
						objModel = ModelCache.copyObjModel(original);
						accumulated.put(item.name, objModel);
					}
					for (final Position position : item.positions) {
						objModel.addTransformation(item.stage.shaderType, position.x / 16, position.y / 16 - MODEL_Y_OFFSET, position.z / 16, position.flipped);
					}
				}

				if (accumulated.isEmpty()) {
					continue;
				}
				final ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> wrappers = new ObjectArrayList<>();
				for (final OptimizedModel.ObjModel objModel : accumulated.values()) {
					wrappers.add(new OptimizedModelWrapper.ObjModelWrapper(objModel));
				}
				batch.wrapper = OptimizedModelWrapper.fromObjModels(wrappers);
				if (batch.wrapper != null) {
					uploaded++;
				}
			}
			if (uploaded == 0) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 合批无可见几何（零件名与 OBJ 组名不匹配？）: {}", entry.modelResource);
			}
		}
	}

	private static final class Entry {
		private final String modelResource;
		private final String[] conditions;
		private final ObjectArrayList<Batch> batches;

		private Entry(String modelResource, String[] conditions, ObjectArrayList<Batch> batches) {
			this.modelResource = modelResource;
			this.conditions = conditions;
			this.batches = batches;
		}

		/** 复刻 LegacyVehicleResource.buildModels 的模型条件过滤。 */
		private boolean matches(int currentCar, int totalCars) {
			return conditions.length < 2 || NativeTrainLoader.matchesFilter(conditions[1].split(","), currentCar, totalCars) <= NativeTrainLoader.matchesFilter(conditions[0].split(","), currentCar, totalCars);
		}
	}

	private static final class Batch {
		private final PartCondition condition;
		private final String[] whitelist;
		private final String[] blacklist;
		private final ObjectArrayList<GroupItem> items = new ObjectArrayList<>();
		private volatile OptimizedModelWrapper wrapper;

		private Batch(PartCondition condition, String[] whitelist, String[] blacklist) {
			this.condition = condition;
			this.whitelist = whitelist;
			this.blacklist = blacklist;
		}

		private boolean matches(int currentCar, int totalCars) {
			return NativeTrainLoader.matchesFilter(blacklist, currentCar, totalCars) <= NativeTrainLoader.matchesFilter(whitelist, currentCar, totalCars);
		}
	}

	private static final class GroupItem {
		private final String name;
		private final RenderStage stage;
		private final ObjectArrayList<Position> positions;

		private GroupItem(String name, RenderStage stage, ObjectArrayList<Position> positions) {
			this.name = name;
			this.stage = stage;
			this.positions = positions;
		}
	}

	private static final class Position {
		private final double x;
		private final double y;
		private final double z;
		private final boolean flipped;

		private Position(double x, double z, boolean flipped) {
			this.x = x;
			this.y = 0;
			this.z = z;
			this.flipped = flipped;
		}
	}
}
