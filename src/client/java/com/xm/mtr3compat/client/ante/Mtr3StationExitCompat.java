package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.core.data.Station;
import org.mtr.core.data.StationExit;
import org.mtr.core.generated.data.StationSchema;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MTR4 的 Station.exits 是 {@code List<StationExit>}（字段在 StationSchema，final），
 * 西安包脚本按 MTR3 的 Map 语义调用 {@code exits.entrySet()}。
 * 这里在 JCM 组装 StopsData 时（已确认会执行的路径）把每个 Stop.station 的 exits 字段
 * 反射替换成 {@link Mtr3StationExits}，MTR 内部后续对同一对象的增删仍生效。
 */
public final class Mtr3StationExitCompat {

	private static final AtomicBoolean LOGGED = new AtomicBoolean();
	private static volatile Field exitsField;

	private Mtr3StationExitCompat() {
	}

	public static void ensureStops(Object stopsData) {
		if (stopsData == null) {
			return;
		}
		for (final Field field : stopsData.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				final Object value = field.get(stopsData);
				if (value instanceof Iterable<?>) {
					for (final Object element : (Iterable<?>) value) {
						ensureStop(element, 0);
					}
				}
			} catch (Throwable ignored) {
			}
		}
	}

	private static void ensureStop(Object value, int depth) {
		if (!(value instanceof VehicleWrapper.Stop)) {
			return;
		}
		final VehicleWrapper.Stop stop = (VehicleWrapper.Stop) value;
		ensureStation(stop.station);
		ensureStation(stop.destinationStation);
		if (depth == 0) {
			ensureStop(stop.roundUpRoute, 1);
		}
	}

	private static void ensureStation(Station station) {
		if (station == null) {
			return;
		}
		try {
			Field field = exitsField;
			if (field == null) {
				field = StationSchema.class.getDeclaredField("exits");
				field.setAccessible(true);
				exitsField = field;
			}
			final Object current = field.get(station);
			if (current instanceof Mtr3StationExits) {
				return;
			}
			final Mtr3StationExits replacement = new Mtr3StationExits();
			if (current instanceof List<?>) {
				for (final Object exit : (List<?>) current) {
					if (exit instanceof StationExit) {
						replacement.add((StationExit) exit);
					}
				}
			}
			field.set(station, replacement);
			if (LOGGED.compareAndSet(false, true)) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] Station.exits 已替换为 MTR3 Map 语义, 出口数=" + replacement.size());
			}
		} catch (Throwable e) {
			if (LOGGED.compareAndSet(false, true)) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] Station.exits 替换失败", e);
			}
		}
	}
}
