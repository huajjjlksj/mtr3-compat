package com.xm.mtr3compat.client;

import org.mtr.core.data.Rail;

/**
 * MTR3 的 RailType 枚举在脚本里只被用来做两件事：与字符串比较（"PLATFORM"/"TURN_BACK"/"SIDING"）
 * 和读 {@code speedLimit}。MTR4 已把 railType 移出 Rail，这里提供等价的轻量替代：
 * 名字通过 toString() 暴露（Rhino 的 == 比较会走 toString），速度用公有字段暴露。
 */
public final class LegacyRailType {

	private static final LegacyRailType WOODEN = new LegacyRailType("WOODEN", 20);
	private static final LegacyRailType STONE = new LegacyRailType("STONE", 40);
	private static final LegacyRailType EMERALD = new LegacyRailType("EMERALD", 60);
	private static final LegacyRailType IRON = new LegacyRailType("IRON", 80);
	private static final LegacyRailType BRICKS = new LegacyRailType("BRICKS", 100);
	private static final LegacyRailType OBSIDIAN = new LegacyRailType("OBSIDIAN", 120);
	private static final LegacyRailType PRISMARINE = new LegacyRailType("PRISMARINE", 140);
	private static final LegacyRailType BLAZE = new LegacyRailType("BLAZE", 160);
	private static final LegacyRailType QUARTZ = new LegacyRailType("QUARTZ", 200);
	private static final LegacyRailType DIAMOND = new LegacyRailType("DIAMOND", 300);
	private static final LegacyRailType PLATFORM = new LegacyRailType("PLATFORM", 80);
	private static final LegacyRailType SIDING = new LegacyRailType("SIDING", 40);
	private static final LegacyRailType TURN_BACK = new LegacyRailType("TURN_BACK", 80);
	private static final LegacyRailType CABLE_CAR = new LegacyRailType("CABLE_CAR", 30);
	private static final LegacyRailType RUNWAY = new LegacyRailType("RUNWAY", 300);

	/** 脚本直接读的限速值（公里/小时）。 */
	public final int speedLimit;
	private final String name;

	private LegacyRailType(String name, int speedLimit) {
		this.name = name;
		this.speedLimit = speedLimit;
	}

	public static LegacyRailType of(Rail rail, int speedLimit) {
		if (rail.isPlatform()) {
			return PLATFORM;
		}
		if (rail.canTurnBack()) {
			return TURN_BACK;
		}
		if (rail.isSiding()) {
			return SIDING;
		}
		switch (speedLimit) {
			case 30:
				return CABLE_CAR;
			case 40:
				return STONE;
			case 60:
				return EMERALD;
			case 100:
				return BRICKS;
			case 120:
				return OBSIDIAN;
			case 140:
				return PRISMARINE;
			case 160:
				return BLAZE;
			case 200:
				return QUARTZ;
			case 300:
				return RUNWAY;
			default:
				return IRON;
		}
	}

	@Override
	public String toString() {
		return name;
	}
}
