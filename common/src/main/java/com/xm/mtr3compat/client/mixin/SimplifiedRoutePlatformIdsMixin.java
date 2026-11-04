package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.jcm.Mtr3RoutePlatformIds;
import org.mtr.core.data.SimplifiedRoute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * JCM 的 {@code Stop.route} 是 MTR4 的 SimplifiedRoute（只有 getPlatforms()），
 * 而 MTR3 脚本按 {@code route.platformIds} 读取站台。
 * <p>
 * 这里补一个公有 getter：Rhino 对 Java 对象的属性访问本来就走 bean getter
 * （脚本里的 {@code route.name} / {@code route.id} 即 getName() / getId()），
 * 因此 {@code route.platformIds} 会命中 getPlatformIds()，且永远返回非 null。
 * <p>
 * 不能用「合并公有字段 + 构造器 TAIL 注入初始化」：Fabric 的打包重映射会把
 * {@code method = "<init>*"} 展开成带描述的单一构造器（如 {@code <init>(Route)}），
 * 经 ReaderBase 反序列化出来的实例不会执行注入，字段恒为 null
 * （脚本报 {@code Cannot call method "size" of null}）；Forge 打包保留通配符所以两端表现不一致。
 */
@Mixin(value = SimplifiedRoute.class, remap = false)
public class SimplifiedRoutePlatformIdsMixin {

	@Unique
	private Mtr3RoutePlatformIds mtr3compat$platformIds;

	public Mtr3RoutePlatformIds getPlatformIds() {
		if (mtr3compat$platformIds == null) {
			mtr3compat$platformIds = new Mtr3RoutePlatformIds((Object) this);
		}
		return mtr3compat$platformIds;
	}
}
