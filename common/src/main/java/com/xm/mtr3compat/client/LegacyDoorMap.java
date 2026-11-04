package com.xm.mtr3compat.client;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Box;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 旧版资源包的门扇常由内外两片组成（同一门洞槽位、同一门偏移）。
 * MTR4 按部件包围盒各自就近绑定门洞，内层往往因包围盒更大（含墙板等大面板）而被绑到
 * 相邻门洞格，于是开门时只有外层滑动、内层停在关门位置。
 * 这里以「门洞列表实例 + 门洞槽位」为分组，把同组所有门扇统一绑定到组内最薄那片门扇的门口。
 */
public final class LegacyDoorMap {

	/** 写入旧版门部件 names 的标记名；MTR4 找不到同名模型部件，解析时会被忽略。 */
	public static final String MARKER = "mtr3compat$door";

	private static final Map<Object, Map<String, Group>> GROUPS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Class<?>, Field[]> PART_FIELDS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<String, Field> NAMED_FIELDS = new ConcurrentHashMap<>();

	/**
	 * 在 {@code ModelPropertiesPart.mapDoors} 之后调用。
	 *
	 * @param part            ModelPropertiesPart 实例
	 * @param partDetailsList 该部件的渲染细节列表
	 * @param doorways        当前车型资源缓存的门洞列表（同一次缓存构建中所有部件共用同一实例）
	 */
	public static void unify(Object part, ObjectArrayList<?> partDetailsList, ObjectArrayList<Box> doorways) {
		if (partDetailsList == null || partDetailsList.isEmpty() || doorways == null || doorways.isEmpty()) {
			return;
		}

		final Field namesField = getField(part.getClass(), "names");
		final Field doorXField = getField(part.getClass(), "doorXMultiplier");
		final Field doorZField = getField(part.getClass(), "doorZMultiplier");
		if (namesField == null || doorXField == null || doorZField == null) {
			return;
		}

		final double partDoorXMultiplier;
		final double partDoorZMultiplier;
		try {
			final Object names = namesField.get(part);
			if (!(names instanceof ObjectArrayList) || !((ObjectArrayList<?>) names).contains(MARKER)) {
				return;
			}
			partDoorXMultiplier = (Double) doorXField.get(part);
			partDoorZMultiplier = (Double) doorZField.get(part);
		} catch (Exception ignored) {
			return;
		}

		final Map<String, Group> groups;
		synchronized (GROUPS) {
			groups = GROUPS.computeIfAbsent(doorways, key -> new HashMap<>());
		}

		synchronized (groups) {
			for (final Object partDetails : partDetailsList) {
				final Field[] fields = getPartFields(partDetails.getClass());
				if (fields == null) {
					return;
				}

				try {
					final Box box = (Box) fields[0].get(partDetails);
					final double x = (Double) fields[1].get(partDetails);
					final double y = (Double) fields[2].get(partDetails);
					final double z = (Double) fields[3].get(partDetails);
					final boolean flipped = (Boolean) fields[4].get(partDetails);
					final Box doorway = (Box) fields[5].get(partDetails);
					if (box == null || doorway == null) {
						continue;
					}

					final double centerX = (box.getMinXMapped() + box.getMaxXMapped()) / 2;
					final int side = centerX < 0 ? -1 : centerX > 0 ? 1 : 0;
					// 分组键必须带门偏移量：同一门洞的两片门扇朝相反方向滑出（door_offset 不同），
					// 不能与同一门洞的内外层混为一组，否则会把本来正确的门扇挪到隔壁门洞。
					final String key = side + "|" + partDoorXMultiplier + "|" + partDoorZMultiplier + "|" + x + "|" + y + "|" + z + "|" + flipped;
					final Group group = groups.computeIfAbsent(key, ignored -> new Group());
					group.members.add(partDetails);

					final double volume = Math.abs(box.getMaxXMapped() - box.getMinXMapped())
							* Math.abs(box.getMaxYMapped() - box.getMinYMapped())
							* Math.abs(box.getMaxZMapped() - box.getMinZMapped());
					if (volume < group.bestVolume) {
						group.bestVolume = volume;
						group.doorway = doorway;
					}

					for (final Object member : group.members) {
						fields[5].set(member, group.doorway);
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static Field getField(Class<?> targetClass, String name) {
		return getNamedField(targetClass, name);
	}

	/** 按「类 + 字段名」缓存的反射读取（MTR4 同一类上可能有多个待读字段）。 */
	private static Field getNamedField(Class<?> targetClass, String name) {
		final String cacheKey = targetClass.getName() + "#" + name;
		final Field cached = NAMED_FIELDS.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		Field result = null;
		Class<?> current = targetClass;
		while (current != null && result == null) {
			try {
				result = current.getDeclaredField(name);
				result.setAccessible(true);
			} catch (Exception ignored) {
				current = current.getSuperclass();
			}
		}
		if (result != null) {
			NAMED_FIELDS.put(cacheKey, result);
		}
		return result;
	}

	/**
	 * 缓存 PartDetails 的字段顺序：box、x、y、z、flipped、doorway。
	 */
	private static Field[] getPartFields(Class<?> partDetailsClass) {
		return PART_FIELDS.computeIfAbsent(partDetailsClass, key -> {
			try {
				final Field[] fields = new Field[6];
				fields[0] = key.getDeclaredField("box");
				fields[1] = key.getDeclaredField("x");
				fields[2] = key.getDeclaredField("y");
				fields[3] = key.getDeclaredField("z");
				fields[4] = key.getDeclaredField("flipped");
				fields[5] = key.getDeclaredField("doorway");
				for (final Field field : fields) {
					field.setAccessible(true);
				}
				return fields;
			} catch (Exception ignored) {
				return null;
			}
		});
	}

	/**
	 * 判断是否为旧版资源包注册的 OBJ 门部件（names 中含标记名）。
	 *
	 * @param part ModelPropertiesPart 实例
	 */
	public static boolean hasMarker(Object part) {
		if (part == null) {
			return false;
		}

		final Field namesField = getField(part.getClass(), "names");
		if (namesField == null) {
			return false;
		}

		try {
			final Object names = namesField.get(part);
			return names instanceof ObjectArrayList && ((ObjectArrayList<?>) names).contains(MARKER);
		} catch (Exception ignored) {
			return false;
		}
	}

	private static final class Group {
		private double bestVolume = Double.MAX_VALUE;
		private Box doorway;
		private final ObjectArrayList<Object> members = new ObjectArrayList<>();
	}
}
