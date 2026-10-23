package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JCM 的 getStops() 把该车要跑的**所有**线路的站串成一个列表（日志里 stops=20 routes=2），
 * 而 getThisRouteStops() 拿“全量列表里的下一个站下标”去 stopIdxToRouteStops 里查当前线路的站，
 * 换线后下标与当前线路对不上，于是当前站永远不前进
 * （LCD 会按时间切页，但站台状态/当前站不变）。
 * <p>
 * 这里直接按 getThisRouteId() 过滤出当前线路的站，与 NTE 的 getThisRoutePlatforms 语义一致；
 * 上游的 getThisRoutePlatformsNextIndex / findNextStopIndex 都是基于 stop.distance 与
 * railProgress 计算的，换成“本站线路的站”后即可正常推进。
 */
@Mixin(VehicleWrapper.class)
public abstract class VehicleRouteStopsMixin {

	private static final Map<Long, String> LAST_LOG = new HashMap<>();

	@Shadow
	public abstract List<VehicleWrapper.Stop> getStops();

	@Shadow
	protected abstract long getThisRouteId();

	@Shadow
	public abstract double getRailProgress();

	@Inject(method = "getThisRouteStops", at = @At("HEAD"), cancellable = true)
	private void mtr3compat$thisRouteStops(CallbackInfoReturnable<List<VehicleWrapper.Stop>> cir) {
		final List<VehicleWrapper.Stop> allStops = getStops();
		if (allStops == null || allStops.isEmpty()) {
			return;
		}
		final long routeId = getThisRouteId();
		final ObjectArrayList<VehicleWrapper.Stop> filtered = new ObjectArrayList<>();
		for (final VehicleWrapper.Stop stop : allStops) {
			final SimplifiedRoute route = stop.route;
			if (route != null && route.getId() == routeId) {
				filtered.add(stop);
			}
		}
		if (filtered.isEmpty()) {
			return;
		}
		try {
			final long id = ((VehicleWrapper) (Object) this).getId();
			final StringBuilder builder = new StringBuilder();
			builder.append("stops=").append(filtered.size()).append('/').append(allStops.size())
					.append(" route=").append(routeId)
					.append(" progress=").append(String.format("%.1f", getRailProgress()));
			for (int i = 0; i < Math.min(3, filtered.size()); i++) {
				builder.append(" d").append(i).append('=').append(String.format("%.1f", filtered.get(i).distance));
			}
			final String message = builder.toString();
			if (!message.equals(LAST_LOG.put(id, message))) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] routeStops#{} {}", id, message);
			}
		} catch (Throwable ignored) {
		}
		cir.setReturnValue(filtered);
	}
}
