package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.AutoConsist;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.ScreenAbstractMapping;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.VehicleSelectorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleSelectorScreen.class)
public abstract class VehicleSelectorScreenMixin implements IGui {

	@Shadow
	private Siding siding;

	@Shadow
	protected abstract void updateList();

	@Inject(method = "init2()V", at = @At("TAIL"))
	private void mtr3compat$addToggleButton(CallbackInfo ci) {
		final ButtonWidgetExtension button = new ButtonWidgetExtension(0, 0, PANEL_WIDTH, SQUARE_SIZE, mtr3compat$buttonText(), b -> {
			Mtr3CompatClient.autoConsist = !Mtr3CompatClient.autoConsist;
			b.setMessage(new Text(mtr3compat$buttonText().data));
			updateList();
		});
		final int screenWidth = ((ScreenAbstractMapping) (Object) this).getWidthMapped();
		IDrawing.setPositionAndWidth(button, (screenWidth - PANEL_WIDTH) / 2, 0, PANEL_WIDTH);
		((ScreenExtension) (Object) this).addChild(new ClickableWidget(button));
	}

	@Inject(method = "updateList()V", at = @At("TAIL"))
	private void mtr3compat$autoConsist(CallbackInfo ci) {
		if (!Mtr3CompatClient.autoConsist) {
			return;
		}
		final ObjectArrayList<VehicleCar> cars = siding.getVehicleCars();
		if (cars.size() != 1) {
			return;
		}
		final ObjectArrayList<VehicleCar> expanded = AutoConsist.buildConsist(siding, cars.get(0).getVehicleId());
		if (expanded == null || expanded.size() <= 1) {
			return;
		}
		siding.setVehicleCars(expanded);
		InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)));
	}

	private MutableText mtr3compat$buttonText() {
		return TextHelper.literal(Mtr3CompatClient.autoConsist ? "MTR3 自动编组: 开" : "MTR3 自动编组: 关");
	}
}
