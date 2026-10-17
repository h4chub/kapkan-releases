package gg.kapkan.presence;
import gg.kapkan.presence.cosmetics.ZoomState;
public final class ZoomStateTest {
 static int assertions;
 static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
 public static void main(String[]args){
  ZoomState state=new ZoomState();long now=1_000_000_000L;double first=state.update(now,true,3,false);check(first>1&&first<3,"zoom transition begins immediately");double previous=first;
  for(int i=0;i<30;i++){double value=state.update(now+=16_666_667,true,3,false);check(value>=previous&&value<=3,"smooth bounded zoom");previous=value;}check(previous>2.99,"reaches configured zoom");
  double release=state.update(now+=16_666_667,false,3,false);check(release<previous&&release>1,"release easesout");
  for(int i=0;i<40;i++)release=state.update(now+=16_666_667,false,3,false);check(release==1,"release settles exactlynormal");
  check(state.update(now+=16_666_667,true,5,true)==5,"reducedmotion instantzoom");check(state.update(now+=16_666_667,false,5,true)==1,"reducedmotion instantrelease");state.reset();check(state.update(now,false,3,false)==1,"worldchange resetsfov");
  ZoomState a=new ZoomState(),b=new ZoomState();long start=2_000_000_000L;a.update(start,true,4,false);b.update(start,true,4,false);double va=0,vb=0;for(int i=1;i<=60;i++)va=a.update(start+i*16_666_667L,true,4,false);for(int i=1;i<=30;i++)vb=b.update(start+i*33_333_334L,true,4,false);check(Math.abs(va-vb)<.001,"easing independentscreen framerate");System.out.println("ZoomStateTest: "+assertions+" assertions passed");
 }
}
