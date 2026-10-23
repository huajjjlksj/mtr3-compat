package com.xm.mtr3compat.client.mixin;

import org.mtr.core.data.Station;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * MTR3 语义里脚本拿到的 Station 与 DATA_CACHE 映射键是同一实例（世界数据单例），
 * HashMap 身份比较即可命中；MTR4 下 JCM 的 Stop.station 与 compat 数据集的 Station
 * 是不同实例，导致 stationIdToConnectingStations 等以 Station 为键的查表 miss，
 * 脚本对结果直接 forEach 时抛 "Cannot call method forEach of undefined" 中断整个渲染。
 * 这里补按 id 的值相等（id 全局唯一，对 MTR4 内部使用也语义安全）。
 */
@Mixin(Station.class)
public class StationEqualsMixin {

	@Shadow
	public long getId() {
		throw null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Station)) {
			return false;
		}
		return getId() == ((Station) obj).getId();
	}

	@Override
	public int hashCode() {
		return Long.hashCode(getId());
	}
}
