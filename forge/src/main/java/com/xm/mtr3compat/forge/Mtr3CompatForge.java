package com.xm.mtr3compat.forge;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(Mtr3CompatClient.MOD_ID)
public class Mtr3CompatForge {

	public Mtr3CompatForge() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		bus.addListener(this::onClientSetup);
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		Mtr3CompatConfig.configDir = FMLPaths.CONFIGDIR.get();
		Mtr3CompatClient.init();
	}
}
