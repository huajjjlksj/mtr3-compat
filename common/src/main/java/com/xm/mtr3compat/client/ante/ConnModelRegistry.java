package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.util.Matrices;
import com.lx862.mtrscripting.core.util.model.ModelJS;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * drawConnModel 的脚本侧登记表（与 {@link ConnStretchRegistry} 同构）。
 * 脚本线程在 VehicleScriptContext.drawConnModel 中写入（车辆 hexId -> 后车尾全局车厢序号 -> 模型+矩阵），
 * 渲染线程在 RenderVehicles 每节车厢回调处读取，平移到该连接处端面后绘制。
 */
public final class ConnModelRegistry {

	public static final class Entry {
		public final ModelJS model;
		public final Matrices matrices;
		public final long frame;

		Entry(ModelJS model, Matrices matrices, long frame) {
			this.model = model;
			this.matrices = matrices;
			this.frame = frame;
		}
	}

	private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Entry>> DATA = new ConcurrentHashMap<>();
	private static volatile long currentFrame = 0;

	private ConnModelRegistry() {
	}

	public static void nextFrame() {
		currentFrame++;
	}

	public static void put(String hexId, int trailingCarIndex, ModelJS model, Matrices matrices) {
		DATA.computeIfAbsent(hexId, k -> new ConcurrentHashMap<>())
				.put(trailingCarIndex, new Entry(model, matrices, currentFrame));
	}

	public static Entry get(String hexId, int trailingCarIndex) {
		Map<Integer, Entry> map = DATA.get(hexId);
		if (map == null) {
			return null;
		}
		Entry entry = map.get(trailingCarIndex);
		if (entry == null) {
			return null;
		}
		if (currentFrame - entry.frame > 1) {
			map.remove(trailingCarIndex, entry);
			return null;
		}
		return entry;
	}

	public static void prune() {
		long frame = currentFrame;
		DATA.forEach((hexId, map) -> map.entrySet().removeIf(en -> frame - en.getValue().frame > 2));
	}
}
