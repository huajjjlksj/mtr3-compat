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

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

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
	/** 条目声明了 model/贴图/属性，但已启用资源包里实际不存在这些文件：无法渲染，故不注册（选车列表里也不会出现）。 */
	private static final ObjectOpenHashSet<String> BROKEN_IDS = new ObjectOpenHashSet<>();
	private static final ObjectArrayList<NativeEntry2> ENTRIES = new ObjectArrayList<>();
	/** 原生注册的车型 id -> 来源资源包名（不含 .zip），供按包区分的渲染校正使用。 */
	private static final java.util.HashMap<String, String> VEHICLE_PACK = new java.util.HashMap<>();
	/** 原生注册的车型 id -> 资源包里声明的显示名（未声明时回退为 id）。 */
	private static final java.util.HashMap<String, String> VEHICLE_NAME = new java.util.HashMap<>();
	/** 当前是否为 MTR3 自动编组模式：MTR4 车辆不加载时必然是，否则取决于开关（读取实时的配置值，切开关不需要重载）。 */
	public static boolean isAutoConsistMode() {
		return !Mtr3CompatConfig.isMtr4Vehicles() || Mtr3CompatConfig.isAutoConsist();
	}

	public static String packOf(String vehicleId) {
		return VEHICLE_PACK.get(vehicleId);
	}

	/** 是否为本模组为勾选（MTR3 模式）资源包提供的 ResourceProvider（用于区分解析缓存的作用范围）。 */
	public static boolean isNativeResourceProvider(ResourceProvider provider) {
		return provider == RESOURCE_PROVIDER;
	}

	public static String nativeName(String vehicleId) {
		final String name = VEHICLE_NAME.get(vehicleId);
		return name == null || name.isEmpty() ? vehicleId : name;
	}

	/** 经启用中的资源包读取 OBJ/贴图/JSON（勾选包仍在原版正常启用，仅解析被拦截）。 */
	private static final ResourceProvider RESOURCE_PROVIDER = new ResourceProvider() {
		@Override
		public String get(Identifier identifier) {
			final String content = ResourceManagerHelper.readResource(identifier);
			return identifier.getPath().endsWith(".mtl") ? fixMissingMtlTextures(identifier, content) : content;
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

	public static int countNative() {
		return NATIVE_IDS.size();
	}

	/** 该 id 是否为勾选资源包经 MTR4 LegacyVehicleResource 转换出的变体（车头/车厢/车尾等）。 */
	public static boolean isMtr4Variation(String vehicleId) {
		if (vehicleId == null || !vehicleId.startsWith(VARIANTS_PREFIX)) {
			return false;
		}
		for (final String suffix : VARIATION_SUFFIXES) {
			if (vehicleId.endsWith(suffix)) {
				return NATIVE_IDS.contains(vehicleId.substring(VARIANTS_PREFIX.length(), vehicleId.length() - suffix.length()));
			}
		}
		return false;
	}

	/** CustomResourceLoader.reload HEAD：直接读磁盘收集勾选（MTR3 模式）资源包的 custom_trains 条目。 */
	public static void scan() {
		NATIVE_IDS.clear();
		BROKEN_IDS.clear();
		ENTRIES.clear();
		VEHICLE_PACK.clear();
		VEHICLE_NAME.clear();
		ModelCache.clear();
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
			VEHICLE_NAME.put(id, tryGet(element.getAsJsonObject(), "name"));
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
		final boolean scriptOnly = isScriptOnly(legacy);
		final String missingResource = scriptOnly ? null : firstMissingResource(legacy);
		if (missingResource != null) {
			BROKEN_IDS.add(entry.id);
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 资源包缺少文件，隐藏该车型: {} -> {}", entry.id, missingResource);
			return;
		}

		JsonObject propertiesObject;
		if (scriptOnly) {
			// 纯脚本车型：无 model/贴图，靠 base_type 提供尺寸，车身由脚本绘制，属性只需空 parts。
			propertiesObject = new JsonObject();
			propertiesObject.add("parts", new JsonArray());
		} else {
			propertiesObject = legacy.modelProperties().isEmpty() ? null : Utilities.parseJson(RESOURCE_PROVIDER.get(new Identifier(legacy.modelProperties())));
			if (propertiesObject == null || !propertiesObject.has("parts")) {
				BROKEN_IDS.add(entry.id);
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 缺少可用模型属性，隐藏该车型: {} -> {}", entry.id, legacy.modelProperties());
				return;
			}
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
		final boolean hasGangway = !effectiveGangwayId(legacy).isEmpty();
		baseObject.addProperty("hasGangway1", hasGangway);
		baseObject.addProperty("hasGangway2", hasGangway);
		baseObject.addProperty("hasBarrier1", !legacy.trainBarrierId().isEmpty());
		baseObject.addProperty("hasBarrier2", !legacy.trainBarrierId().isEmpty());
		baseObject.addProperty("legacyRiderOffset", legacy.riderOffset());

		final double finalDoorMax = doorMax;
		Mtr3CompatClient.LOGGER.info("[mtr3compat] 注册原生车型 {} length={} width={} doorMax={} model={}", entry.id, length, width, doorMax, legacy.model());
		registerVehicle.accept(new VehicleResource(new JsonReader(baseObject),
				(currentCar, totalCars) -> buildModels(legacy, propertiesObject, finalDoorMax, currentCar, totalCars, entry.id),
				RESOURCE_PROVIDER));
	}

	/** 纯脚本车型（MTR3/NTE 的 base_type 写法：无 model、无贴图，车身完全由脚本绘制）。 */
	private static boolean isScriptOnly(LegacyEntry legacy) {
		return legacy.model().isEmpty() && !legacy.baseTrainType().isEmpty();
	}

	/**
	 * 第一条在已启用资源包里找不到的模型/属性文件；全部存在返回 null。
	 * <p>
	 * 不检查 {@code texture_id}：MTR3 里它只是 JonModelTrainRenderer 用的整车贴图，OBJ 车型的实际贴图来自
	 * 模型自带的 .mtl；大量资源包的 texture_id 指向别处或不存在的 png（如本包指向 made_by_clarsc/clarsc.png），
	 * 按它判定“资源缺失”会把能正常渲染的车型整条隐藏掉。
	 */
	private static String firstMissingResource(LegacyEntry legacy) {
		// MTR3 的 model 字段通常不带扩展名（实际文件是 .obj 或 .bbmodel），属性 json 也可能省略 .json
		if (!legacy.model().isEmpty() && !resourceExistsAny(legacy.model(), ".obj", ".bbmodel")) {
			return legacy.model();
		}
		if (!legacy.modelProperties().isEmpty() && !resourceExistsAny(legacy.modelProperties(), ".json")) {
			return legacy.modelProperties();
		}
		return null;
	}

	/** 给定资源路径或路径前缀，任一候选（含各扩展名）存在即视为存在。 */
	private static boolean resourceExistsAny(String resource, String... suffixes) {
		if (resourceExists(resource)) {
			return true;
		}
		for (final String suffix : suffixes) {
			if (resourceExists(resource + suffix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * MTR3 的 .mtl 只是建模工具残留：整节车厢只绑定 texture_id 那一张贴图（见 JonModelTrainRenderer），
	 * 所以包里大量 {@code map_Kd} 指向根本不存在的 png。MTR4 改为逐材质取贴图，这些引用解析失败就会显示紫黑格
	 * （如重庆3号线 crt3_6.obj 的 default 材质，2025 个面全是车厢侧面）。
	 * <p>
	 * 这里把解析不到的 {@code map_Kd} 改写为 MTR4 约定的 {@code default.png}，由
	 * {@code ModelResourceLoader} 换成车型贴图，等价于 MTR3 找不到贴图时回退到车身贴图的行为。
	 */
	private static String fixMissingMtlTextures(Identifier mtlIdentifier, String content) {
		if (content == null || content.isEmpty()) {
			return content;
		}
		final String mtlPath = mtlIdentifier.getPath();
		final int slash = mtlPath.lastIndexOf('/');
		final String baseDir = slash < 0 ? "" : mtlPath.substring(0, slash);
		final StringBuilder result = new StringBuilder(content.length());
		int replaced = 0;
		for (final String line : content.split("\n")) {
			final String trimmed = line.trim();
			if (trimmed.regionMatches(true, 0, "map_kd", 0, 6)) {
				final String texture = trimmed.substring(6).trim();
				if (!texture.isEmpty()) {
					final String path = (baseDir.isEmpty() ? texture : baseDir + "/" + texture).toLowerCase(Locale.ENGLISH);
					if (!resourceExists(mtlIdentifier.getNamespace() + ":" + path)) {
						result.append(trimmed, 0, 6).append(" default.png\n");
						replaced++;
						continue;
					}
				}
			}
			result.append(line).append('\n');
		}
		if (replaced > 0) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] {} 有 {} 处 map_Kd 指向包内不存在的贴图，已回退为车型贴图", mtlPath, replaced);
		}
		return result.toString();
	}

	private static boolean resourceExists(String resource) {
		final Minecraft client = Minecraft.getInstance();
		if (client == null || client.getResourceManager() == null) {
			return true;
		}
		final ResourceLocation identifier = ResourceLocation.tryParse(resource);
		return identifier == null || client.getResourceManager().getResource(identifier).isPresent();
	}

	/** 声明了风挡连接但未给贴图的车型，回退到 MTR 内置 sp1900 风挡（`mtr:textures/gangway/sp1900_*`）。 */
	private static String effectiveGangwayId(LegacyEntry legacy) {
		final String gangwayId = legacy.gangwayConnectionId();
		return gangwayId.isEmpty() && legacy.hasGangwayConnection() ? "mtr:textures/entity/sp1900" : gangwayId;
	}

	/** 复刻 LegacyVehicleResource.convert 的 supplier lambda：逐车过滤 parts，构造 VehicleModel 列表。 */
	private static ObjectArrayList<VehicleModel> buildModels(LegacyEntry legacy, JsonObject propertiesObject, double doorMax, int currentCar, int totalCars, String id) {
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
			processModel(legacy, currentCar, totalCars, isObj, reversed, propertiesObject.getAsJsonArray("parts"), positionDefinitionsArray, partsArray, doorMax);
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] processModel 失败: {} car={}/{}", id, currentCar, totalCars, e);
		}

		// Migrate legacy gangway & barriers
		// has_gangway_connection=true 且 gangway_connection_id 为空的车型，MTR3 里靠模型几何画风挡；
		// 该几何在 MTR4 下不再生效，故与官方修正包一致回退到内置 sp1900 风挡贴图。
		final String barrierId = legacy.trainBarrierId();
		String barrierBaseId = barrierId;
		final String gangwayId = effectiveGangwayId(legacy);
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

		// 风挡只在每节车的首个模型（车体）上挂贴图，其余模型留空。
		// MTR3 的贯通道是“每节车画一次”（用 prev↔this 一对端点，与车体模型数量无关），
		// 而 MTR4 的 RenderVehicles 按 modelIndex 逐车缓存“上一节车的端点”。legacy 的 model 字段用 %N 过滤，
		// 车头/车尾车比中间车多出 lf.obj / lb.obj，这些索引上的端点永远停留在整列车首/尾，于是画出贯穿整列车的
		// 拉伸四边形。每个车都有首个模型，故只在 index 0 画，等价于 MTR3 的每车一次；其余模型贴图为空，MTR4 会直接跳过。
		final JsonObject modelPropertiesObject = new JsonObject();
		final JsonObject gangwayModelPropertiesObject = new JsonObject();
		for (final JsonObject target : new JsonObject[]{modelPropertiesObject, gangwayModelPropertiesObject}) {
			target.addProperty("modelYOffset", 1);
			target.addProperty("barrierInnerSideResource", barrierBaseId + "_exterior.png");
			target.addProperty("barrierOuterSideResource", barrierBaseId + "_exterior.png");
			target.addProperty("barrierWidth", 2.25);
			target.addProperty("barrierHeight", 1);
			target.addProperty("barrierYOffset", 1.25);
			target.addProperty("barrierZOffset", 0.25);
			target.add("parts", partsArray);
		}
		if (hasVisibleGangway) {
			gangwayModelPropertiesObject.addProperty("gangwayInnerSideResource", gangwayConnectionBaseId + "_side.png");
			gangwayModelPropertiesObject.addProperty("gangwayInnerTopResource", gangwayConnectionBaseId + "_roof.png");
			gangwayModelPropertiesObject.addProperty("gangwayInnerBottomResource", gangwayConnectionBaseId + "_floor.png");
			gangwayModelPropertiesObject.addProperty("gangwayOuterSideResource", gangwayConnectionBaseId + "_exterior.png");
			gangwayModelPropertiesObject.addProperty("gangwayOuterTopResource", gangwayConnectionBaseId + "_exterior.png");
			gangwayModelPropertiesObject.addProperty("gangwayOuterBottomResource", gangwayConnectionBaseId + "_exterior.png");
			gangwayModelPropertiesObject.addProperty("gangwayWidth", 1.5);
			gangwayModelPropertiesObject.addProperty("gangwayHeight", 2.25);
			gangwayModelPropertiesObject.addProperty("gangwayYOffset", 1);
			gangwayModelPropertiesObject.addProperty("gangwayZOffset", 0.5);
		}

		final JsonObject positionDefinitionsObject = new JsonObject();
		positionDefinitionsObject.add("positionDefinitions", positionDefinitionsArray);
		Mtr3CompatClient.LOGGER.info("[mtr3compat] {} car={}/{} models={} parts={} posDefs={}", id, currentCar, totalCars, modelObjects.size(), partsArray.size(), positionDefinitionsArray.size());
		final ObjectArrayList<VehicleModel> models = new ObjectArrayList<>();
		for (int i = 0; i < modelObjects.size(); i++) {
			final JsonObject modelObject = modelObjects.get(i);
			final JsonObject modelPropertiesForModel = i == 0 ? gangwayModelPropertiesObject : modelPropertiesObject;
			// 注意：每节车厢必须用自己的 VehicleModel（DynamicVehicleModel 的 map 会被消费后清空）
			final VehicleModel vehicleModel = new VehicleModel(
					new JsonReader(modelObject),
					new JsonReader(modelPropertiesForModel),
					new JsonReader(positionDefinitionsObject),
					id,
					RESOURCE_PROVIDER
			);
			ModelCache.disableExpiry(vehicleModel);
			models.add(vehicleModel);
		}
		return models;
	}

	/**
	 * 旧版 OBJ 部件用 “文件.obj/组名” 指定几何所在的 OBJ 组（NTE 写法，允许一个属性文件跨多个 OBJ）；
	 * MTR4 的 OBJ 模型按单个组名索引几何，不带文件名前缀，故这里只保留组名。
	 */
	private static String legacyObjPartName(String name, boolean isObj) {
		if (!isObj) {
			return name;
		}
		final int index = name.lastIndexOf(".obj/");
		return index < 0 ? name : name.substring(index + ".obj/".length());
	}

	/** 复刻 LegacyVehicleResource.processModel（MTR3 只有单一 "parts" 数组，无 override）。 */
	private static void processModel(LegacyEntry legacy, int currentCar, int totalCars, boolean isObj, boolean reversed, JsonArray propertiesPartsArray, JsonArray positionDefinitionsArray, JsonArray partsArray, double doorMax) {
		propertiesPartsArray.forEach(jsonElement -> {
			final JsonObject propertiesPartsObject = jsonElement.getAsJsonObject();
			final String[] whitelistedCarsFilters = tryGet(propertiesPartsObject, "whitelisted_cars").split(",");
			final String[] blacklistedCarsFilters = tryGet(propertiesPartsObject, "blacklisted_cars").split(",");

			if (matchesFilter(blacklistedCarsFilters, currentCar, totalCars) <= matchesFilter(whitelistedCarsFilters, currentCar, totalCars)) {
				final JsonObject partsObject = new JsonObject();
				partsArray.add(partsObject);

				addSingleArrayItem(partsObject, "names", legacyObjPartName(propertiesPartsObject.has("name")
						? tryGet(propertiesPartsObject, "name")
						: tryGet(propertiesPartsObject, "part_name"), isObj));

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
				// 镜像模型的几何已绕 Y 翻转，门动画方向随之翻转，写回 MTR4 时需要抵消。
				final double worldDoorZMultiplier = (isObj ? -1 : 1) * doorZMultiplier;
				final double writtenDoorXMultiplier = isObj && reversed ? -doorXMultiplier : doorXMultiplier;
				final double writtenDoorZMultiplier = isObj && reversed ? -worldDoorZMultiplier : worldDoorZMultiplier;
				partsObject.addProperty("doorXMultiplier", writtenDoorXMultiplier);
				partsObject.addProperty("doorZMultiplier", writtenDoorZMultiplier);
				if (writtenDoorXMultiplier != 0 || writtenDoorZMultiplier != 0) {
					// 标记旧版门部件，供 LegacyDoorMap 统一同一门洞槽位上的多层门扇绑定。
					partsObject.getAsJsonArray("names").add(LegacyDoorMap.MARKER);
				}

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
