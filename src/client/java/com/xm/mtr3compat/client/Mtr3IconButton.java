package com.xm.mtr3compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 原版资源包选择界面右上角的 MTR 图标小按钮：点击直接打开「以 MTR3 加载」勾选界面。
 * 图标来自模组自带贴图。
 */
public class Mtr3IconButton extends Button {

	private static final ResourceLocation ICON = ResourceLocation.tryParse("mtr3compat:textures/gui/mtr3_icon.png");
	/** 图标贴图边长。 */
	private static final int ICON_TEXTURE_SIZE = 128;
	private static final int ICON_SIZE = 18;

	public Mtr3IconButton(int x, int y, OnPress onPress) {
		super(x, y, 20, 20, Component.literal("MTR3"), onPress, Button.DEFAULT_NARRATION);
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.blit(ICON, getX() + 1, getY() + 1, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (isHoveredOrFocused()) {
			guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal("打开 MTR3 资源包选择"), getX() + 10, getY() + 20);
		}
	}
}
