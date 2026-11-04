package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.util.ScriptVector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * ANTE 全局 "TickableSound" 替身（源 cn.zbx1425.mtrsteamloco.scripting.util.client.TickableSound）。
 * ANTE 经 mtr.mappings.TickableSoundInstanceMapper 继承；mojmap 环境直接继承原生 AbstractTickableSoundInstance，
 * 公开字段（volume/pitch/x/y/z/looping/delay/relative/attenuation）语义一致。
 * 构造接受 String 声音 id 或 ResourceLocation；1.19.3+ 用 SoundEvent.createVariableRangeEvent。
 */
public class TickableSoundCompat extends AbstractTickableSoundInstance {

	public TickableSoundCompat(Object sound) {
		this(sound, SoundSource.BLOCKS);
	}

	public TickableSoundCompat(Object sound, SoundSource source) {
		super(lookupEvent(sound), source, net.minecraft.util.RandomSource.create(0L));
	}

	private static SoundEvent lookupEvent(Object sound) {
		final ResourceLocation loc;
		if (sound instanceof ResourceLocation) {
			loc = (ResourceLocation) sound;
		} else if (sound instanceof CharSequence) {
			final String id = sound.toString();
			loc = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
		} else {
			loc = null;
		}
		if (loc == null) {
			throw new IllegalArgumentException("TickableSound: invalid sound id " + sound);
		}
		return SoundEvent.createVariableRangeEvent(loc);
	}

	public void setData(float volume, float pitch, ScriptVector3f pos) {
		this.volume = volume;
		this.pitch = pitch;
		if (pos != null) {
			this.x = pos.x();
			this.y = pos.y();
			this.z = pos.z();
		}
	}

	public void setLooping(boolean looping) {
		this.looping = looping;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public void setAttenuation(boolean attenuation) {
		this.attenuation = attenuation ? SoundInstance.Attenuation.LINEAR : SoundInstance.Attenuation.NONE;
	}

	public void setRelative(boolean relative) {
		this.relative = relative;
	}

	public void play() {
		Minecraft.getInstance().getSoundManager().play(this);
	}

	public void quit() {
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop(this));
	}

	public void pause() {
		stop();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public boolean canPlaySound() {
		return true;
	}

	@Override
	public void tick() {
	}
}
