package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.util.ScriptVector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * ANTE 全局 "SoundHelper" 替身（源 cn.zbx1425.mtrsteamloco.scripting.util.client.SoundHelper）。
 * ANTE 的 sowcer Vector3f 对应 ScriptVector3f；其余逻辑与 ANTE 一致（全部经 Minecraft.execute 包到客户端线程）。
 * 额外提供 play(String id, ...) / stop(String id, ...) 便捷重载：ANTE 脚本常先 getSoundEvent 再 play，
 * 但 JCM 环境无 SoundEvent 注册表助手，故直接接受声音 id（如 "minecraft:entity.generic.explode"）。
 */
public final class SoundHelperCompat {

	private SoundHelperCompat() {
	}

	public static void play(SoundInstance sound) {
		if (sound == null) {
			return;
		}
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().play(sound));
	}

	public static void play(SoundInstance sound, int delay) {
		if (sound == null) {
			return;
		}
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().playDelayed(sound, Math.max(0, delay)));
	}

	public static void play(SoundEvent sound, ScriptVector3f pos, SoundSource source, float volume, float pitch) {
		if (sound == null || pos == null) {
			return;
		}
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().level != null) {
				Minecraft.getInstance().level.playLocalSound(pos.x(), pos.y(), pos.z(), sound, source, volume, pitch, false);
			}
		});
	}

	public static void play(SoundEvent sound, ScriptVector3f pos, float volume, float pitch) {
		play(sound, pos, SoundSource.BLOCKS, volume, pitch);
	}

	public static void play(String id, ScriptVector3f pos, SoundSource source, float volume, float pitch) {
		play(lookupSoundEvent(id), pos, source, volume, pitch);
	}

	public static void play(String id, ScriptVector3f pos, float volume, float pitch) {
		play(lookupSoundEvent(id), pos, SoundSource.BLOCKS, volume, pitch);
	}

	public static void stop() {
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop());
	}

	public static void stop(SoundInstance sound) {
		if (sound == null) {
			return;
		}
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop(sound));
	}

	public static void stop(ResourceLocation sound, SoundSource source) {
		if (sound == null) {
			return;
		}
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop(sound, source));
	}

	public static void stop(ResourceLocation sound) {
		stop(sound, SoundSource.BLOCKS);
	}

	public static void stop(String sound, SoundSource source) {
		stop(lookupLocation(sound), source);
	}

	public static void stop(String sound) {
		stop(lookupLocation(sound), SoundSource.BLOCKS);
	}

	public static SoundSource getSoundSource(String str) {
		if (str == null) {
			return SoundSource.BLOCKS;
		}
		switch (str.toUpperCase()) {
			case "MASTER":
				return SoundSource.MASTER;
			case "MUSIC":
				return SoundSource.MUSIC;
			case "RECORDS":
				return SoundSource.RECORDS;
			case "WEATHER":
				return SoundSource.WEATHER;
			case "BLOCKS":
				return SoundSource.BLOCKS;
			case "HOSTILE":
				return SoundSource.HOSTILE;
			case "NEUTRAL":
				return SoundSource.NEUTRAL;
			case "PLAYERS":
				return SoundSource.PLAYERS;
			case "AMBIENT":
				return SoundSource.AMBIENT;
			case "VOICE":
				return SoundSource.VOICE;
			default:
				return SoundSource.BLOCKS;
		}
	}

	/** 供 TickableSound 构造与脚本取 SoundEvent 用。 */
	public static SoundEvent getSoundEvent(String id) {
		return lookupSoundEvent(id);
	}

	private static SoundEvent lookupSoundEvent(String id) {
		final ResourceLocation loc = lookupLocation(id);
		if (loc == null) {
			return null;
		}
		return BuiltInRegistries.SOUND_EVENT.get(loc);
	}

	private static ResourceLocation lookupLocation(String id) {
		if (id == null) {
			return null;
		}
		return id.contains(":") ? ResourceLocation.tryParse(id) : ResourceLocation.tryParse("minecraft:" + id);
	}
}
