package gg.kapkan.presence.cosmetics;
/** Frame-rate independent camera easing; no input is queued or delayed. */
public final class ZoomState {
    private double divisor=1;
    private long previous;
    public double update(long now,boolean held,int power,boolean reducedMotion){
        double target=held?Math.max(2,Math.min(5,power)):1;
        if(reducedMotion){divisor=target;previous=now;return divisor;}
        double dt=previous==0?1.0/60:Math.max(0,Math.min(.1,(now-previous)/1e9));previous=now;
        divisor+=(target-divisor)*(1-Math.exp(-dt*24));if(Math.abs(target-divisor)<.001)divisor=target;return divisor;
    }
    public void reset(){divisor=1;previous=0;}
}
