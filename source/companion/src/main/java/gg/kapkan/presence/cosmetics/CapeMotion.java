package gg.kapkan.presence.cosmetics;

/** Fixed-step, constrained cloth spine in model space (positive Y points down).
 * Gravity is always present. Wind is driven by travel, never by an idle timer.
 * Four substeps and inextensible links make behavior independent of render FPS. */
public final class CapeMotion {
    public static final int SEGMENTS=20;
    private static final double LINK=1.0/SEGMENTS;
    private final double[] y=new double[SEGMENTS+1],z=new double[SEGMENTS+1],oldY=new double[SEGMENTS+1],oldZ=new double[SEGMENTS+1];
    private final double[] prevY=new double[SEGMENTS+1],prevZ=new double[SEGMENTS+1];
    private double wind,phase,previousWind,previousPhase;
    public CapeMotion(){reset();}
    public void reset(){wind=previousWind=phase=previousPhase=0;for(int i=0;i<=SEGMENTS;i++){y[i]=oldY[i]=prevY[i]=i*LINK;z[i]=oldZ[i]=prevZ[i]=0;}}
    public void tick(double speed,double vertical,String material,boolean reduced){
        speed=Double.isFinite(speed)?Math.clamp(speed,0,0.5):0;vertical=Double.isFinite(vertical)?Math.clamp(vertical,-0.6,0.6):0;
        System.arraycopy(y,0,prevY,0,y.length);System.arraycopy(z,0,prevZ,0,z.length);
        previousWind=wind;previousPhase=phase;
        double target=speed<0.003?0:Math.min(1,speed/0.28);wind+=(target-wind)*0.24;if(wind<0.00001)wind=0;
        phase+=wind*0.65;
        double gravity=material.equals("Heavy")?0.0030:material.equals("Silk")?0.0013:0.0019;
        double drag=material.equals("Heavy")?0.90:0.94;
        for(int sub=0;sub<4;sub++){
            for(int i=1;i<=SEGMENTS;i++){
                double row=(double)i/SEGMENTS;
                double vy=(y[i]-oldY[i])*drag,vz=(z[i]-oldZ[i])*drag;oldY[i]=y[i];oldZ[i]=z[i];
                double ripple=reduced?0:Math.sin(phase-row*5.2)*wind*wind*0.0024*row;
                y[i]+=vy+gravity-vertical*0.003+ ripple;
                z[i]+=vz+wind*wind*(reduced?0.001:0.009)*(0.35+row*0.65);
            }
            for(int pass=0;pass<14;pass++){
                y[0]=z[0]=0;
                for(int i=1;i<=SEGMENTS;i++){
                    double dy=y[i]-y[i-1],dz=z[i]-z[i-1],d=Math.max(1e-9,Math.hypot(dy,dz)),correction=(d-LINK)/d;
                    double share=i==1?1:0.5;
                    y[i]-=dy*correction*share;z[i]-=dz*correction*share;
                    if(i>1){y[i-1]+=dy*correction*0.5;z[i-1]+=dz*correction*0.5;}
                    // Back/body collision plane. No cape can fold through the torso.
                    z[i]=Math.max(0,z[i]);y[i]=Math.max(-0.08,y[i]);
                }
            }
        }
    }
    public float[] frame(float partial,boolean reduced){
        double t=Math.clamp(partial,0,1),w=previousWind+(wind-previousWind)*t,p=previousPhase+(phase-previousPhase)*t;
        float[] points=new float[(SEGMENTS+1)*3];
        for(int i=0;i<=SEGMENTS;i++){double row=(double)i/SEGMENTS;points[i*3]=(float)(prevY[i]+(y[i]-prevY[i])*t);points[i*3+1]=(float)(prevZ[i]+(z[i]-prevZ[i])*t);points[i*3+2]=reduced?0:(float)(Math.sin(p-row*4)*w*row*row*0.018);}
        return points;
    }
    public double tipHeight(){return y[SEGMENTS];}
    public double tipTrail(){return z[SEGMENTS];}
}
