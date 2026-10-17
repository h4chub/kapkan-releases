package gg.kapkan.presence.cosmetics;

import java.util.function.IntFunction;

/** Client-thread snapshot cache; changing players or enabled widgets refreshes immediately. */
final class HudRefreshCache<T> {
    static final long INTERVAL=100_000_000L;
    private T value;
    private Object player;
    private int mask;
    private long refreshedAt;
    T get(Object currentPlayer,int enabledMask,long now,IntFunction<T> refresh){
        long elapsed=now-refreshedAt;
        if(value!=null&&player==currentPlayer&&mask==enabledMask&&elapsed>=0&&elapsed<INTERVAL)return value;
        T next=refresh.apply(enabledMask);
        player=currentPlayer;mask=enabledMask;refreshedAt=now;value=next;
        return next;
    }
}
