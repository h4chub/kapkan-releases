package gg.kapkan.presence.cosmetics;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import gg.kapkan.presence.mixin.RenderTypeAccess;
import java.util.*;

/** Double-sided, smoothly segmented cloth; immutable per-frame vertex data. */
public final class CapeRenderer {
    // Preserve the artwork on both faces. Depth testing/writes and world fog still apply.
    // Vanilla's emissive entity pipeline also has PER_FACE_LIGHTING, so it still darkens one side.
    private static final RenderPipeline PIPELINE=RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("kapkan","pipeline/cape"))
        .withVertexShader("core/entity").withFragmentShader("core/entity")
        .withShaderDefine("EMISSIVE").withShaderDefine("NO_CARDINAL_LIGHTING").withShaderDefine("NO_OVERLAY")
        .withShaderDefine("ALPHA_CUTOUT",0.1f).withSampler("Sampler0")
        .withUniform("DynamicTransforms",UniformType.UNIFORM_BUFFER).withUniform("Projection",UniformType.UNIFORM_BUFFER)
        .withUniform("Fog",UniformType.UNIFORM_BUFFER)
        .withVertexFormat(DefaultVertexFormat.ENTITY,VertexFormat.Mode.QUADS)
        .withCull(false).withDepthStencilState(DepthStencilState.DEFAULT)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    private static final Map<Identifier,RenderType> TYPES=new LinkedHashMap<>();
    public static void forget(Identifier texture){TYPES.remove(texture);}
    public static RenderType type(Identifier texture){
        if(TYPES.size()>256)TYPES.clear();
        return TYPES.computeIfAbsent(texture,id->RenderTypeAccess.kapkan$create("kapkan_cape",RenderSetup.builder(PIPELINE).withTexture("Sampler0",id).createRenderSetup()));
    }
    public static void submit(PoseStack pose,SubmitNodeCollector collector,int light,CapeFrame frame){
        float[] points=frame.points();
        collector.submitCustomGeometry(pose,type(frame.texture()),(transform,out)->{
            for(int row=0;row<CapeMotion.SEGMENTS;row++)for(int col=0;col<6;col++){
                float u0=col/6f,u1=(col+1)/6f,v0=(float)row/CapeMotion.SEGMENTS,v1=(float)(row+1)/CapeMotion.SEGMENTS;
                float dy=points[(row+1)*3]-points[row*3],dz=points[(row+1)*3+1]-points[row*3+1];float length=(float)Math.max(1e-6,Math.hypot(dy,dz));
                vertex(out,transform,points,row,u0,v0,light,-dz/length,dy/length);vertex(out,transform,points,row+1,u0,v1,light,-dz/length,dy/length);vertex(out,transform,points,row+1,u1,v1,light,-dz/length,dy/length);vertex(out,transform,points,row,u1,v0,light,-dz/length,dy/length);
            }
        });
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose transform,float[] p,int row,float u,float v,int light,float ny,float nz){
        float side=u*2-1;
        out.addVertex(transform,(u-0.5f)*0.625f,p[row*3]+p[row*3+2]*side,p[row*3+1]+0.17f+p[row*3+2]*side*side).setColor(0xFFFFFFFF).setUv(1-u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform,0,ny,nz);
    }
}
