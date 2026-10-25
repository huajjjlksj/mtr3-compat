package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.LegacyRailType;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Mixin;

/**
 * MTR4 把“轨道长度 / 沿线取点”下沉到了 PathData 与 RailMath，Rail 自身不再暴露。
 * NTE 脚本仍按 MTR3 写法调用 pathDatas[i].rail.getLength() 与 rail.getPosition(progress)，
 * 缺方法会让整条 script_files 的 render 抛异常，
 * 结果车模之后绘制的受电弓、LCD 全部不出现。这里按 MTR4 官方语义补回：
 * PathData.getRailLength() 内部就是 railMath.getLength()，PathData.getPosition(d) 就是 railMath.getPosition(d, reverse)。
 */
@Mixin(Rail.class)
public class RailCompatMixin {

	public double getLength() {
		return ((Rail) (Object) this).railMath.getLength();
	}

	public Vector getPosition(double progress) {
		return ((Rail) (Object) this).railMath.getPosition(progress, false);
	}

	/** MTR3 的 Rail.railType.speedLimit；部分脚本每帧读它，缺失会让整段脚本抛异常。 */
	public LegacyRailType getRailType() {
		final Rail rail = (Rail) (Object) this;
		return LegacyRailType.of(rail, (int) Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true)));
	}
}
