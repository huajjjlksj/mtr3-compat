package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3PackSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mod.screen.ConfigScreen;
import org.mtr.mod.screen.MTRScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR 设置面板右下角新增"以MTR3加载"按钮，打开按包勾选界面。
 */
@Mixin(ConfigScreen.class)
public class ConfigScreenMixin {

	@Inject(method = "init2()V", at = @At("TAIL"))
	private void mtr3compat$addPackButton(CallbackInfo ci) {
		final MTRScreenBase self = (MTRScreenBase) (Object) this;
		// MTR 映射层运行时 ScreenAbstractMapping 被 agent 转为继承原版 Screen，故 this 本身就是原版 Screen
		final Screen vanilla = (Screen) (Object) this;
		final ButtonWidgetExtension button = new ButtonWidgetExtension(self.getWidthMapped() - 161, self.getHeightMapped() - 30, 150, 20, "以MTR3加载",
				b -> Minecraft.getInstance().setScreen(new Mtr3PackSelectScreen(vanilla)));
		self.addChild(new ClickableWidget(button));
	}
}
