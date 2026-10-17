package gg.kapkan.presence;
public final class BadgeVisibility {
    public static boolean self(boolean present,boolean firstPerson,boolean renderNames,boolean hideGui,boolean invisible,boolean spectator,boolean vehicle,boolean discrete,double distanceSquared,boolean teamAllows){
        return present&&!firstPerson&&renderNames&&!hideGui&&!invisible&&!spectator&&!vehicle&&distanceSquared<4096&&(!discrete||distanceSquared<1024)&&teamAllows;
    }
}
