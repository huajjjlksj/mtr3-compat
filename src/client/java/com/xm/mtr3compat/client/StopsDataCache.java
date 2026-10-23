package com.xm.mtr3compat.client;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每辆车最近一次拿到的“完整停站数据”。
 * JCM 在完整数据未就绪时会回退到窗口数据：stop 列表长度不同、distance 全为 -1，
 * 此时 findNextStopIndex 会直接返回 size，取下标即“终点站”，与完整数据帧交替出现就是闪烁。
 * 一旦拿到完整数据，这里长期保留，供 getStops 统一返回，保证下标与站名同源。
 */
public final class StopsDataCache {

	private static final Map<Long, VehicleWrapper.StopsData> STICKY = new ConcurrentHashMap<>();

	private StopsDataCache() {
	}

	public static void put(long id, VehicleWrapper.StopsData data) {
		STICKY.put(id, data);
	}

	public static VehicleWrapper.StopsData get(long id) {
		return STICKY.get(id);
	}
}
