package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeArray;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeJavaObject;

import java.lang.reflect.Array;
import java.util.List;

/**
 * ANTE 全局 "asJavaArray" 的 Java 实现。
 * ANTE 原实现用 Java.to(jsArr, clazz)；Rhino 1.9.0 无 Java 工具类，这里用反射等价实现：
 * 接受 NativeArray / java.util.List / 任意 Object[]，按目标组件类型建数组。
 * clazz 参数接受 Class 对象、NativeJavaClass（Java.type('java.lang.String') 的结果）或类名字符串。
 * 元素逐个 Array.set 并宽容转换（数字按目标基本类型转型，其余原样放入，类型不符抛 IllegalArgumentException 由脚本报错定位）。
 */
public final class JavaArrayUtil {

	private JavaArrayUtil() {
	}

	public static Object asJavaArray(Object source, Object clazz) {
		final Class<?> elementClass = resolveClass(clazz);
		final Object array;
		final int length;
		if (source instanceof NativeArray) {
			length = (int) ((NativeArray) source).getLength();
			array = Array.newInstance(elementClass, length);
			for (int i = 0; i < length; i++) {
				Array.set(array, i, convert(((NativeArray) source).get(i), elementClass));
			}
		} else if (source instanceof List) {
			List<?> list = (List<?>) source;
			length = list.size();
			array = Array.newInstance(elementClass, length);
			for (int i = 0; i < length; i++) {
				Array.set(array, i, convert(list.get(i), elementClass));
			}
		} else if (source instanceof Object[]) {
			Object[] src = (Object[]) source;
			length = src.length;
			array = Array.newInstance(elementClass, length);
			for (int i = 0; i < length; i++) {
				Array.set(array, i, convert(src[i], elementClass));
			}
		} else {
			throw new IllegalArgumentException("asJavaArray: unsupported source " + (source == null ? "null" : source.getClass()));
		}
		return array;
	}

	private static Class<?> resolveClass(Object clazz) {
		if (clazz instanceof Class) {
			return (Class<?>) clazz;
		}
		if (clazz instanceof NativeJavaObject) {
			Object unwrapped = ((NativeJavaObject) clazz).unwrap();
			if (unwrapped instanceof Class) {
				return (Class<?>) unwrapped;
			}
		}
		if (clazz instanceof CharSequence) {
			try {
				return Class.forName(clazz.toString());
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("asJavaArray: unknown class " + clazz, e);
			}
		}
		throw new IllegalArgumentException("asJavaArray: clazz must be Class/Java.type()/String, got " + (clazz == null ? "null" : clazz.getClass()));
	}

	private static Object convert(Object value, Class<?> target) {
		if (value == null) {
			return null;
		}
		if (target.isInstance(value)) {
			return value;
		}
		if (value instanceof Number) {
			Number n = (Number) value;
			if (target == int.class || target == Integer.class) {
				return n.intValue();
			}
			if (target == long.class || target == Long.class) {
				return n.longValue();
			}
			if (target == float.class || target == Float.class) {
				return n.floatValue();
			}
			if (target == double.class || target == Double.class) {
				return n.doubleValue();
			}
			if (target == short.class || target == Short.class) {
				return n.shortValue();
			}
			if (target == byte.class || target == Byte.class) {
				return n.byteValue();
			}
			if (target == boolean.class || target == Boolean.class) {
				return n.intValue() != 0;
			}
		}
		if (value instanceof CharSequence && target == String.class) {
			return value.toString();
		}
		if (value instanceof Boolean && (target == boolean.class || target == Boolean.class)) {
			return value;
		}
		if (target.isInstance(value)) {
			return value;
		}
		// Rhino 包装的 Java 对象（如 NativeJavaObject 包 String）解包后再试
		if (value instanceof NativeJavaObject) {
			Object unwrapped = ((NativeJavaObject) value).unwrap();
			if (target.isInstance(unwrapped)) {
				return unwrapped;
			}
			if (unwrapped instanceof Number) {
				return convert(unwrapped, target);
			}
		}
		return value;
	}
}
