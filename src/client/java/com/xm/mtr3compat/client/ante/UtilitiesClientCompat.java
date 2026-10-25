package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.integration.MinecraftClientWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * ANTE 全局 "UtilitiesClient" 替身（源 cn.zbx1425.mtrsteamloco.scripting.util.client.UtilitiesClientUtil）。
 * getRenderDistance 委托 JCM 的 MinecraftClientWrapper.renderDistance（内部处理 mapping 与版本差异）；
 * hasResource(id) -> 资源管理器是否含该 id（含命名空间，缺省 minecraft）。
 */
public final class UtilitiesClientCompat {

	private UtilitiesClientCompat() {
	}

	public static int getRenderDistance() {
		return MinecraftClientWrapper.renderDistance();
	}

	public static boolean hasResource(String id) {
		if (id == null) {
			return false;
		}
		final ResourceLocation loc = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
		if (loc == null) {
			return false;
		}
		final Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getResourceManager() == null) {
			return false;
		}
		try {
			mc.getResourceManager().getResource(loc);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
