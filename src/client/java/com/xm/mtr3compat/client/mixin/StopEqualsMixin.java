package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Station;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * JCM 在构建站点数据时，会把“与上一站同一站台”的 Stop 从 allStops 中省略（线路换乘/折返），
 * 只挂在对应线路的 Stop 列表上。NTE 脚本大量使用
 * train.getAllPlatforms().indexOf(train.getThisRoutePlatforms().get(0))，
 * 依赖 Stop 能按站台相等，否则 indexOf 得到 -1 后取上一站直接越界。
 * 这里给 Stop 补上“同站台即相等”的语义。
 */
@Mixin(VehicleWrapper.Stop.class)
public class StopEqualsMixin {

	@Shadow
	public Platform platform;

	@Shadow
	public Station station;

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof VehicleWrapper.Stop)) {
			return false;
		}
		final VehicleWrapper.Stop other = (VehicleWrapper.Stop) obj;
		if (platform != null && other.platform != null) {
			return platform.getId() == other.platform.getId();
		}
		if (platform == null && other.platform == null && station != null && other.station != null) {
			return station.getId() == other.station.getId();
		}
		return false;
	}

	@Override
	public int hashCode() {
		if (platform != null) {
			return Long.hashCode(platform.getId());
		}
		if (station != null) {
			return Long.hashCode(station.getId());
		}
		return 0;
	}
}
