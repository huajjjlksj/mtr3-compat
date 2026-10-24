package com.xm.mtr3compat.client;

/**
 * MTR3 的 rail.getPosition() 返回 Vec3d，其 toString() 是 "(x, y, z)"；
 * 大连脚本正是靠 toString 再 parseFloat 取值。MTR4 的 Vector 没有这种 toString，
 * 这里给出等价实现。
 */
public final class LegacyVector {

	private final double x;
	private final double y;
	private final double z;

	public LegacyVector(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
