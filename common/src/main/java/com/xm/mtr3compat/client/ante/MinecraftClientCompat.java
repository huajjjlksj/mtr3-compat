package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.integration.MinecraftClientWrapper;
import com.lx862.mtrscripting.core.integration.PlayerEntityWrapper;
import com.lx862.mtrscripting.core.integration.VanillaTextWrapper;
import com.lx862.mtrscripting.core.util.ScriptVector3f;
import com.mojang.blaze3d.pipeline.RenderCall;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.text2speech.Narrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Station;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.block.BlockPSDAPGBase;
import org.mtr.mod.block.BlockPlatform;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mapping.holder.Identifier;

import java.util.List;

/**
 * ANTE 全局 "MinecraftClient" 替身。
 * ANTE 面（cn.zbx1425.mtrsteamloco.scripting.util.client.MinecraftClientUtil）逐方法移植到 mojmap 1.20.1 + MTR4 数据层；
 * 同时保留 JCM MinecraftClientWrapper 的全部方法名做静态委托，勾选包与 JCM 原生脚本都能用。
 * sowcer Vector3f -> ScriptVector3f；mtr.mappings.Text -> 原生 Component（经 displayMessage 委托 JCM 实现）。
 */
public final class MinecraftClientCompat {

	private MinecraftClientCompat() {
	}

	// ---------------- ANTE 面 ----------------

	public static boolean worldIsRaining() {
		return MinecraftClientWrapper.worldIsRaining();
	}

	public static boolean worldIsRainingAt(ScriptVector3f pos) {
		return MinecraftClientWrapper.worldIsRainingAt(pos);
	}

	public static int worldDayTime() {
		return MinecraftClientWrapper.worldDayTime();
	}

	public static void narrate(String message) {
		Minecraft.getInstance().execute(() -> Narrator.getNarrator().say(message, true));
	}

	public static void displayMessage(String message, boolean actionBar) {
		MinecraftClientWrapper.displayMessage(message, actionBar);
	}

	public static void execute(Runnable runnable) {
		Minecraft.getInstance().execute(runnable);
	}

	public static void recordRenderCall(RenderCall renderCall) {
		RenderSystem.recordRenderCall(renderCall);
	}

	public static boolean isOnRenderThreadOrInit() {
		return RenderSystem.isOnRenderThreadOrInit();
	}

	public static void levelEvent(int eventId, ScriptVector3f pos, int data) {
		if (pos == null) {
			return;
		}
		final Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().level != null) {
				Minecraft.getInstance().level.levelEvent(player, eventId, toBlockPos(pos), data);
			}
		});
	}

	/**
	 * ANTE: 从 (vPos, facing) 出发按 aspects 扇出逐跳扫描铁路，返回第一个被占用方向的跳数（0=可达但未占用，-1=不可达）。
	 * MTR3 用 ClientData.RAILS/SIGNAL_BLOCKS/OCCUPIED_RAILS；MTR4 等价物：
	 * Data.positionsToRail（Position->Position->Rail）做拓扑，占用 = blockedRailIds 含 hexId 或 railIdToCurrentlyBlockedSignalColors 非空。
	 * facing 数值约定与 ANTE 一致：直接交给 Angle.similarFacing(float, float) 比较。
	 */
	public static int getOccupiedAspect(ScriptVector3f vPos, float facing, int aspects) {
		if (vPos == null) {
			return -1;
		}
		final MinecraftClientData clientData = MinecraftClientData.getInstance();
		if (clientData == null) {
			return -1;
		}
		final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail = clientData.positionsToRail;
		final Object2ObjectAVLTreeMap<String, LongArrayList> blockedColors = clientData.railIdToCurrentlyBlockedSignalColors;
		Position pos = toPosition(vPos);
		Object2ObjectOpenHashMap<Position, Float> nodesToScan = new Object2ObjectOpenHashMap<>();
		nodesToScan.put(pos, facing);
		int occupiedAspect = -1;

		for (int j = 1; j < aspects; j++) {
			final Object2ObjectOpenHashMap<Position, Float> newNodesToScan = new Object2ObjectOpenHashMap<>();
			for (final Object2ObjectMap.Entry<Position, Float> checkNode : nodesToScan.object2ObjectEntrySet()) {
				final Object2ObjectOpenHashMap<Position, Rail> railMap = positionsToRail.get(checkNode.getKey());
				if (railMap == null) {
					continue;
				}
				for (final Object2ObjectMap.Entry<Position, Rail> railEntry : railMap.object2ObjectEntrySet()) {
					final Rail rail = railEntry.getValue();
					final Angle angleAtNode = rail.getStartAngle(checkNode.getKey());
					if (angleAtNode == null || !angleAtNode.similarFacing(checkNode.getValue())) {
						continue;
					}
					final String hexId = rail.getHexId();
					final LongArrayList colors = blockedColors.get(hexId);
					if (clientData.blockedRailIds.contains(hexId) || (colors != null && !colors.isEmpty())) {
						return j;
					}
					newNodesToScan.put(railEntry.getKey(), rail.getStartAngle(railEntry.getKey()).getOpposite().angleDegrees);
					occupiedAspect = 0;
				}
			}
			nodesToScan = newNodesToScan;
		}
		return occupiedAspect;
	}

	public static Station getStationAt(ScriptVector3f pos) {
		if (pos == null) {
			return null;
		}
		final ClientData data = MinecraftClientData.getInstance();
		if (data == null) {
			return null;
		}
		final Position p = toPosition(pos);
		for (final Station station : data.stations) {
			if (station.inArea(p)) {
				return station;
			}
		}
		return null;
	}

	/**
	 * ANTE: 取 pos 所在车站内、距 pos 水平半径 radius、垂直 [lower, upper] 范围内的最近站台。
	 * MTR3 用 blockPosToPlatformId 逐格扫描；MTR4 无该缓存，改为对车站内站台按中点距离过滤（功能等价，精度到站台级）。
	 */
	public static Platform getPlatformAt(ScriptVector3f pos, int radius, int lower, int upper) {
		if (pos == null) {
			return null;
		}
		final Station station = getStationAt(pos);
		if (station == null) {
			return null;
		}
		final ClientData data = MinecraftClientData.getInstance();
		if (data == null) {
			return null;
		}
		final Position p = toPosition(pos);
		Platform best = null;
		double bestDist = Double.MAX_VALUE;
		for (final Platform platform : data.platforms) {
			if (platform.area != station) {
				continue;
			}
			final Position mid = platform.getMidPosition();
			if (mid == null) {
				continue;
			}
			final double dx = mid.getX() - p.getX();
			final double dz = mid.getZ() - p.getZ();
			final double dy = mid.getY() - p.getY();
			if (Math.sqrt(dx * dx + dz * dz) > radius + 1 || dy < lower - 1 || dy > upper + 1) {
				continue;
			}
			final double dist = dx * dx + dy * dy + dz * dz;
			if (dist < bestDist) {
				bestDist = dist;
				best = platform;
			}
		}
		return best;
	}

	/** ANTE: 在 pos 沿 facing 前后 4 格、下方 0..5 格内找最近的 MTR 节点方块（BlockNode），返回其块坐标。 */
	public static ScriptVector3f getNodeAt(ScriptVector3f vPos, Float fFacing) {
		final Level world = Minecraft.getInstance().level;
		if (world == null || vPos == null) {
			return null;
		}
		final BlockPos pos = toBlockPos(vPos);
		final Direction facing = Direction.fromYRot(fFacing == null ? 0 : fFacing);
		final int[] checkDistance = {0, 1, -1, 2, -2, 3, -3, 4, -4};
		for (final int z : checkDistance) {
			for (final int x : checkDistance) {
				for (int y = -5; y <= 0; y++) {
					final BlockPos checkPos = pos.above(y).relative(facing.getClockWise(), x).relative(facing, z);
					if (world.getBlockState(checkPos).getBlock() instanceof BlockNode) {
						return new ScriptVector3f(checkPos.getX(), checkPos.getY(), checkPos.getZ());
					}
				}
			}
		}
		return null;
	}

	public static ScriptVector3f getCameraPos() {
		final Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gameRenderer == null) {
			return new ScriptVector3f(0, 0, 0);
		}
		final Vec3 v = mc.gameRenderer.getMainCamera().getPosition();
		return new ScriptVector3f((float) v.x, (float) v.y, (float) v.z);
	}

	public static float getCameraDistance(ScriptVector3f from) {
		if (from == null) {
			return Float.MAX_VALUE;
		}
		final ScriptVector3f cameraPos = getCameraPos();
		return (float) cameraPos.distance(from);
	}

	public static Level getLevel() {
		return Minecraft.getInstance().level;
	}

	/**
	 * ANTE: 从车厢两端点中间向两侧扫描站台门方块，返回 [左门可开, 右门可开]。
	 * 已知降级：ANTE 的 BlockEyeCandy.isPlatform 分支（眼糖站台标记）在 JCM 眼糖方块实体上无等价接口，此处不判定；
	 * MTR 原生 BlockPlatform / BlockPSDAPGBase 分支功能完整。
	 */
	public static boolean[] canOpenDoorsAt(ScriptVector3f p1, ScriptVector3f p2) {
		final Level world = getLevel();
		if (world == null || p1 == null || p2 == null) {
			return new boolean[]{false, false};
		}
		final Vec3 pos1 = new Vec3(p1.x(), p1.y(), p1.z());
		final Vec3 pos2 = new Vec3(p2.x(), p2.y(), p2.z());
		final double x = (pos1.x + pos2.x) / 2;
		final double y = (pos1.y + pos2.y) / 2 + 1;
		final double z = (pos1.z + pos2.z) / 2;
		final double realSpacing = pos2.distanceTo(pos1);
		final float yaw = (float) Mth.atan2(pos2.x - pos1.x, pos2.z - pos1.z);
		final float pitch = realSpacing == 0 ? 0 : (float) Math.asin((pos2.y - pos1.y) / realSpacing);
		final boolean doorLeftOpen = scanDoors(world, x, y, z, (float) Math.PI + yaw, pitch, realSpacing / 2);
		final boolean doorRightOpen = scanDoors(world, x, y, z, yaw, pitch, realSpacing / 2);
		return new boolean[]{doorLeftOpen, doorRightOpen};
	}

	private static boolean scanDoors(Level world, double trainX, double trainY, double trainZ, float checkYaw, float pitch, double halfSpacing) {
		final Vec3 offsetVec = new Vec3(1, 0, 0).yRot(checkYaw).xRot(pitch);
		final Vec3 traverseVec = new Vec3(0, 0, 1).yRot(checkYaw).xRot(pitch);
		for (int checkX = 1; checkX <= 3; checkX++) {
			for (int checkY = -2; checkY <= 3; checkY++) {
				for (double checkZ = -halfSpacing; checkZ <= halfSpacing; checkZ++) {
					final BlockPos checkPos = new BlockPos(
							Mth.floor(trainX + offsetVec.x * checkX + traverseVec.x * checkZ),
							Mth.floor(trainY + checkY),
							Mth.floor(trainZ + offsetVec.z * checkX + traverseVec.z * checkZ));
					final Block block = world.getBlockState(checkPos).getBlock();
					if (block instanceof BlockPlatform || block instanceof BlockPSDAPGBase) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static WrappedEntity getCameraEntity() {
		return new WrappedEntity(Minecraft.getInstance().cameraEntity);
	}

	public static WrappedEntity getPlayer() {
		return new WrappedEntity(Minecraft.getInstance().player);
	}

	public static int packLightTexture(int blockLight, int skyLight) {
		return blockLight << 4 | skyLight << 20;
	}

	public static int getLightColor(ScriptVector3f pos) {
		if (pos == null) {
			return 0;
		}
		final Level level = getLevel();
		if (level == null) {
			return 0;
		}
		return LevelRenderer.getLightColor(level, toBlockPos(pos));
	}

	public static void setScreen(Screen screen) {
		execute(() -> Minecraft.getInstance().setScreen(screen));
	}

	public static Screen getPresentScreen() {
		return Minecraft.getInstance().screen;
	}

	public static void reloadResourcePacks() {
		execute(Minecraft.getInstance()::reloadResourcePacks);
	}

	public static void markRendererAllChanged() {
		Minecraft.getInstance().levelRenderer.allChanged();
	}

	// ---------------- JCM 面委托（保持 MinecraftClientWrapper 方法名，避免破坏 JCM 风格脚本） ----------------

	public static boolean worldIsThundering() {
		return MinecraftClientWrapper.worldIsThundering();
	}

	public static boolean isEmittingRedstonePower(ScriptVector3f pos) {
		return MinecraftClientWrapper.isEmittingRedstonePower(pos);
	}

	public static boolean isEmittingRedstonePower(ScriptVector3f pos, org.mtr.mapping.holder.Direction direction) {
		return MinecraftClientWrapper.isEmittingRedstonePower(pos, direction);
	}

	public static int blockLightAt(ScriptVector3f pos) {
		return MinecraftClientWrapper.blockLightAt(pos);
	}

	public static int skyLightAt(ScriptVector3f pos) {
		return MinecraftClientWrapper.skyLightAt(pos);
	}

	public static int lightAt(ScriptVector3f pos) {
		return MinecraftClientWrapper.lightAt(pos);
	}

	public static int lightLevelAt(ScriptVector3f pos) {
		return MinecraftClientWrapper.lightLevelAt(pos);
	}

	public static int getRedstoneLevel(ScriptVector3f pos) {
		return MinecraftClientWrapper.getRedstoneLevel(pos);
	}

	public static String getCurrentWorldId() {
		return MinecraftClientWrapper.getCurrentWorldId();
	}

	public static String getWorldBlockState(ScriptVector3f pos, String key) {
		return MinecraftClientWrapper.getWorldBlockState(pos, key);
	}

	public static PlayerEntityWrapper localPlayer() {
		return MinecraftClientWrapper.localPlayer();
	}

	public static List<PlayerEntityWrapper> getWorldPlayers() {
		return MinecraftClientWrapper.getWorldPlayers();
	}

	public static Integer getScoreboardScore(String target, String criterion) {
		return MinecraftClientWrapper.getScoreboardScore(target, criterion);
	}

	public static int getCameraMode() {
		return MinecraftClientWrapper.getCameraMode();
	}

	public static boolean gamePaused() {
		return MinecraftClientWrapper.gamePaused();
	}

	public static void displayMessage(VanillaTextWrapper text, boolean actionBar) {
		MinecraftClientWrapper.displayMessage(text, actionBar);
	}

	public static int renderDistance() {
		return MinecraftClientWrapper.renderDistance();
	}

	public static void spawnParticleInWorld(Identifier id, ScriptVector3f pos, ScriptVector3f speed) {
		MinecraftClientWrapper.spawnParticleInWorld(id, pos, speed);
	}

	public static void spawnParticleInWorld(Identifier id, ScriptVector3f pos, ScriptVector3f speed, boolean force) {
		MinecraftClientWrapper.spawnParticleInWorld(id, pos, speed, force);
	}

	// ---------------- 工具 ----------------

	private static BlockPos toBlockPos(ScriptVector3f v) {
		return new BlockPos(Mth.floor(v.x()), Mth.floor(v.y()), Mth.floor(v.z()));
	}

	private static Position toPosition(ScriptVector3f v) {
		final BlockPos p = toBlockPos(v);
		return new Position(p.getX(), p.getY(), p.getZ());
	}
}
