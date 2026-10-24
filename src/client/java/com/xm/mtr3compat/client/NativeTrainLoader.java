package com.xm.mtr3compat.client;

import org.apache.commons.io.IOUtils;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.mtr.mod.Init;
import org.mtr.mod.resource.ResourceProvider;
import org.mtr.mod.resource.VehicleModel;
import org.mtr.mod.resource.VehicleResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * MTR3 原生加载路径：勾选（MTR3 模式）的 resourcepacks 资源包中 custom_trains 条目
 * 以 MTR3 语义注册为原生 VehicleResource（一车一条目、id=原名、无 4 变体），
 * 几何按 (currentCar, totalCars) 在 supplier 内逐车过滤 whitelisted/blacklisted_cars。
 * 逻辑复刻 LegacyVehicleResource.convert 的单变体路径（padding=0、length=props.length+1）。
 */
public final class NativeTrainLoader {

	private static final String VARIANTS_PREFIX = "mtr_custom_train_";
	private static final String[] VARIATION_SUFFIXES = {"_trailer", "_cab_1", "_cab_2", "_cab_3"};
	private static final String[] LEGACY_GANGWAYS = {"a_train", "e44", "k_train_ael", "k_train", "k_train_tcl", "m_train", "r211", "s_train", "sp1900"};
	private static final String[] LEGACY_BARRIERS = {"london_underground_d78", "mlr", "r179"};

	private static final ObjectOpenHashSet<String> NATIVE_IDS = new ObjectOpenHashSet<>();
	private static final ObjectArrayList<NativeEntry2> ENTRIES = new ObjectArrayList<>();
	/** 原生注册的车型 id -> 来源资源包名（不含 .zip），供按包区分的渲染校正使用。 */
	private static final java.util.HashMap<String, String> VEHICLE_PACK = new java.util.HashMap<>();

	public static String packOf(String vehicleId) {
		return VEHICLE_PACK.get(vehicleId);
	}

	/** 经启用中的资源包读取 OBJ/贴图/JSON（勾选包仍在原版正常启用，仅解析被拦截）。 */
	private static final ResourceProvider RESOURCE_PROVIDER = new ResourceProvider() {
		@Override
		public String get(Identifier identifier) {
			return ResourceManagerHelper.readResource(identifier);
		}

		@Override
		public byte[] getBytes(Identifier identifier) {
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

	private NativeTrainLoader() {
	}

	private static final class NativeEntry2 {
		private final String id;
		private final String pack;
		private final JsonObject json;

		private NativeEntry2(String id, String pack, JsonObject json) {
			this.id = id;
			this.pack = pack;
			this.json = json;
		}
	}

	public static boolean isNative(String vehicleId) {
		return NATIVE_IDS.contains(vehicleId);
	}

	/** 已原生注册的车型 id（供 JCM 脚本别名使用）。 */
	public static Iterable<String> nativeIds() {
		return NATIVE_IDS;
	}

	/** LegacyVehicleResource 生成的 4 变体 id 是否属于已原生注册的 mtr3-compat 车型（应抑制）。 */
	public static boolean shouldSuppress(String variantId) {
		if (NATIVE_IDS.isEmpty() || !variantId.startsWith(VARIANTS_PREFIX)) {
			return false;
		}
		for (final String suffix : VARIATION_SUFFIXES) {
			if (variantId.endsWith(suffix)) {
				final String base = variantId.substring(VARIANTS_PREFIX.length(), variantId.length() - suffix.length());
				if (NATIVE_IDS.contains(base)) {
					return true;
				}
			}
		}
		return false;
	}

	/** CustomResourceLoader.reload HEAD：直接读磁盘收集勾选（MTR3 模式）资源包的 custom_trains 条目。 */
	public static void scan() {
		NATIVE_IDS.clear();
		ENTRIES.clear();
		VEHICLE_PACK.clear();
		JcmScriptAlias.invalidate();
		// 只处理原版资源包管理器真正加载了的包：未启用的包贴图/脚本/JSON 一律读不到，
		// 注册出来只会造成 "Script ... is missing" 与 null 配置报错。
		final java.util.Set<String> loadedPacks = Mtr3CompatPacks.loadedPackNames();
		for (final Path packPath : Mtr3CompatPacks.mtrPacks()) {
			if (!Mtr3CompatConfig.isIntercepted(packPath.getFileName().toString())) {
				continue;
			}
			if (!loadedPacks.contains(Mtr3CompatPacks.packBaseName(packPath))) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] 跳过未启用的资源包: " + Mtr3CompatPacks.packBaseName(packPath));
				continue;
			}
			final byte[] bytes = Mtr3CompatPacks.readPackFile(packPath, "assets/mtr/mtr_custom_resources.json");
			if (bytes == null) {
				continue;
			}
			try {
				final JsonObject root = Utilities.parseJson(new String(bytes, StandardCharsets.UTF_8));
				if (root == null || !root.has("custom_trains")) {
					continue;
				}
				final JsonElement customTrains = root.get("custom_trains");
				if (customTrains.isJsonObject()) {
					for (final Map.Entry<String, JsonElement> entry : customTrains.getAsJsonObject().entrySet()) {
						addEntry(Mtr3CompatPacks.packBaseName(packPath), entry.getKey().toLowerCase(Locale.ENGLISH), entry.getValue());
					}
				} else if (customTrains.isJsonArray()) {
					for (final JsonElement element : customTrains.getAsJsonArray()) {
						if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
							addEntry(Mtr3CompatPacks.packBaseName(packPath), element.getAsJsonObject().get("id").getAsString().toLowerCase(Locale.ENGLISH), element);
						}
					}
				}
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 解析 mtr_custom_resources.json 失败: " + packPath, e);
			}
		}
	}

	private static void addEntry(String pack, String id, JsonElement element) {
		if (element.isJsonObject() && !id.isEmpty()) {
			NATIVE_IDS.add(id);
			ENTRIES.add(new NativeEntry2(id, pack, element.getAsJsonObject()));
		}
	}

	/** CustomResourceLoader.reload TAIL：注册全部原生 VehicleResource。 */
	public static void register(Consumer<VehicleResource> registerVehicle) {
		for (final NativeEntry2 entry : ENTRIES) {
			try {
				registerOne(entry, registerVehicle);
				VEHICLE_PACK.put(entry.id, entry.pack);
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 原生注册失败: " + entry.id, e);
			}
		}
	}

	private static void registerOne(NativeEntry2 entry, Consumer<VehicleResource> registerVehicle) {
		final LegacyEntry legacy = new LegacyEntry(new JsonReader(entry.json));
		final JsonObject propertiesObject = legacy.modelProperties().isEmpty() ? null : Utilities.parseJson(RESOURCE_PROVIDER.get(new Identifier(legacy.modelProperties())));
		if (propertiesObject == null) {
			return;
		}

		final JsonObject baseObject = new JsonObject();
		baseObject.addProperty("id", entry.id);
		baseObject.addProperty("name", legacy.name().isEmpty() ? entry.id : legacy.name());
		baseObject.addProperty("color", legacy.color());

		if (!legacy.description().isEmpty()) {
			baseObject.addProperty("description", legacy.description());
		}

		if (!legacy.wikipediaArticle().isEmpty()) {
			baseObject.addProperty("wikipediaArticle", legacy.wikipediaArticle());
		}

		if (!legacy.bveSoundBaseId().isEmpty()) {
			baseObject.addProperty("bveSoundBaseResource", legacy.bveSoundBaseId());
		}

		if (!legacy.speedSoundBaseId().isEmpty()) {
			baseObject.addProperty("legacySpeedSoundBaseResource", legacy.speedSoundBaseId());
		}

		if (!legacy.doorSoundBaseId().isEmpty()) {
			baseObject.addProperty("legacyDoorSoundBaseResource", legacy.doorSoundBaseId());
		}

		if (legacy.speedSoundCount() != 0) {
			baseObject.addProperty("legacySpeedSoundCount", legacy.speedSoundCount());
		}

		if (legacy.accelSoundAtCoast()) {
			baseObject.addProperty("legacyUseAccelerationSoundsWhenCoasting", true);
		}

		if (legacy.constPlaybackSpeed()) {
			baseObject.addProperty("legacyConstantPlaybackSpeed", true);
		}

		baseObject.addProperty("legacyDoorCloseSoundTime", legacy.doorCloseSoundTime() == 0 ? 0.5 : legacy.doorCloseSoundTime());

		double length = 0;
		double width = 0;
		double doorMax = 0;

		try {
			baseObject.addProperty("transportMode", propertiesObject.get("transport_mode").getAsString());
			length = propertiesObject.get("length").getAsDouble() + 1;
			width = propertiesObject.get("width").getAsDouble();
		} catch (Exception ignored1) {
			try {
				final String baseTrainType = legacy.baseTrainType().startsWith("base_") ? legacy.baseTrainType().replace("base_", "train_") : legacy.baseTrainType();
				final String[] baseTrainTypeSplit = baseTrainType.toUpperCase(Locale.ENGLISH).replace("CABLE_CAR", "CABLE-CAR").split("_");
				baseObject.addProperty("transportMode", baseTrainTypeSplit[0].replace("CABLE-CAR", "CABLE_CAR"));
				length = Double.parseDouble(baseTrainTypeSplit[1]) + 1;
				width = Double.parseDouble(baseTrainTypeSplit[2]);
			} catch (Exception ignored2) {
			}
		}

		try {
			doorMax = propertiesObject.get("door_max").getAsDouble();
		} catch (Exception ignored) {
		}

		if (length == 0 || width == 0) {
			return;
		}

		baseObject.addProperty("length", length);
		baseObject.addProperty("width", width);
		final double bogiePosition = length < 10 ? 0 : length * 0.34;
		baseObject.addProperty("bogie1Position", -bogiePosition);
		baseObject.addProperty("bogie2Position", bogiePosition);
		// MTR3 语义：间距 = length(=props.length+1) + 0 + 0，车厢几何按槽位建模，禁止按变体位加 padding
		baseObject.addProperty("couplingPadding1", 0);
		baseObject.addProperty("couplingPadding2", 0);
		// MTR3：has_gangway_connection 只影响连通性，风挡贴图仅在 gangway_connection_id 非空时才绘制。
		// 为空时 MTR4 若按 sp1900 回退绘制，就会在每个车缝多出一层“别人的风挡”。
		final boolean hasGangway = !legacy.gangwayConnectionId().isEmpty();
		baseObject.addProperty("hasGangway1", hasGangway);
		baseObject.addProperty("hasGangway2", hasGangway);
		baseObject.addProperty("hasBarrier1", !legacy.trainBarrierId().isEmpty());
		baseObject.addProperty("hasBarrier2", !legacy.trainBarrierId().isEmpty());
		baseObject.addProperty("legacyRiderOffset", legacy.riderOffset());

		final double finalDoorMax = doorMax;
		final boolean hideDisplay = Mtr3CompatConfig.hideNativeDisplay(entry.pack);
		Mtr3CompatClient.LOGGER.info("[mtr3compat] 注册原生车型 {} length={} width={} doorMax={} hideDisplay={} model={}", entry.id, length, width, doorMax, hideDisplay, legacy.model());
		registerVehicle.accept(new VehicleResource(new JsonReader(baseObject),
				(currentCar, totalCars) -> buildModels(legacy, propertiesObject, finalDoorMax, currentCar, totalCars, entry.id, hideDisplay),
				RESOURCE_PROVIDER));
	}

	/** 复刻 LegacyVehicleResource.convert 的 supplier lambda：逐车过滤 parts，构造 VehicleModel 列表。 */
	private static ObjectArrayList<VehicleModel> buildModels(LegacyEntry legacy, JsonObject propertiesObject, double doorMax, int currentCar, int totalCars, String id, boolean hideDisplay) {
		boolean isObj = false;
		boolean reversed = false;
		final ObjectArrayList<JsonObject> modelObjects = new ObjectArrayList<>();
		final String[] modelSplit = splitWithEmptyStrings(legacy.model(), '|');
		for (int j = 0; j < modelSplit.length; j += 2) {
			final String[] conditions = j + 1 < modelSplit.length ? splitWithEmptyStrings(modelSplit[j + 1], ';') : new String[]{};
			if (modelSplit[j].isEmpty()) {
				// 纯脚本车型（无 model 字段、base_type 兜底后仍为空）：MTR 加载空名模型会反复报 "Invalid model!"，
				// 且产出 null VehicleModel。车身本就由脚本 drawCarModel 绘制，这里直接跳过空模型条目。
				continue;
			}
			if (conditions.length < 2 || matchesFilter(conditions[1].split(","), currentCar, totalCars) <= matchesFilter(conditions[0].split(","), currentCar, totalCars)) {
				final JsonObject modelObject = new JsonObject();
				modelObject.addProperty("modelResource", modelSplit[j]);
				modelObject.addProperty("textureResource", legacy.textureId());
				modelObject.addProperty("flipTextureV", legacy.flipV());
				modelObjects.add(modelObject);
				if (modelSplit[j].endsWith(".obj")) {
					isObj = true;
				}
				if (conditions.length >= 3 && conditions[2].equals("reversed")) {
					reversed = true;
				}
			}
		}

		final JsonArray positionDefinitionsArray = new JsonArray();
		final JsonArray partsArray = new JsonArray();

		try {
			processModel(legacy, currentCar, totalCars, isObj, reversed, propertiesObject.getAsJsonArray("parts"), positionDefinitionsArray, partsArray, doorMax, hideDisplay);
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] processModel 失败: {} car={}/{}", id, currentCar, totalCars, e);
		}

		// Migrate legacy gangway & barriers
		// 注意：has_gangway_connection 只表示“车厢连通”，MTR3 在 gangway_connection_id 为空时
		// 并不绘制风挡（西安包这类车由脚本自己画 FD 模型）。若按 sp1900 贴图回退，
		// MTR4 会在每个车缝额外叠一层 sp1900 风挡，看起来像“别的车的风挡”。
		final String barrierId = legacy.trainBarrierId();
		String barrierBaseId = barrierId;
		final String gangwayId = legacy.gangwayConnectionId();
		final boolean hasVisibleGangway = !gangwayId.isEmpty();
		String gangwayConnectionBaseId = hasVisibleGangway ? gangwayId + "_connector" : "";
		if (gangwayId.startsWith("mtr:textures/entity") && Arrays.stream(LEGACY_GANGWAYS).anyMatch(oldId -> gangwayId.startsWith("mtr:textures/entity/" + oldId))) {
			gangwayConnectionBaseId = gangwayConnectionBaseId
					.replace("/entity", "/gangway")
					.replace("_connector", "");
		}
		if (barrierId.startsWith("mtr:textures/entity") && Arrays.stream(LEGACY_BARRIERS).anyMatch(oldId -> barrierId.startsWith("mtr:textures/entity/" + oldId))) {
			barrierBaseId = barrierBaseId
					.replace("/entity", "/barrier")
					.replace("_barrier", "");
		}

		final JsonObject modelPropertiesObject = new JsonObject();
		modelPropertiesObject.addProperty("modelYOffset", 1);
		if (hasVisibleGangway) {
			modelPropertiesObject.addProperty("gangwayInnerSideResource", gangwayConnectionBaseId + "_side.png");
			modelPropertiesObject.addProperty("gangwayInnerTopResource", gangwayConnectionBaseId + "_roof.png");
			modelPropertiesObject.addProperty("gangwayInnerBottomResource", gangwayConnectionBaseId + "_floor.png");
			modelPropertiesObject.addProperty("gangwayOuterSideResource", gangwayConnectionBaseId + "_exterior.png");
			modelPropertiesObject.addProperty("gangwayOuterTopResource", gangwayConnectionBaseId + "_exterior.png");
			modelPropertiesObject.addProperty("gangwayOuterBottomResource", gangwayConnectionBaseId + "_exterior.png");
			modelPropertiesObject.addProperty("gangwayWidth", 1.5);
			modelPropertiesObject.addProperty("gangwayHeight", 2.25);
			modelPropertiesObject.addProperty("gangwayYOffset", 1);
			modelPropertiesObject.addProperty("gangwayZOffset", 0.5);
		}
		modelPropertiesObject.addProperty("barrierInnerSideResource", barrierBaseId + "_exterior.png");
		modelPropertiesObject.addProperty("barrierOuterSideResource", barrierBaseId + "_exterior.png");
		modelPropertiesObject.addProperty("barrierWidth", 2.25);
		modelPropertiesObject.addProperty("barrierHeight", 1);
		modelPropertiesObject.addProperty("barrierYOffset", 1.25);
		modelPropertiesObject.addProperty("barrierZOffset", 0.25);
		modelPropertiesObject.add("parts", partsArray);

		final JsonObject positionDefinitionsObject = new JsonObject();
		positionDefinitionsObject.add("positionDefinitions", positionDefinitionsArray);
		Mtr3CompatClient.LOGGER.info("[mtr3compat] {} car={}/{} models={} parts={} posDefs={}", id, currentCar, totalCars, modelObjects.size(), partsArray.size(), positionDefinitionsArray.size());
		return modelObjects.stream().map(modelObject -> new VehicleModel(new JsonReader(modelObject), new JsonReader(modelPropertiesObject), new JsonReader(positionDefinitionsObject), id, RESOURCE_PROVIDER)).collect(Collectors.toCollection(ObjectArrayList::new));
	}

	/** 复刻 LegacyVehicleResource.processModel（MTR3 只有单一 "parts" 数组，无 override）。 */
	private static void processModel(LegacyEntry legacy, int currentCar, int totalCars, boolean isObj, boolean reversed, JsonArray propertiesPartsArray, JsonArray positionDefinitionsArray, JsonArray partsArray, double doorMax, boolean hideDisplay) {
		propertiesPartsArray.forEach(jsonElement -> {
			final JsonObject propertiesPartsObject = jsonElement.getAsJsonObject();
			final String[] whitelistedCarsFilters = tryGet(propertiesPartsObject, "whitelisted_cars").split(",");
			final String[] blacklistedCarsFilters = tryGet(propertiesPartsObject, "blacklisted_cars").split(",");

			// MTR3 遗留的 display 部件（原生文字屏）：该包已改由脚本绘制 LCD，配置 hide_native_display
			// 后直接不再生成这些部件，避免门上出现 MTR4 额外画出的屏幕。
			if (hideDisplay && propertiesPartsObject.has("display")) {
				return;
			}

			if (matchesFilter(blacklistedCarsFilters, currentCar, totalCars) <= matchesFilter(whitelistedCarsFilters, currentCar, totalCars)) {
				final JsonObject partsObject = new JsonObject();
				partsArray.add(partsObject);

				if (propertiesPartsObject.has("name")) {
					addSingleArrayItem(partsObject, "names", tryGet(propertiesPartsObject, "name"));
				} else {
					addSingleArrayItem(partsObject, "names", tryGet(propertiesPartsObject, "part_name"));
				}

				partsObject.addProperty("renderStage", tryGet(propertiesPartsObject, "stage").toUpperCase(Locale.ENGLISH));
				partsObject.addProperty("doorAnimationType", legacy.doorAnimationType());

				final String renderCondition;
				switch (tryGet(propertiesPartsObject, "render_condition").toUpperCase(Locale.ENGLISH)) {
					case "DOORS_OPEN":
					case "DOORS_LEFT_OPEN":
					case "DOORS_RIGHT_OPEN":
						renderCondition = "DOORS_OPENED";
						break;
					case "DOORS_CLOSED":
					case "DOORS_LEFT_CLOSED":
					case "DOORS_RIGHT_CLOSED":
						renderCondition = "DOORS_CLOSED";
						break;
					case "MOVING_FORWARDS":
						renderCondition = "ON_ROUTE_FORWARDS";
						break;
					case "MOVING_BACKWARDS":
						renderCondition = "ON_ROUTE_BACKWARDS";
						break;
					default:
						renderCondition = "NORMAL";
						break;
				}
				partsObject.addProperty("condition", renderCondition);

				if (propertiesPartsObject.has("display")) {
					partsObject.addProperty("type", "DISPLAY");

					final JsonObject displayObject = propertiesPartsObject.getAsJsonObject("display");
					partsObject.addProperty("displayXPadding", tryGetNumber(displayObject, "x_padding"));
					partsObject.addProperty("displayYPadding", tryGetNumber(displayObject, "y_padding"));
					partsObject.addProperty("displayCjkSizeRatio", tryGetNumber(displayObject, "cjk_size_ratio"));
					partsObject.addProperty("displayColorCjk", tryGet(displayObject, "color_cjk"));
					partsObject.addProperty("displayColor", tryGet(displayObject, "color"));
					partsObject.addProperty("displayPadZeros", tryGetNumber(displayObject, "display_pad_zeros"));

					final String displayType = tryGet(displayObject, "type");
					partsObject.addProperty("displayType", "NEXT_STATION_PLAIN".equals(displayType) ? "NEXT_STATION" : displayType);

					final JsonArray displayOptionsArray = new JsonArray();
					partsObject.add("displayOptions", displayOptionsArray);
					if (tryGetBoolean(displayObject, "should_scroll")) {
						displayOptionsArray.add("SCROLL_NORMAL");
					}
					if (tryGetBoolean(displayObject, "force_upper_case")) {
						displayOptionsArray.add("UPPER_CASE");
					}
					if (tryGetBoolean(displayObject, "force_single_line")) {
						displayOptionsArray.add("SINGLE_LINE");
					}
				} else {
					partsObject.addProperty("type", "NORMAL");
				}

				final boolean mirror = tryGetBoolean(propertiesPartsObject, "mirror");
				final double doorXMultiplier;
				final double doorZMultiplier;
				if (propertiesPartsObject.has("door_offset")) {
					switch (tryGet(propertiesPartsObject, "door_offset").toUpperCase(Locale.ENGLISH)) {
						case "LEFT_NEGATIVE":
							doorXMultiplier = mirror ? -1 : 1;
							doorZMultiplier = -doorMax;
							break;
						case "RIGHT_NEGATIVE":
							doorXMultiplier = mirror ? 1 : -1;
							doorZMultiplier = -doorMax;
							break;
						case "LEFT_POSITIVE":
							doorXMultiplier = mirror ? -1 : 1;
							doorZMultiplier = doorMax;
							break;
						case "RIGHT_POSITIVE":
							doorXMultiplier = mirror ? 1 : -1;
							doorZMultiplier = doorMax;
							break;
						default:
							doorXMultiplier = 0;
							doorZMultiplier = 0;
							break;
					}
				} else {
					switch (tryGet(propertiesPartsObject, "door_offset_x").toUpperCase(Locale.ENGLISH)) {
						case "LEFT":
						case "LEFT_NEGATIVE":
						case "RIGHT":
						case "RIGHT_NEGATIVE":
							doorXMultiplier = -1;
							break;
						default:
							doorXMultiplier = 0;
							break;
					}
					switch (tryGet(propertiesPartsObject, "door_offset_z").toUpperCase(Locale.ENGLISH)) {
						case "LEFT_NEGATIVE":
						case "RIGHT_NEGATIVE":
							doorZMultiplier = -doorMax;
							break;
						case "LEFT":
						case "RIGHT":
							doorZMultiplier = doorMax;
							break;
						default:
							doorZMultiplier = 0;
							break;
					}
				}
				partsObject.addProperty("doorXMultiplier", doorXMultiplier);
				partsObject.addProperty("doorZMultiplier", (isObj ? -1 : 1) * doorZMultiplier);

				final String positionDefinitionName = "definition_" + Init.randomString();
				final JsonArray positionDefinitionPositionsArray = new JsonArray();
				final JsonArray positionDefinitionPositionsFlippedArray = new JsonArray();
				final JsonObject positionDefinitionObject = new JsonObject();
				positionDefinitionObject.addProperty("name", positionDefinitionName);
				positionDefinitionObject.add("positions", reversed ? positionDefinitionPositionsFlippedArray : positionDefinitionPositionsArray);
				positionDefinitionObject.add("positionsFlipped", reversed ? positionDefinitionPositionsArray : positionDefinitionPositionsFlippedArray);
				positionDefinitionsArray.add(positionDefinitionObject);

				// 从一手源码推导 OBJ 部件的等价变换（对任意资源包成立，而非拟合单个包）：
				//   NTE 装载期烘焙（DynamicTrainModelLoader L250-265）：非镜像 v' = v + t3；镜像 v' = Ry(π)·v + (-x,0,z)。
				//   NTE 渲染（SowcerModelAgent L25-32）：world = M · Rx(π) · v'。
				//   MTR4 装载（ObjModelLoader 烘焙 Rx(π)）+ addTransformation：world = M · Ry(π·flip) · (Rx(π)·v + t4)。
				// 令两式恒等：
				//   非镜像（flip=false）：Rx(π)v + Rx(π)t3 = Rx(π)v + t4   → t4 = (x, 0, -z)
				//   镜像  （flip=true） ：Rx(π)Ry(π)v + Rx(π)(-x,0,z) = Ry(π)(Rx(π)v + t4) → Ry(π)t4 = (-x,0,-z) → t4 = (x, 0, z)
				// 即 z 仅在未翻转时取负；翻转条目在 positionsFlipped 中还会被 iteratePositions 对 x 取负，故存 -x。
				final boolean objOffset = isObj && !propertiesPartsObject.has("display");
				// flip = 条目最终是否按翻转渲染 = (落在 positionsFlipped) XOR reversed
				final boolean flipPositions = mirror ^ reversed;
				final boolean flipPositionsFlipped = !reversed;
				processPositions(propertiesPartsObject, "positions", reversed, mirror, flipPositions, mirror ? positionDefinitionPositionsFlippedArray : positionDefinitionPositionsArray, objOffset);
				processPositions(propertiesPartsObject, "positions_flipped", reversed, true, flipPositionsFlipped, positionDefinitionPositionsFlippedArray, objOffset);

				addSingleArrayItem(partsObject, "positionDefinitions", positionDefinitionName);
			}
		});
	}

	private static void processPositions(JsonObject propertiesPartsObject, String property, boolean reversed, boolean flippedEntry, boolean flip, JsonArray positionDefinitionPositionsArray, boolean objOffset) {
		try {
			propertiesPartsObject.getAsJsonArray(property).forEach(jsonElement -> {
				final JsonObject positionDefinitionObject = new JsonObject();
				final double x = jsonElement.getAsJsonArray().get(0).getAsDouble();
				final double z = jsonElement.getAsJsonArray().get(1).getAsDouble();
				positionDefinitionObject.addProperty("x", objOffset ? (flip ? -x : x) : (flippedEntry ? -x : x));
				positionDefinitionObject.addProperty("z", objOffset ? (flip ? z : -z) : z * (reversed ? -1 : 1));
				positionDefinitionPositionsArray.add(positionDefinitionObject);
			});
		} catch (Exception ignored) {
		}
	}

	private static void addSingleArrayItem(JsonObject jsonObject, String property, String string) {
		final JsonArray jsonArray = new JsonArray();
		jsonArray.add(string);
		jsonObject.add(property, jsonArray);
	}

	private static String tryGet(JsonObject jsonObject, String property) {
		try {
			return jsonObject.get(property).getAsString();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static double tryGetNumber(JsonObject jsonObject, String property) {
		try {
			return jsonObject.get(property).getAsDouble();
		} catch (Exception ignored) {
			return 0;
		}
	}

	private static boolean tryGetBoolean(JsonObject jsonObject, String property) {
		try {
			return jsonObject.has(property) && jsonObject.get(property).getAsBoolean();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static int matchesFilter(String[] filters, int currentCar, int totalCars) {
		int strength = filters.length == 0 ? 1 : 0;

		for (final String filter : filters) {
			if (!filter.isEmpty()) {
				if (filter.contains("%")) {
					try {
						final String[] filterSplit = filter.split("\\+");
						final int multiple = Integer.parseInt(filterSplit[0].replace("%", ""));
						final int additional = filterSplit.length == 1 ? 0 : Integer.parseInt(filterSplit[1]);
						if ((currentCar + 1 + additional) % multiple == 0) {
							strength = 2;
						}
					} catch (Exception ignored) {
					}
				} else {
					try {
						final int car = Integer.parseInt(filter);
						if (car == currentCar + 1 || car == currentCar - totalCars) {
							return 3;
						}
					} catch (Exception ignored) {
					}
				}
			}
		}

		return strength;
	}

	private static String[] splitWithEmptyStrings(String string, char token) {
		final String filler = Init.randomString();
		final String[] firstSplit = string.replace(String.valueOf(token), String.format("%1$s%2$s%1$s", filler, token)).split(("\\") + token);
		final String[] finalSplit = new String[firstSplit.length];
		for (int i = 0; i < firstSplit.length; i++) {
			finalSplit[i] = firstSplit[i].replace(filler, "");
		}
		return finalSplit;
	}
}
