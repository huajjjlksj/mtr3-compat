package com.xm.mtr3compat.fabric;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class Mtr3CompatFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Mtr3CompatConfig.configDir = FabricLoader.getInstance().getConfigDir();
		Mtr3CompatClient.init();
	}
}
