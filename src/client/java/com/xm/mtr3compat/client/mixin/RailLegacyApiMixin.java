package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.LegacyRailType;
import com.xm.mtr3compat.client.LegacyVector;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * MTR3 脚本 API 兼容：MTR3 的 Rail 上有 getLength()、getPosition(double)、railType，
 * MTR4 全部移除（改为 railMath 内部实现）。MTR3 资源包脚本（如大连 MMI 屏）依赖这三个，
 * 缺一个就在 render/update 里抛异常，整车脚本绘制因此中止、车门 LCD 等全部不显示。
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailLegacyApiMixin {

	@Shadow
	@Final
	public RailMath railMath;

	public double getLength() {
		return railMath.getLength();
	}

	public Object getPosition(double value) {
		final Vector vector = railMath.getPosition(value, false);
		return new LegacyVector(vector.x, vector.y, vector.z);
	}

	public LegacyRailType getRailType() {
		final Rail rail = (Rail) (Object) this;
		return LegacyRailType.of(rail, (int) Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true)));
	}
}
