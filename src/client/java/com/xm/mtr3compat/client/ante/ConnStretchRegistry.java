package com.xm.mtr3compat.client.ante;

import org.mtr.mapping.holder.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * drawConnStretchTexture 的脚本侧登记表。
 * 脚本线程在 VehicleScriptContext.drawConnStretchTexture 中写入（车辆 hexId -> 后车尾全局车厢序号 -> 纹理），
 * 渲染线程在 RenderVehicles 每节车厢回调处读取并提交四边形。
 * 帧号用于淘汰脚本不再调用的过期条目（容忍异步执行带来的 ±1 帧时序差）。
 */
public final class ConnStretchRegistry {

	public static final class Entry {
		public final Identifier texture;
		public final long frame;

		Entry(Identifier texture, long frame) {
			this.texture = texture;
			this.frame = frame;
		}
	}

	private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Entry>> DATA = new ConcurrentHashMap<>();
	private static volatile long currentFrame = 0;

	private ConnStretchRegistry() {
	}

	public static void nextFrame() {
		currentFrame++;
	}

	public static void put(String hexId, int trailingCarIndex, Identifier texture) {
		DATA.computeIfAbsent(hexId, k -> new ConcurrentHashMap<>())
				.put(trailingCarIndex, new Entry(texture, currentFrame));
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
