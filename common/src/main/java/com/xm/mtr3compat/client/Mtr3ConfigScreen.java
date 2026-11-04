package com.xm.mtr3compat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 本模组配置页：两个按车型选择的配置入口 + 完成。
 * 关闭时若配置有改动，自动重载资源包；没有改动则不重载。
 */
public class Mtr3ConfigScreen extends Screen {

	private final Screen parent;
	private final Set<String> lrOnOpen;
	private final Map<String, float[]> offsetsOnOpen;

	public Mtr3ConfigScreen(Screen parent) {
		super(Component.literal("mtr3-compat 配置"));
		this.parent = parent;
		this.lrOnOpen = new LinkedHashSet<>(Mtr3CompatConfig.getScriptLrSwapKeys());
		this.offsetsOnOpen = copyOffsets(Mtr3CompatConfig.getScriptCarOffsets());
	}

	@Override
	protected void init() {
		final int left = width / 2 - 160;
		addRenderableWidget(Button.builder(Component.literal("1. 脚本左右互换 (script_lr_swap)"), button -> minecraft.setScreen(new Mtr3VehicleListScreen(this, Mtr3VehicleListScreen.MODE_LR)))
				.bounds(left, 70, 320, 20)
				.tooltip(Tooltip.create(Component.literal(
						"部分资源包的脚本按“模型 +x = 右侧”命名屏体，但本站的 doorLeftOpen 对应 +x，\n"
								+ "导致两块屏显示的是对侧内容、开门提示也反。\n"
								+ "开启后仅对选中的车型生效：脚本看到的 isReversed 取反、左右门状态互换。\n"
								+ "左边是未执行的车型，右边是已执行的车型，点一下即可在两栏之间移动。")))
				.build());

		addRenderableWidget(Button.builder(Component.literal("2. 脚本车厢平移 (script_car_offsets)"), button -> minecraft.setScreen(new Mtr3VehicleListScreen(this, Mtr3VehicleListScreen.MODE_OFFSET)))
				.bounds(left, 100, 320, 20)
				.tooltip(Tooltip.create(Component.literal(
						"给脚本直接在车厢坐标系里绘制的内容（如走字屏、车外贴字）加平移量，单位：格。\n"
								+ "选进右栏的车型下方会出现 x/y/z 输入框，缺省为 0（0 表示不平移）。\n"
								+ "同样只对本模组加载的车型生效。")))
				.build());

		addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
				.bounds(width / 2 - 60, height - 28, 120, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
		guiGraphics.drawCenteredString(font, Component.literal("配置立即写入 config/mtr3-compat.json；点“完成”时有改动会自动重载资源包"), width / 2, 40, 0xAAAAAA);
	}

	@Override
	public void onClose() {
		Mtr3CompatConfig.save();
		final boolean changed = !lrOnOpen.equals(Mtr3CompatConfig.getScriptLrSwapKeys())
				|| !offsetsEqual(offsetsOnOpen, Mtr3CompatConfig.getScriptCarOffsets());
		minecraft.setScreen(parent);
		if (changed) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] 配置已改动，重载资源包");
			minecraft.reloadResourcePacks();
		}
	}

	private static Map<String, float[]> copyOffsets(Map<String, float[]> source) {
		final Map<String, float[]> copy = new HashMap<>();
		source.forEach((key, value) -> copy.put(key, new float[]{value[0], value[1], value[2]}));
		return copy;
	}

	private static boolean offsetsEqual(Map<String, float[]> a, Map<String, float[]> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (final Map.Entry<String, float[]> entry : a.entrySet()) {
			final float[] other = b.get(entry.getKey());
			if (other == null
					|| Float.compare(entry.getValue()[0], other[0]) != 0
					|| Float.compare(entry.getValue()[1], other[1]) != 0
					|| Float.compare(entry.getValue()[2], other[2]) != 0) {
				return false;
			}
		}
		return true;
	}
}
