package com.xm.mtr3compat.client.ante;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ANTE 全局 "GlobalRegister" 替身（照抄 cn.zbx1425.mtrsteamloco.scripting.util.GlobalRegister）。
 * 跨脚本共享的静态注册表：车辆脚本写入、PIDS/眼糖脚本读取，同一类加载器下所有脚本共享。
 */
public class GlobalRegister {

	private static final Map<String, Object> register = new HashMap<>();

	public static void put(String key, Object value) {
		synchronized (register) {
			register.put(key, value);
		}
	}

	public static Object get(String key) {
		synchronized (register) {
			return register.get(key);
		}
	}

	public static boolean containsKey(String key) {
		synchronized (register) {
			return register.containsKey(key);
		}
	}

	public static boolean containsValue(Object value) {
		synchronized (register) {
			return register.containsValue(value);
		}
	}

	public static Set<String> keySet() {
		synchronized (register) {
			return register.keySet();
		}
	}

	public static Collection<Object> values() {
		synchronized (register) {
			return register.values();
		}
	}

	public static void clear() {
		synchronized (register) {
			register.clear();
		}
	}

	public static int size() {
		synchronized (register) {
			return register.size();
		}
	}

	public static void remove(String key) {
		synchronized (register) {
			register.remove(key);
		}
	}

	public static Set<Map.Entry<String, Object>> entrySet() {
		synchronized (register) {
			return register.entrySet();
		}
	}
}
