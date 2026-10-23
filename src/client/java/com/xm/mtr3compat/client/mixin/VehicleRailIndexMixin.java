package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.core.data.PathData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MTR4 的 VehicleExtraData.immutablePath 会随列车状态在“单段”和“上一/本/下一线路拼接”之间切换，
 * 而 NTE 脚本（西安包 conn.js#25）只在首帧取一次 train.path() 并长期缓存。为了让脚本缓存的数组
 * 与 getRailIndex 的索引始终一致：
 * <ul>
 *   <li>getPathData() 返回每辆车引用稳定的列表，内容同步到最新快照；</li>
 *   <li>索引按 MTR4 自身语义（startDistance &lt;= progress 的最后一段）计算，并夹在
 *       “首帧长度”和“当前长度”之内，避免脚本缓存的旧数组越界读到 undefined。</li>
 * </ul>
 */
@Mixin(VehicleWrapper.class)
public class VehicleRailIndexMixin {

	@Shadow
	@Final
	private VehicleExtension vehicleExtension;

	/** 每辆车一份（脚本在独立线程执行，故用 Map + 同步列表）。 */
	private static final Map<Long, ObjectArrayList<PathData>> STABLE_PATH = new HashMap<>();
	private static final Map<Long, Integer> FIRST_SEEN_SIZE = new HashMap<>();

	private static ObjectArrayList<PathData> sync(long id, List<PathData> current) {
		ObjectArrayList<PathData> stable = STABLE_PATH.get(id);
		if (stable == null) {
			stable = new ObjectArrayList<>();
			STABLE_PATH.put(id, stable);
			final int firstSize = current == null ? 0 : current.size();
			FIRST_SEEN_SIZE.put(id, firstSize);
			if (current != null && !current.isEmpty()) {
				stable.addAll(current);
			}
			Mtr3CompatClient.LOGGER.info("[mtr3compat] path#{} 首帧分段={} 首段起点={} 首段长度={}",
					id, firstSize,
					current == null || current.isEmpty() ? -1 : current.get(0).getStartDistance(),
					current == null || current.isEmpty() ? -1 : current.get(0).getRailLength());
			return stable;
		}
		synchronized (stable) {
			if (current != null && !current.isEmpty()) {
				stable.clear();
				stable.addAll(current);
			}
			return stable;
		}
	}

	@Inject(method = "getPathData", at = @At("RETURN"), cancellable = true)
	private void mtr3compat$stablePath(CallbackInfoReturnable<List<PathData>> cir) {
		cir.setReturnValue(sync(vehicleExtension.getId(), cir.getReturnValue()));
	}

	@Inject(method = "getRailIndex", at = @At("HEAD"), cancellable = true)
	private void mtr3compat$getRailIndex(double progress, boolean fromEnd, CallbackInfoReturnable<Integer> cir) {
		final long id = vehicleExtension.getId();
		final ObjectArrayList<PathData> path = sync(id, vehicleExtension.vehicleExtraData.immutablePath);
		synchronized (path) {
			if (path.isEmpty()) {
				cir.setReturnValue(0);
				return;
			}
			final double target = progress - (fromEnd ? 1 : 0);
			int index = 0;
			for (int i = 0; i < path.size(); i++) {
				if (target >= path.get(i).getStartDistance()) {
					index = i;
				} else {
					break;
				}
			}
			final Integer firstSize = FIRST_SEEN_SIZE.get(id);
			if (firstSize != null && firstSize > 0) {
				index = Math.min(index, firstSize - 1);
			}
			cir.setReturnValue(Math.max(0, Math.min(index, path.size() - 1)));
		}
	}
}
