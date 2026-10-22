package com.xm.mtr3compat.client;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.mod.client.MinecraftClientData;

import java.util.HashMap;
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
		/** Map<Long, Route> */
		public volatile Map<Long, Route> routeIdMap = new HashMap<>();
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
		/** Map<Station, Set<Station>> 键为 Station 实例（MTR3 语义） */
		public volatile Map<Station, Set<Station>> stationIdToConnectingStations = new HashMap<>();
		/** Map<Long(stationId), Map<Integer(routeColor), RouteInterchange(color, name||前段)>> */
		public volatile Map<Long, Map<Integer, VehicleWrapper.Stop.RouteInterchange>> stationIdToRoutes = new HashMap<>();
		/** MTR4 无对应世界扫描缓存，给空表防止脚本 undefined 崩溃（德水包未使用） */
		public final Map<Object, Station> blockPosToStation = new HashMap<>();
		public final Map<Long, Long> blockPosToPlatformId = new HashMap<>();
		public final Map<Long, Object> platformConnections = new HashMap<>();
	}

	/** 在 ClientData.sync() 之后重建派生映射。仅主线程调用。 */
	public static void rebuild() {
		// MinecraftClientData 本身继承 ClientData，实例即客户端数据
		final ClientData data = MinecraftClientData.getInstance();
		if (data == null) {
			return;
		}
		DATA_CACHE.stationIdMap = data.stationIdMap;
		DATA_CACHE.platformIdMap = data.platformIdMap;
		DATA_CACHE.sidingIdMap = data.sidingIdMap;
		DATA_CACHE.routeIdMap = data.routeIdMap;
		DATA_CACHE.depotIdMap = data.depotIdMap;
		DATA_CACHE.liftsServerIdMap = data.liftIdMap;
		DATA_CACHE.liftsClientIdMap = data.liftIdMap;

		final Map<Long, Station> platformIdToStation = new HashMap<>();
		for (final Platform platform : data.platforms) {
			if (platform.area != null) {
				platformIdToStation.put(platform.getId(), platform.area);
			}
		}
		DATA_CACHE.platformIdToStation = platformIdToStation;

		final Map<Long, Depot> sidingIdToDepot = new HashMap<>();
		for (final Siding siding : data.sidings) {
			if (siding.area != null) {
				sidingIdToDepot.put(siding.getId(), siding.area);
			}
		}
		DATA_CACHE.sidingIdToDepot = sidingIdToDepot;

		final Map<Long, Depot> routeIdToOneDepot = new HashMap<>();
		final Map<Station, Set<Station>> connecting = new HashMap<>();
		final Map<Long, Map<Integer, VehicleWrapper.Stop.RouteInterchange>> stationIdToRoutes = new HashMap<>();
		for (final Route route : data.routes) {
			if (!route.depots.isEmpty()) {
				routeIdToOneDepot.put(route.getId(), route.depots.get(0));
			}
			if (route.getHidden()) {
				continue; // MTR3 ClientCache 同步时跳过隐藏线路
			}
			for (final RoutePlatformData routePlatform : route.getRoutePlatforms()) {
				final Platform platform = routePlatform.getPlatform();
				if (platform == null || platform.area == null) {
					continue;
				}
				final Station station = platform.area;
				stationIdToRoutes.computeIfAbsent(station.getId(), k -> new HashMap<>())
						.put(route.getColor(), new VehicleWrapper.Stop.RouteInterchange(route.getColor(), route.getName().split("\\|\\|")[0]));
			}
		}
		DATA_CACHE.routeIdToOneDepot = routeIdToOneDepot;
		DATA_CACHE.stationIdToRoutes = stationIdToRoutes;

		for (final Station station : data.stations) {
			connecting.put(station, station.connectedStations);
		}
		DATA_CACHE.stationIdToConnectingStations = connecting;
	}
}
