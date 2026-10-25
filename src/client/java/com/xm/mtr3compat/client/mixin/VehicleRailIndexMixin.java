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
 * 而 NTE 脚本只在首帧取一次 train.path()，并把它累加出的 pathLength 数组长期缓存。
 * 若兼容层每帧把列表换成新窗口，脚本缓存的 pathLength 与列表内容就会错位
 * （railIndex 指向另一段 → railValue 偏移甚至 NaN → 风挡随机左右偏移，窗口恢复后又自愈）。
 *
 * 因此：首帧之后一律冻结该列表（不再清空重填），只有“整条路径换了起点”（换线/换向重新出发）
 * 时才重建；getRailIndex 也在同一份冻结列表上取索引，保证与脚本缓存同坐标系。
 *
 * 另外：MTR4 用绝对累积距离编号轨道，停车位置常落在两段轨道交界处。若按原进度取值会取到
 * “后一段”（dwellTime 恒为 0），且停车时进度抖动会让索引在交界两侧跳变，导致脚本的
 * trainStatus 在 arrived / on_route 间抖动（LCD 开门小灯时有时无）。故统一取 progress - 1，
 * 与 MTR4 自身计算车头索引的方式一致。
 */
@Mixin(VehicleWrapper.class)
public class VehicleRailIndexMixin {

	@Shadow
	@Final
	private VehicleExtension vehicleExtension;

	/** 每辆车一份（脚本在独立线程执行，故用 Map + 同步列表）。 */
	private static final Map<Long, ObjectArrayList<PathData>> STABLE_PATH = new HashMap<>();

	/**
	 * 只冻结、不清空：脚本（贯通道 / LCD）在首帧就把分段与 pathLength 缓存下来，
	 * 之后任何“列表被替换或短暂变空”都会让它的索引错位或整块画空。
	 * 因此快照一旦建立就长期沿用，仅当列车整体退回到快照起点之前（换线/反向重新出发）才重锚。
	 */
	private static ObjectArrayList<PathData> sync(long id, List<PathData> current) {
		ObjectArrayList<PathData> stable = STABLE_PATH.get(id);
		if (current == null || current.isEmpty()) {
			return stable == null ? new ObjectArrayList<>() : stable;
		}
		if (stable == null) {
			stable = new ObjectArrayList<>();
			stable.addAll(current);
			STABLE_PATH.put(id, stable);
			Mtr3CompatClient.LOGGER.info("[mtr3compat] path#{} 首帧分段={} 首段起点={} 首段长度={}",
					id, current.size(), current.get(0).getStartDistance(), current.get(0).getRailLength());
			return stable;
		}
		synchronized (stable) {
			if (stable.isEmpty() || current.get(0).getStartDistance() < stable.get(0).getStartDistance()) {
				stable.clear();
				stable.addAll(current);
				Mtr3CompatClient.LOGGER.info("[mtr3compat] path#{} 重新出发 分段={} 首段起点={}",
						id, current.size(), current.get(0).getStartDistance());
			}
			return stable;
		}
	}

	@Inject(method = "getPathData", at = @At("RETURN"), cancellable = true)
	private void mtr3compat$stablePath(CallbackInfoReturnable<List<PathData>> cir) {
		cir.setReturnValue(sync(vehicleExtension.getId(), cir.getReturnValue()));
	}

	/**
	 * 脚本的贯通道角度必须与渲染管线用同一份进度：
	 * MTR4 渲染按 smoothedRailProgress 摆放车厢，但 JCM 的 getRailProgress() 返回服务端原始值。
	 * 行驶中原始值每次同步跳变、平滑值滞后收敛（≈1 秒），停站开门时平滑被冻结（adjustmentAmount=0），
	 * 两者之差在曲线上表现为风挡随机左右大幅偏移、停站持续、启动后才恢复。
	 */
	@Inject(method = "getRailProgress()D", at = @At("HEAD"), cancellable = true)
	private void mtr3compat$smoothedProgress(CallbackInfoReturnable<Double> cir) {
		final double smoothed = ((PersistentVehicleDataAccessor) (Object) vehicleExtension.persistentVehicleData).mtr3compat$getSmoothedRailProgress();
		if (smoothed != 0) {
			cir.setReturnValue(smoothed);
		}
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
			final double target = progress - 1;
			int index = 0;
			for (int i = 0; i < path.size(); i++) {
				if (target >= path.get(i).getStartDistance()) {
					index = i;
				} else {
					break;
				}
			}
			cir.setReturnValue(Math.max(0, Math.min(index, path.size() - 1)));
		}
	}
}
