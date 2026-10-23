package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.ante.Mtr3StationExits;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.core.data.StationExit;
import org.mtr.core.generated.data.StationSchema;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR4 的 Station.exits 是 List&lt;StationExit&gt;（字段声明在 StationSchema，final），
 * NTE 脚本却按 MTR3 的 Map 用：station.exits.entrySet()（西安包 pis_config_parser.js#6），
 * Rhino 读 Java 对象属性走的是字段本身，所以必须替换字段实例而不是加 getter。
 * 这里在 StationSchema 构造后把 exits 换成同类型子类 Mtr3StationExits（额外带 entrySet()），
 * MTR 内部后续的增删改仍作用在同一对象上，出口数据不会丢。
 */
@Mixin(StationSchema.class)
public class StationExitsCompatMixin {

	@Shadow
	@Final
	@Mutable
	protected ObjectArrayList<StationExit> exits;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mtr3compat$replaceExits(CallbackInfo ci) {
		if (exits == null || exits instanceof Mtr3StationExits) {
			return;
		}
		final Mtr3StationExits replacement = new Mtr3StationExits();
		replacement.addAll(exits);
		exits = replacement;
		Mtr3CompatClient.LOGGER.info("[mtr3compat] Station.exits 已升级为 MTR3 Map 语义, exits=" + replacement.size());
	}
}
