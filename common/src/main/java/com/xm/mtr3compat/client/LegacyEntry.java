package com.xm.mtr3compat.client;

import org.mtr.core.serializer.ReaderBase;

/**
 * 以 MTR3 legacy schema 解析 mtr_custom_resources.json 的 custom_trains 条目，
 * 暴露受保护字段（默认值与 LegacyVehicleResource 完全一致）。
 */
public final class LegacyEntry extends org.mtr.legacy.generated.resource.VehicleResourceSchema {

	public LegacyEntry(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public String baseTrainType() {
		return base_train_type;
	}

	public String name() {
		return name;
	}

	public String color() {
		return color;
	}

	public String model() {
		return model;
	}

	public String modelProperties() {
		return model_properties;
	}

	public String description() {
		return description;
	}

	public String wikipediaArticle() {
		return wikipedia_article;
	}

	public String textureId() {
		return texture_id;
	}

	public boolean hasGangwayConnection() {
		return has_gangway_connection;
	}

	public String gangwayConnectionId() {
		return gangway_connection_id;
	}

	public String trainBarrierId() {
		return train_barrier_id;
	}

	public String doorAnimationType() {
		return door_animation_type;
	}

	public double riderOffset() {
		return rider_offset;
	}

	public String bveSoundBaseId() {
		return bve_sound_base_id;
	}

	public long speedSoundCount() {
		return speed_sound_count;
	}

	public String speedSoundBaseId() {
		return speed_sound_base_id;
	}

	public String doorSoundBaseId() {
		return door_sound_base_id;
	}

	public double doorCloseSoundTime() {
		return door_close_sound_time;
	}

	public boolean accelSoundAtCoast() {
		return accel_sound_at_coast;
	}

	public boolean constPlaybackSpeed() {
		return const_playback_speed;
	}

	public boolean flipV() {
		return flipV;
	}
}
