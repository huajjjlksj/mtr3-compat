package com.xm.mtr3compat.client;

import com.lx862.mtrscripting.mod.impl.mtr.MTRDatasetHolder;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleDataCache;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.SimplifiedRoutePlatform;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.core.operation.DataRequest;
import org.mtr.core.operation.DataResponse;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.mapper.MinecraftClientHelper;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketRequestData;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NTE/ANTE 全局 "MTRClientData" 的兼容替身。
 * NTE 时代脚本通过 MTRClientData.DATA_CACHE.<field> 读取客户端数据派生映射（MTR3 org.mtr.client.ClientData.DATA_CACHE），
 * JCM 绑定的 MTRClientData 是 MTR4 MinecraftClientData 原类，没有 DATA_CACHE，脚本会拿到 undefined 后在 entrySet() 处抛错。
 * 本类镜像 MTR3 DataCache/ClientCache 的全部公开字段；数据由 {@link com.xm.mtr3compat.client.mixin.ClientDataSyncMixin}
 * 在 ClientData.sync() 尾部调用 {@link #rebuild()} 刷新（跟随数据同步，不做每帧重建）。
 * 静态方法 getInstance/getDashboardInstance/reset 转发到真实 MinecraftClientData，保证其他用法不变。
 */
public final class MtrClientDataCompat {

	/** 脚本以 MTRClientData.DATA_CACHE.xxx 字段访问，字段全部公开。 */
	public static final DataCache DATA_CACHE = new DataCache();

	private MtrClientDataCompat() {
	}

	public static MinecraftClientData getInstance() {
		return MinecraftClientData.getInstance();
	}

	public static MinecraftClientData getDashboardInstance() {
		return MinecraftClientData.getDashboardInstance();
	}

	public static void reset() {
		MinecraftClientData.reset();
	}

	/** MTR3 DataCache + ClientCache 公开字段集。id 直查映射直接引用 MTR4 的活映射；派生映射在 rebuild 时整体替换。 */
	public static final class DataCache {
		/** Map<Long, Station> */
		public volatile Map<Long, Station> stationIdMap = new HashMap<>();
		/** Map<Long, Platform> */
		public volatile Map<Long, Platform> platformIdMap = new HashMap<>();
		/** Map<Long, Siding> */
		public volatile Map<Long, Siding> sidingIdMap = new HashMap<>();
		/** Map<Long, SimplifiedRoute>（NTE 脚本里 Stop.route 即此类） */
		public volatile Map<Long, ?> routeIdMap = new HashMap<>();
		/** Map<Long, Depot> */
		public volatile Map<Long, Depot> depotIdMap = new HashMap<>();
		/** Map<Long, Lift>（MTR3 liftsServerIdMap/liftsClientIdMap 的近似） */
		public volatile Map<Long, ?> liftsServerIdMap = new HashMap<>();
		public volatile Map<Long, ?> liftsClientIdMap = new HashMap<>();
		/** Map<Long(platformId), Station> */
		public volatile Map<Long, Station> platformIdToStation = new HashMap<>();
		/** Map<Long(sidingId), Depot> */
		public volatile Map<Long, Depot> sidingIdToDepot = new HashMap<>();
		/** Map<Long(routeId), Depot> */
		public volatile Map<Long, Depot> routeIdToOneDepot = new HashMap<>();
		/** Map<Station, Set<Station>> 键为 Station 实例（MTR3 语义），miss 返回空集 */
		public volatile Map<Station, Set<Station>> stationIdToConnectingStations = connectingMap();
		/** Map<Long(stationId), Map<Integer(routeColor), RouteInterchange(color, name||前段)>> */
		public volatile Map<Long, Map<Integer, VehicleWrapper.Stop.RouteInterchange>> stationIdToRoutes = new HashMap<>();
		/** MTR4 无对应世界扫描缓存，给空表防止脚本 undefined 崩溃。 */
		public final Map<Object, Station> blockPosToStation = new HashMap<>();
		public final Map<Long, Long> blockPosToPlatformId = new HashMap<>();
		public final Map<Long, Object> platformConnections = new HashMap<>();
	}

	/**
	 * 已知车厂。MTR4 的 DataResponse.write() 会按请求里的 depotsToKeep 把客户端车厂裁剪成本次范围内的集合，
	 * 而车厂离玩家通常很远，附近窗口永远不带车厂，脚本的 getDepot 就会拿到 undefined。
	 * 这里把见过的车厂留下来，由 {@link com.xm.mtr3compat.client.mixin.DataResponseStickyDepotMixin}
	 * 在 write() 结束后加回客户端数据。
	 */
	private static final Map<Long, Depot> knownDepots = new HashMap<>();
	/**
	 * 持久的“侧线 id -> 车厂”映射。MTR4 的 Data.sync() 会先清空车厂的 savedRails，
	 * 再用客户端现有（附近窗口内的）侧线重新填充，所以这个关系不能只靠 data.depots 推导，
	 * 必须自己累积，否则侧线被裁剪后映射会丢。
	 */
	private static final Map<Long, Depot> accumulatedSidingToDepot = new HashMap<>();
	/** 上一次 rebuild 得到的真实“侧线 id -> 车厂”，用于判断哪些侧线还缺车厂数据。 */
	private static volatile Map<Long, Depot> lastSidingIdToDepot = new HashMap<>();
	/** 上一次处理的客户端数据集实例，reset() 换新实例时清空累积数据。 */
	private static MinecraftClientData lastClientData;
	/** 车厂数据尚未同步时的占位车厂，保证脚本 getDepot(...).name 不会因 undefined 崩溃。 */
	private static Depot fallbackDepot;

	private static Depot getFallbackDepot() {
		if (fallbackDepot == null) {
			final MinecraftClientData data = MinecraftClientData.getInstance();
			if (data == null) {
				return null;
			}
			fallbackDepot = new Depot(TransportMode.TRAIN, data);
			fallbackDepot.setName("车厂|Depot");
		}
		return fallbackDepot;
	}
	/** 每条侧线已请求车厂数据的次数与上次请求时间，用于失败重试。 */
	private static final Map<Long, Integer> depotRequestAttempts = new HashMap<>();
	private static final Map<Long, Long> depotRequestTimes = new HashMap<>();
	private static final int MAX_DEPOT_REQUEST_ATTEMPTS = 5;
	private static volatile long lastDepotRequestMillis = 0L;
	private static volatile String lastLogSignature = "";

	/**
	 * 由 mixin 在 DataResponse.write() 写入客户端数据之前调用。此时响应里的车厂对象仍带着服务端的完整
	 * savedRails 列表；Data.sync() 里的 mapAreasAndSavedRails 随后会把它清空并只用附近侧线重填，
	 * 所以“侧线 -> 车厂”必须在这里建立。
	 */
	public static void captureIncomingDepots(DataResponse response) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		if (data == null) {
			return;
		}
		resetAccumulationIfNeeded(data);
		for (final Depot depot : responseDepots(response)) {
			if (knownDepots.put(depot.getId(), depot) == null) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] depot captured: name={} id={} sidings={}",
						depot.getName(), depot.getId(), depot.savedRails.size());
			}
			for (final Siding siding : depot.savedRails) {
				accumulatedSidingToDepot.put(siding.getId(), depot);
			}
		}
	}

	/**
	 * DataResponse 的 depots 字段声明在父类 DataResponseSchema 中（mixin 的 @Shadow 定位不到，
	 * 且字段类型是 MTR 重定位后的 fastutil 类），这里用一次性反射拿到。
	 */
	private static Field responseDepotsField;

	@SuppressWarnings("unchecked")
	private static List<Depot> responseDepots(DataResponse response) {
		try {
			Field field = responseDepotsField;
			if (field == null) {
				Class<?> type = response.getClass();
				while (type != null && field == null) {
					try {
						field = type.getDeclaredField("depots");
						field.setAccessible(true);
					} catch (NoSuchFieldException e) {
						type = type.getSuperclass();
					}
				}
				responseDepotsField = field;
			}
			if (field == null) {
				return java.util.Collections.emptyList();
			}
			return (List<Depot>) field.get(response);
		} catch (Throwable t) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] read response depots failed", t);
			return java.util.Collections.emptyList();
		}
	}

	/**
	 * 由 mixin 在 DataResponse.write() 返回时调用：此时本次响应已经 addAll 并按 depotsToKeep 裁剪完毕。
	 * 先把客户端现有车厂记入 knownDepots，再把被裁掉但已知的车厂加回去，最后重新 sync()，
	 * 让 siding.area 关联恢复。
	 */
	public static void captureAndRestoreDepots(MinecraftClientData data) {
		resetAccumulationIfNeeded(data);
		boolean changed = false;
		final Set<Long> present = new HashSet<>();
		for (final Depot depot : data.depots) {
			present.add(depot.getId());
			knownDepots.putIfAbsent(depot.getId(), depot);
		}
		for (final Depot depot : knownDepots.values()) {
			if (!present.contains(depot.getId())) {
				data.depots.add(depot);
				for (final Siding siding : depot.savedRails) {
					accumulatedSidingToDepot.put(siding.getId(), depot);
				}
				changed = true;
			}
		}
		if (changed) {
			// 重新 sync 让刚同步下来的侧线与（被加回的）车厂重新建立 siding.area 关联
			data.sync();
		}
	}

	/** 存档/维度切换时 MinecraftClientData.reset() 会换新实例，累积数据必须跟着清空。 */
	private static void resetAccumulationIfNeeded(MinecraftClientData data) {
		if (data != lastClientData) {
			lastClientData = data;
			knownDepots.clear();
			accumulatedSidingToDepot.clear();
			lastSidingIdToDepot = new HashMap<>();
			depotRequestAttempts.clear();
			depotRequestTimes.clear();
			lastLogSignature = "";
		}
	}

	/**
	 * 每帧由客户端 tick 调用。服务端 DataRequest.getData 会用 Depot.inArea(clientPosition, requestRadius)
	 * 决定是否把车厂放进响应，而玩家附近的请求永远覆盖不到车厂。
	 * 这里对还缺车厂映射的侧线，以侧线自身中点为圆心定向请求一次（半径 1024，侧线必然落在所属车厂的角点盒内），
	 * 并在同一 tick 补一个与 MTR 自身一致的“相机位置 + 视距”请求，把服务端的跟踪位置/半径复位
	 * （DataRequest.getData 会调用 Client.setPositionAndUpdateRadius）。
	 * 车厂拿到一次后由 mixin 的粘性机制保留，不再随裁剪丢失。
	 */
	public static void requestDepotDataIfNeeded() {
		final MTRDatasetHolder dataset = VehicleDataCache.mtrData;
		if (dataset == null || dataset.sidings.isEmpty()) {
			return;
		}
		final long now = System.currentTimeMillis();
		if (now - lastDepotRequestMillis < 500L) {
			return;
		}
		Siding target = null;
		for (final Siding siding : dataset.sidings) {
			final long id = siding.getId();
			if (lastSidingIdToDepot.containsKey(id)) {
				continue;
			}
			final int attempts = depotRequestAttempts.getOrDefault(id, 0);
			final long lastAttempt = depotRequestTimes.getOrDefault(id, 0L);
			if (attempts < MAX_DEPOT_REQUEST_ATTEMPTS && now - lastAttempt > 5_000L) {
				target = siding;
				break;
			}
		}
		if (target == null) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		final ClientPlayerEntity player = client == null ? null : client.getPlayerMapped();
		if (player == null || client.getWorldMapped() == null) {
			return;
		}
		lastDepotRequestMillis = now;
		depotRequestAttempts.put(target.getId(), depotRequestAttempts.getOrDefault(target.getId(), 0) + 1);
		depotRequestTimes.put(target.getId(), now);
		try {
			final DataRequest depotRequest = new DataRequest(player.getUuid(), target.getMidPosition(), 1024L);
			depotRequest.writeExistingIds(MinecraftClientData.getInstance());
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketRequestData(depotRequest));

			final Position cameraPosition = Init.blockPosToPosition(client.getGameRendererMapped().getCamera().getBlockPos());
			final DataRequest restoreRequest = new DataRequest(player.getUuid(), cameraPosition, (long) MinecraftClientHelper.getRenderDistance() * 16L);
			restoreRequest.writeExistingIds(MinecraftClientData.getInstance());
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketRequestData(restoreRequest));
		} catch (Throwable t) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] request depot data failed for siding {}", target.getId(), t);
		}
	}

	/** 数据集尺寸签名，用于在 JCM 数据集加载/切换存档后触发重建。 */
	private static volatile String lastDatasetSignature = "";

	/** 每帧由客户端 tick 调用：定向请求车厂数据，并在 JCM 数据集变化时重建 DATA_CACHE。 */
	public static void tickEnsureData() {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		if (data != null) {
			resetAccumulationIfNeeded(data);
		}
		requestDepotDataIfNeeded();
		final MTRDatasetHolder dataset = VehicleDataCache.mtrData;
		final String signature = (dataset == null ? -1 : dataset.stations.size()) + ":" + (dataset == null ? -1 : dataset.platforms.size())
				+ ":" + (dataset == null ? -1 : dataset.routes.size()) + ":" + (dataset == null ? -1 : dataset.sidings.size());
		if (!signature.equals(lastDatasetSignature)) {
			lastDatasetSignature = signature;
			rebuild();
		}
	}

	/** 在 ClientData.sync() 之后重建派生映射。仅主线程调用。 */
	public static void rebuild() {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final MTRDatasetHolder dataset = VehicleDataCache.mtrData;
		final boolean hasDataset = dataset != null && !dataset.stations.isEmpty();
		if (data == null && !hasDataset) {
			return;
		}

		final Map<Long, Station> platformIdToStation = new HashMap<>();
		final Map<Long, Depot> routeIdToOneDepot = new HashMap<>();
		final Map<Long, Depot> depotById = new HashMap<>();
		int trains = 0;
		int clientStations = 0;
		if (data != null) {
			trains = data.vehicles.size();
			clientStations = data.stations.size();
			DATA_CACHE.liftsServerIdMap = data.liftIdMap;
			DATA_CACHE.liftsClientIdMap = data.liftIdMap;
			for (final Platform platform : data.platforms) {
				if (platform.area != null) {
					platformIdToStation.put(platform.getId(), platform.area);
				}
			}
			// 车厂只存在于 MTR 客户端数据里（JCM 数据集不含车厂）。mixin 会把已知车厂保留在 data.depots 中，
			// 侧线关系则累积在 accumulatedSidingToDepot 里（Data.sync 会清空车厂的 savedRails，不能只靠它推导）。
			for (final Depot depot : data.depots) {
				depotById.put(depot.getId(), depot);
				for (final Siding siding : depot.savedRails) {
					accumulatedSidingToDepot.put(siding.getId(), depot);
				}
			}
			for (final Siding siding : data.sidings) {
				if (siding.area != null) {
					accumulatedSidingToDepot.put(siding.getId(), siding.area);
					depotById.put(siding.area.getId(), siding.area);
				}
			}
			for (final Route route : data.routes) {
				if (!route.depots.isEmpty()) {
					routeIdToOneDepot.put(route.getId(), route.depots.get(0));
				}
			}
		}
		lastSidingIdToDepot = new HashMap<>(accumulatedSidingToDepot);
		DATA_CACHE.depotIdMap = depotById;
		final Map<Long, Depot> sidingIdToDepot = new HashMap<>(accumulatedSidingToDepot);
		if (hasDataset) {
			// 车厂数据暂时拿不到时给占位车厂，避免脚本 getDepot(...).name 抛异常把整块 LCD 画不出来；
			// 真实车厂同步下来后会覆盖这里的占位值。
			final Depot fallback = getFallbackDepot();
			if (fallback != null) {
				for (final Siding siding : dataset.sidings) {
					sidingIdToDepot.putIfAbsent(siding.getId(), fallback);
				}
			}
		}

		final Map<Long, Map<Integer, VehicleWrapper.Stop.RouteInterchange>> stationIdToRoutes = new HashMap<>();
		// MTR3 语义：每个车站都有连接站条目（可为空集）。MTR4 增量同步下远处车站可能缺席，
		// 查不到时返回空集，避免脚本对结果直接 forEach 抛 "undefined" 中断渲染。
		final ConnectingMap connecting = connectingMap();

		if (hasDataset) {
			// 脚本里的 Stop.station/platform/siding 都是这个数据集的实例（VehicleDataCache.buildVehicleStopsData 直接取它），
			// 所以 id 直查映射和 Station 作键的映射都必须用这里的实例，否则脚本用 == 比较时永远匹配不上。
			dataset.generateIdMapCache();
			DATA_CACHE.stationIdMap = dataset.stationIdMap;
			DATA_CACHE.platformIdMap = dataset.platformIdMap;
			DATA_CACHE.sidingIdMap = dataset.sidingIdMap;
			DATA_CACHE.routeIdMap = dataset.routeIdMap;

			for (final SimplifiedRoute route : dataset.routes) {
				final String routeName = route.getName().split("\\|\\|")[0];
				for (final SimplifiedRoutePlatform routePlatform : route.getPlatforms()) {
					final Station station = dataset.stationIdMap.get(routePlatform.getStationId());
					if (station == null) {
						continue;
					}
					stationIdToRoutes.computeIfAbsent(station.getId(), k -> new HashMap<>())
							.putIfAbsent(route.getColor(), new VehicleWrapper.Stop.RouteInterchange(route.getColor(), routeName));
					platformIdToStation.putIfAbsent(routePlatform.getPlatformId(), station);
				}
			}

			final Map<String, Station> nameToStation = new HashMap<>();
			for (final Station station : dataset.stations) {
				if (station.getName() != null) {
					nameToStation.putIfAbsent(station.getName(), station);
				}
			}

			// 每个车站都放一个条目（哪怕为空集）：脚本直接对查到的集合调用 forEach，缺失条目会抛 TypeError。
			// 键必须用 stationIdMap 的规范实例：dataset.stations 里同一 id 可能有多个实例，
			// 直接遍历 put 会让 HashMap（按 id 相等）只保留最后写入的键，脚本传入其它实例时 == 匹配不上。
			for (final Station station : dataset.stationIdMap.values()) {
				connecting.put(station, new HashSet<>());
			}
			dataset.connectingStationInterchangeMap.forEach((stationId, nameToInterchanges) -> {
				final Station station = dataset.stationIdMap.get(stationId);
				if (station == null) {
					return;
				}
				final Set<Station> set = connecting.get(station);
				for (final String otherName : nameToInterchanges.keySet()) {
					final Station other = nameToStation.get(otherName);
					if (other != null && other != station) {
						set.add(other);
					}
				}
			});

			// 再并上 MTR 客户端同步来的 connectedStations（按 id 映射回数据集实例，保证实例一致）
			if (data != null) {
				for (final Station station : dataset.stations) {
					final Station source = data.stationIdMap.get(station.getId());
					if (source == null) {
						continue;
					}
					final Set<Station> set = connecting.get(station);
					for (final Station other : source.connectedStations) {
						final Station mapped = dataset.stationIdMap.get(other.getId());
						if (mapped != null && mapped != station) {
							set.add(mapped);
						}
					}
				}
				// 脚本的 getMapValueByKey 遍历 entrySet 用 == 比较键（Rhino 的 == 不调 equals），
				// 而脚本传入的 station 是客户端实例（Stop.station 来自 MinecraftClientData.stationIdMap），
				// 表键却是数据集实例 → 永不命中 → 返回 undefined → forEach 崩溃、LCD/走字屏整块空白。
				// 这里为客户端实例镜像一组条目键，指向与数据集键同一个集合。
				for (final Station clientStation : data.stations) {
					final Station datasetStation = dataset.stationIdMap.get(clientStation.getId());
					if (datasetStation != null) {
						connecting.mirror(clientStation, connecting.get(datasetStation));
					}
				}
			}
			// dataset.stations 里的非规范重复实例（同 id 不同对象）也必须逐个镜像：
			// 脚本的 == 是身份比较，任何一份实例都可能是 Stop.station 的来源。
			for (final Station station : dataset.stations) {
				final Station canonical = dataset.stationIdMap.get(station.getId());
				if (canonical != null && station != canonical) {
					connecting.mirror(station, connecting.get(canonical));
				}
			}
		} else if (data != null) {
			DATA_CACHE.stationIdMap = data.stationIdMap;
			DATA_CACHE.platformIdMap = data.platformIdMap;
			DATA_CACHE.sidingIdMap = data.sidingIdMap;
			DATA_CACHE.routeIdMap = data.routeIdMap;
			for (final Route route : data.routes) {
				if (route.getHidden()) {
					continue;
				}
				for (final RoutePlatformData routePlatform : route.getRoutePlatforms()) {
					final Platform platform = routePlatform.getPlatform();
					if (platform == null || platform.area == null) {
						continue;
					}
					stationIdToRoutes.computeIfAbsent(platform.area.getId(), k -> new HashMap<>())
							.put(route.getColor(), new VehicleWrapper.Stop.RouteInterchange(route.getColor(), route.getName().split("\\|\\|")[0]));
				}
			}
			for (final Station station : data.stations) {
				connecting.put(station, station.connectedStations);
			}
		}

		DATA_CACHE.platformIdToStation = platformIdToStation;
		DATA_CACHE.sidingIdToDepot = sidingIdToDepot;
		DATA_CACHE.routeIdToOneDepot = routeIdToOneDepot;
		DATA_CACHE.stationIdToRoutes = stationIdToRoutes;
		DATA_CACHE.stationIdToConnectingStations = connecting;

		final String signature = (hasDataset ? dataset.stations.size() + "/" + dataset.platforms.size() + "/" + dataset.sidings.size() + "/" + dataset.routes.size() : "-")
				+ "|" + clientStations + "/" + knownDepots.size() + "/" + trains + "|" + accumulatedSidingToDepot.size();
		if (!signature.equals(lastLogSignature)) {
			lastLogSignature = signature;
			Mtr3CompatClient.LOGGER.info("[mtr3compat] DATA_CACHE: dataset(stations={} platforms={} sidings={} routes={}) client(stations={} depots={} trains={}) | stationIdToRoutes={} connecting={} sidingIdToDepot={} (real={})",
					hasDataset ? dataset.stations.size() : 0, hasDataset ? dataset.platforms.size() : 0,
					hasDataset ? dataset.sidings.size() : 0, hasDataset ? dataset.routes.size() : 0,
					clientStations, knownDepots.size(), trains, stationIdToRoutes.size(), connecting.size(), sidingIdToDepot.size(), accumulatedSidingToDepot.size());
		}
	}

	/** MTR3 语义连接站表工厂（DATA_CACHE 字段初值与 rebuild 共用）。 */
	static ConnectingMap connectingMap() {
		return new ConnectingMap();
	}

	/**
	 * MTR3 语义的 Map&lt;Station, Set&lt;Station&gt;&gt;：
	 * - get/containsKey 对 miss 返回空集/true（MTR3 每个车站都有键，值可为空集）；
	 * - entrySet 额外包含 {@link #mirror} 登记的客户端实例键条目 —— 脚本的 getMapValueByKey
	 *   遍历 entrySet 用 == 比较键（Rhino 的 == 不调用 equals），必须让客户端 Station 实例也能命中。
	 */
	static final class ConnectingMap extends HashMap<Station, Set<Station>> {
		private final List<Map.Entry<Station, Set<Station>>> mirrors = new ArrayList<>();

		/** 以客户端实例为键镜像一个条目，值与数据集键指向同一集合。 */
		void mirror(Station clientStation, Set<Station> value) {
			mirrors.add(new AbstractMap.SimpleImmutableEntry<>(clientStation, value));
		}

		@Override
		public Set<Station> get(Object key) {
			final Set<Station> value = super.get(key);
			return value != null ? value : Collections.emptySet();
		}

		@Override
		public boolean containsKey(Object key) {
			return true;
		}

		@Override
		public Set<Map.Entry<Station, Set<Station>>> entrySet() {
			if (mirrors.isEmpty()) {
				return super.entrySet();
			}
			// 必须用身份去重：StationEqualsMixin 使按 id 相等的键的条目互相 equals，
			// 普通 Set 会把镜像条目当作重复项丢弃，客户端实例键就又查不到了。
			final Set<Map.Entry<Station, Set<Station>>> combined = Collections.newSetFromMap(new IdentityHashMap<>());
			combined.addAll(super.entrySet());
			combined.addAll(mirrors);
			return combined;
		}
	}
}
