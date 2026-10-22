package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeJavaObject;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext;
import com.xm.mtr3compat.client.ante.ConnStretchRegistry;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 为 JCM 的 VehicleScriptContext 补上 NTE/ANTE 的 drawConnStretchTexture(texture, carIndex)。
 * JCM 2.3.0-beta.2 尚未重新实现该方法（drawConnModel 同样抛 NotImplementedException），
 * 这里把纹理登记到 ConnStretchRegistry，由 ConnStretchRenderMixin 在渲染管线中提交四边形。
 */
@Mixin(VehicleScriptContext.class)
public class VehicleScriptContextConnMixin {

	@Shadow
	private VehicleExtension vehicleExtension;

	@Shadow
	private int[] myCars;

	public void drawConnStretchTexture(Object texture, int carIndex) {
		try {
			final Identifier id = resolveTexture(texture);
			if (id == null) {
				return;
			}
			final int[] cars = this.myCars;
			if (cars == null || carIndex < 0 || carIndex + 1 >= cars.length) {
				return;
			}
			// conn carIndex 位于 cars[carIndex] 与 cars[carIndex+1] 之间；
			// 渲染管线在绘制“后车”时画该连接处（prevConnList 按后车全局序号索引）。
			final int a = cars[carIndex];
			final int b = cars[carIndex + 1];
			final int trailing;
			if (b == a + 1) {
				trailing = b;
			} else if (b == a - 1) {
				trailing = a;
			} else {
				return;
			}
			ConnStretchRegistry.put(this.vehicleExtension.getHexId(), trailing, id);
		} catch (Throwable ignored) {
		}
	}

	private static Identifier resolveTexture(Object texture) {
		Object value = texture;
		if (value instanceof NativeJavaObject) {
			value = ((NativeJavaObject) value).unwrap();
		}
		if (value instanceof Identifier) {
			return (Identifier) value;
		}
		if (value instanceof String) {
			final String s = ((String) value).replace("\\", "/");
			Identifier parsed = Identifier.tryParse(s);
			if (parsed == null && !s.contains(":")) {
				parsed = Identifier.tryParse("mtr:" + s);
			}
			return parsed;
		}
		return null;
	}
}
