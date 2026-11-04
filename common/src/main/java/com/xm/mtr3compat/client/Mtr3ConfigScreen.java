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
 * MTR3 自动编组只影响选车列表的显示，切换不需要重载；MTR4 车辆加载开关会重载资源包。
 */
public class Mtr3ConfigScreen extends Screen {

	private final Screen parent;
	private final boolean mtr4VehiclesOnOpen;
	private final boolean autoConsistOnOpen;
	private final Set<String> lrOnOpen;
	private final Map<String, float[]> offsetsOnOpen;

	public Mtr3ConfigScreen(Screen parent) {
		super(Component.literal("mtr3-compat 配置"));
		this.parent = parent;
		this.mtr4VehiclesOnOpen = Mtr3CompatConfig.isMtr4Vehicles();
		this.autoConsistOnOpen = Mtr3CompatConfig.isAutoConsist();
		this.lrOnOpen = new LinkedHashSet<>(Mtr3CompatConfig.getScriptLrSwapKeys());
		this.offsetsOnOpen = copyOffsets(Mtr3CompatConfig.getScriptCarOffsets());
	}

	@Override
	protected void init() {
		final int left = width / 2 - 160;
		addRenderableWidget(Button.builder(mtr4VehiclesLabel(), button -> {
					Mtr3CompatConfig.setMtr4Vehicles(!Mtr3CompatConfig.isMtr4Vehicles());
					rebuildWidgets();
				})
				.bounds(left, 70, 320, 20)
				.tooltip(Tooltip.create(Component.literal(
						"关闭（默认）：MTR4 与本模组混合加载，MTR4 的车头/车厢/车尾变体和本模组的单车厢条目都会出现。\n"
								+ "开启：MTR4 自身车辆（内置车辆与资源包转换出的变体）完全不加载，选车列表只留本模组的车型；\n"
								+ "此时 MTR3 自动编组必定生效，对应开关会被隐藏。\n"
								+ "切换后点“完成”会自动重载资源包生效（会重建车场列车，请留意）。")))
				.build());

		if (Mtr3CompatConfig.isMtr4Vehicles()) {
			addRenderableWidget(Button.builder(autoConsistLabel(), button -> {
						Mtr3CompatConfig.setAutoConsist(!Mtr3CompatConfig.isAutoConsist());
						button.setMessage(autoConsistLabel());
					})
					.bounds(left, 100, 320, 20)
					.tooltip(Tooltip.create(Component.literal(
							"开启（默认）：选车列表隐藏 MTR4 的车头、车厢、车尾等变体，只显示本模组的单车厢条目；\n"
									+ "选中后按轨道长度自动铺满整列编组。\n"
									+ "关闭：选车列表隐藏本模组注册的车型，只显示 MTR4 原生变体，由你逐个挑选来拼编组。\n"
									+ "只影响选车列表的显示，不注销车型、不重载：列车建模和已选用的车辆都不受影响。")))
					.build());
		}

		addRenderableWidget(Button.builder(Component.literal("1. 脚本左右互换 (script_lr_swap)"), button -> minecraft.setScreen(new Mtr3VehicleListScreen(this, Mtr3VehicleListScreen.MODE_LR)))
				.bounds(left, 130, 320, 20)
				.tooltip(Tooltip.create(Component.literal(
						"部分资源包的脚本按“模型 +x = 右侧”命名屏体，但本站的 doorLeftOpen 对应 +x，\n"
								+ "导致两块屏显示的是对侧内容、开门提示也反。\n"
								+ "开启后仅对选中的车型生效：脚本看到的 isReversed 取反、左右门状态互换。\n"
								+ "左边是未执行的车型，右边是已执行的车型，点一下即可在两栏之间移动。")))
				.build());

		addRenderableWidget(Button.builder(Component.literal("2. 脚本车厢平移 (script_car_offsets)"), button -> minecraft.setScreen(new Mtr3VehicleListScreen(this, Mtr3VehicleListScreen.MODE_OFFSET)))
				.bounds(left, 160, 320, 20)
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
		guiGraphics.drawCenteredString(font, Component.literal("配置立即写入 config/mtr3-compat.json；只有切换“仅使用本模组车辆”才会在点“完成”时重载资源包"), width / 2, 40, 0xAAAAAA);
	}

	@Override
	public void onClose() {
		Mtr3CompatConfig.save();
		final boolean mtr4VehiclesChanged = mtr4VehiclesOnOpen != Mtr3CompatConfig.isMtr4Vehicles();
		final boolean changed = autoConsistOnOpen != Mtr3CompatConfig.isAutoConsist()
				|| !lrOnOpen.equals(Mtr3CompatConfig.getScriptLrSwapKeys())
				|| !offsetsEqual(offsetsOnOpen, Mtr3CompatConfig.getScriptCarOffsets());
		minecraft.setScreen(parent);
		if (mtr4VehiclesChanged) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] MTR4 车辆加载开关已改动，重载资源包");
			minecraft.reloadResourcePacks();
		} else if (changed) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] 配置已改动，无需重载");
		}
	}

	private static Component mtr4VehiclesLabel() {
		return Component.literal(Mtr3CompatConfig.isMtr4Vehicles() ? "仅使用本模组车辆：关（混合加载）" : "仅使用本模组车辆：开（MTR4 车辆不加载）");
	}

	private static Component autoConsistLabel() {
		return Component.literal(Mtr3CompatConfig.isAutoConsist() ? "MTR3 自动编组：开" : "MTR3 自动编组：关");
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
