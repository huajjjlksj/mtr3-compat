package com.xm.mtr3compat.client.ante;

import net.minecraft.network.chat.Component;

/**
 * ANTE 全局 "ComponentUtil" 替身。
 * ANTE 源码经 mtr.mappings.Text 转 mapping；本环境是 mojmap Fabric，直接用原生 Component 工厂，行为等价：
 * translatable -> Component.translatable（占位符 {} 由 args 填充），literal -> Component.literal，
 * getString -> component.getString()（纯文本，不含格式代码展开差异）。
 */
public final class ComponentUtil {

	private ComponentUtil() {
	}

	public static Component translatable(String key, Object... args) {
		return Component.translatable(key, args);
	}

	public static Component literal(String text) {
		return Component.literal(text);
	}

	public static String getString(Component component) {
		return component == null ? "" : component.getString();
	}
}
