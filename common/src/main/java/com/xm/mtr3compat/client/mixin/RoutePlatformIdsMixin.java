package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.jcm.Mtr3RoutePlatformIds;
import org.mtr.core.data.Route;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * MTR3 脚本按 {@code route.platformIds} 读取站台列表，MTR4 只有 getRoutePlatforms()。
 * 这里补一个公有 getter，Rhino 的属性访问会命中它（同 {@code route.name} 走 getName()）。
 * <p>
 * 不用「公有字段 + 构造器注入」的原因同 {@link SimplifiedRoutePlatformIdsMixin}：
 * Fabric 打包会把 {@code "<init>*"} 展开成单一构造器签名，反序列化路径的实例字段恒为 null。
 */
@Mixin(value = Route.class, remap = false)
public class RoutePlatformIdsMixin {

	@Unique
	private Mtr3RoutePlatformIds mtr3compat$platformIds;

	public Mtr3RoutePlatformIds getPlatformIds() {
		if (mtr3compat$platformIds == null) {
			mtr3compat$platformIds = new Mtr3RoutePlatformIds((Object) this);
		}
		return mtr3compat$platformIds;
	}
}
