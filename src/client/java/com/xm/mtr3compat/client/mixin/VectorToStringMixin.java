package com.xm.mtr3compat.client.mixin;

import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Mixin;

/**
 * MTR3 的 Vector 有 toString()（形如 "(x, y, z)"），MTR4 删掉了。
 * NTE 脚本用 vec3.toString() + 字符串解析来取坐标（conn.js#121 toVector3f，源自 rail.getPosition），
 * 缺 toString 会拿到 Object 默认的 "类名@hash"，解析成 NaN，贯通道方向/偏航补偿全错。
 */
@Mixin(Vector.class)
public class VectorToStringMixin {

	@Override
	public String toString() {
		final Vector vector = (Vector) (Object) this;
		return "(" + vector.x + ", " + vector.y + ", " + vector.z + ")";
	}
}
