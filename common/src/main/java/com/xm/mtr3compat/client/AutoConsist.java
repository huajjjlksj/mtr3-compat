package com.xm.mtr3compat.client;

import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.VehicleResource;

/**
 * MTR3 式自动编组：选中一辆 mtr3-compat 原生车型（一车一条目），
 * 按 MTR3 公式 trainCars = min(transportMode.maxLength, floor(railLength / spacing))
 * 生成 N 节相同车辆（spacing = length，长度已含 +1，padding=0）。
 */
public final class AutoConsist {

	private AutoConsist() {
	}

	/** 按轨道长度自动生成编组；非原生车型或无法编组时返回 null。 */
	public static ObjectArrayList<VehicleCar> buildConsist(Siding siding, String vehicleId) {
		if (!NativeTrainLoader.isNative(vehicleId)) {
			return null;
		}
		final TransportMode mode = siding.getTransportMode();
		final VehicleResource resource = getVehicle(mode, vehicleId);
		if (resource == null) {
			return null;
		}
		final double spacing = resource.getLength() + resource.getCouplingPadding1() + resource.getCouplingPadding2();
		if (spacing <= 0) {
			return null;
		}
		final int count = Math.min(mode.maxLength, (int) Math.floor(siding.getRailLength() / spacing));
		if (count < 1) {
			return null;
		}
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		for (int i = 0; i < count; i++) {
			cars.add(new VehicleCar(resource.getId(), resource.getLength(), resource.getWidth(),
					resource.getBogie1Position(), resource.getBogie2Position(),
					resource.getCouplingPadding1(), resource.getCouplingPadding2()));
		}
		return cars;
	}

	private static VehicleResource getVehicle(TransportMode mode, String id) {
		final VehicleResource[] result = new VehicleResource[1];
		CustomResourceLoader.getVehicleById(mode, id, pair -> result[0] = pair.left());
		return result[0];
	}
}
