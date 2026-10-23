package com.xm.mtr3compat.client.ante;

import org.mtr.core.data.StationExit;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * MTR3 的 Station.exits 是 Map&lt;区域名, 出口名列表&gt;，NTE 脚本按
 * station.exits.entrySet().iterator() / getKey() == "Z99" / getValue().get(i) 使用（西安包 pis_config_parser.js）。
 * MTR4 把它改成了 List&lt;StationExit&gt;，直接调用会 "Cannot find function entrySet"。
 * 这里提供同样内容的 List 子类：底层仍是 StationExit 列表（MTR 内部的增删改照常生效），
 * 只是额外暴露 MTR3 风格的 entrySet()（key = 出口名，value = destinations）。
 */
public final class Mtr3StationExits extends ObjectArrayList<StationExit> {

	public Mtr3StationExits() {
		super();
	}

	public Set<Map.Entry<String, ObjectArrayList<String>>> entrySet() {
		final Set<Map.Entry<String, ObjectArrayList<String>>> result = new LinkedHashSet<>();
		for (final StationExit exit : this) {
			result.add(new AbstractMap.SimpleEntry<>(exit.getName(), exit.getDestinations()));
		}
		return result;
	}
}
