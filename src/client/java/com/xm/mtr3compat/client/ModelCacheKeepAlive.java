package com.xm.mtr3compat.client;

import org.mtr.mod.resource.VehicleResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MTR3 原生车型的模型缓存保活。
 * MTR4 的 CachedResource 在 60 秒未访问后会丢弃数据并重建烘焙模型；
 * 这里定期刷新已出现过的 (车型, 车厢序号, 总车厢数) 缓存，避免列车离开视野后再次重建。
 *
 * 两个约束（否则会随着见过的车型/编组增多而越来越卡）：
 * 1. 保活有上限：只固定最近的若干个组合，超出的交给 MTR4 自行过期，避免显存无限增长；
 * 2. 只 touch、绝不 force：force=true 会丢弃已烘焙模型并重建，门/悬挂等依赖模型实例内
 *    平滑状态的动画会瞬间归零（表现为一帧直接开门），故刷新必须传 false。
 */
public final class ModelCacheKeepAlive {

	/** 每个组合的刷新间隔（tick）：够短于 MTR4 的 60 秒过期即可，不是重建周期。 */
	private static final int REFRESH_TICKS = 16;
	/** 最多固定多少组合（每组合都有自己的顶点缓冲）。 */
	private static final int MAX_PINNED = 28;

	private static final List<Combo> COMBOS = new ArrayList<>();
	private static final Set<String> SEEN = new HashSet<>();
	private static int ticks;
	private static int refreshIndex;

	private ModelCacheKeepAlive() {
	}

	private static final class Combo {
		private final VehicleResource resource;
		private final String id;
		private final int car;
		private final int total;

		private Combo(VehicleResource resource, String id, int car, int total) {
			this.resource = resource;
			this.id = id;
			this.car = car;
			this.total = total;
		}

		private String key() {
			return id + '#' + car + '/' + total;
		}

		/** 只刷新访问时间，force 恒为 false，避免重建烘焙模型导致门动画状态归零。 */
		private void touch() {
			try {
				resource.getCachedVehicleResource(car, total, false);
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 刷新车型缓存失败: {} car={}/{}", id, car, total, e);
			}
		}
	}

	/** 车型的某一编组组合首次生效：登记，仅用于后续保活刷新，绝不主动重建。 */
	public static void register(VehicleResource resource, String id, int car, int total) {
		if (resource == null) {
			return;
		}
		final Combo combo = new Combo(resource, id, car, total);
		if (SEEN.add(combo.key())) {
			COMBOS.add(combo);
			while (COMBOS.size() > MAX_PINNED) {
				SEEN.remove(COMBOS.remove(0).key());
			}
		}
	}

	public static void tick() {
		ticks++;

		if (!COMBOS.isEmpty() && ticks % REFRESH_TICKS == 0) {
			COMBOS.get(refreshIndex % COMBOS.size()).touch();
			refreshIndex++;
		}
	}

	public static void clear() {
		COMBOS.clear();
		SEEN.clear();
		ticks = 0;
		refreshIndex = 0;
	}
}
