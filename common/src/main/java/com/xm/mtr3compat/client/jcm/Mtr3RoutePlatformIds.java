package com.xm.mtr3compat.client.jcm;

import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.SimplifiedRoutePlatform;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

/**
 * MTR3 脚本按 {@code route.platformIds} 读取线路站台（{@code .size()} / {@code .get(i).platformId}），
 * 而 MTR4 的 Route / SimplifiedRoute 只有 {@code getRoutePlatforms()} / {@code getPlatforms()}。
 * 这里提供一个惰性委托的列表，元素统一暴露 platformId / stationId / stationName 公有字段。
 */
public final class Mtr3RoutePlatformIds extends AbstractList<Object> {

    private final Object route;

    public Mtr3RoutePlatformIds(Object route) {
        this.route = route;
    }

    private List<?> source() {
        if (route instanceof SimplifiedRoute simplifiedRoute) {
            return simplifiedRoute.getPlatforms();
        }
        if (route instanceof Route fullRoute) {
            return fullRoute.getRoutePlatforms();
        }
        return Collections.emptyList();
    }

    @Override
    public int size() {
        final List<?> list = source();
        return list == null ? 0 : list.size();
    }

    @Override
    public Object get(int index) {
        final List<?> list = source();
        if (list == null || index < 0 || index >= list.size()) {
            throw new IndexOutOfBoundsException(index);
        }
        final Object platform = list.get(index);
        if (platform instanceof SimplifiedRoutePlatform simplifiedPlatform) {
            return new Entry(simplifiedPlatform.getPlatformId(), simplifiedPlatform.getStationId(), simplifiedPlatform.getStationName());
        }
        if (platform instanceof RoutePlatformData routePlatform) {
            final long platformId = routePlatform.platform == null ? 0 : routePlatform.platform.getId();
            return new Entry(platformId, 0, "");
        }
        return new Entry(0, 0, "");
    }

    /** 供 Rhino 直接按字段读取。 */
    public static final class Entry {
        public final long platformId;
        public final long stationId;
        public final String stationName;

        Entry(long platformId, long stationId, String stationName) {
            this.platformId = platformId;
            this.stationId = stationId;
            this.stationName = stationName == null ? "" : stationName;
        }
    }
}
