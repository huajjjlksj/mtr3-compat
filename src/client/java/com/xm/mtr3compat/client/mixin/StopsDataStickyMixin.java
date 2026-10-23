package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.StopsDataCache;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JCM 的 StopsData.constructData 在“服务器返回的完整停站数据”尚未到达时会回退到
 * buildLimitedStopsData（只用 MTR4 客户端相机窗口里的线路/站点）。这份数据会随着车开出
 * 窗口而变少甚至为空，脚本 pis.js#55 拿到 stations.size() &lt;= 1 就直接填黑返回，
 * 表现就是 LCD 先正常显示、几秒后变成纯黑。
 * <p>
 * 这里让每辆车一旦拿到过完整数据就一直沿用，不再退回窗口数据。
 */
@Mixin(targets = "com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper$StopsData")
public class StopsDataStickyMixin {

	private static final Map<Long, String> LAST_LOG = new ConcurrentHashMap<>();

	@Inject(method = "constructData", at = @At("RETURN"), cancellable = true)
	private static void mtr3compat$stickyStops(VehicleScriptContext.DataFetchMode mode, VehicleExtension vehicle,
			CallbackInfoReturnable<VehicleWrapper.StopsData> cir) {
		if (vehicle == null) {
			return;
		}
		final long id = vehicle.getId();
		VehicleWrapper.StopsData result = cir.getReturnValue();
		if (result != null && result.isFullData && !result.allStops.isEmpty()) {
			StopsDataCache.put(id, result);
		} else {
			final VehicleWrapper.StopsData cached = StopsDataCache.get(id);
			if (cached != null) {
				result = cached;
				cir.setReturnValue(cached);
			}
		}
		if (result != null) {
			com.xm.mtr3compat.client.ante.Mtr3StationExitCompat.ensureStops(result);
			final String signature = result.isFullData + "/" + result.allStops.size() + "/" + result.routeToRun.size();
			if (!signature.equals(LAST_LOG.put(id, signature))) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] stops#{} full={} stops={} routes={}",
						id, result.isFullData, result.allStops.size(), result.routeToRun.size());
			}
		}
	}
}
