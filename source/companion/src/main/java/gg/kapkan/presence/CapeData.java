package gg.kapkan.presence;

import java.awt.image.BufferedImage;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import java.nio.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** A bounded image, identified by its content. Never a URL or a path supplied by another player. */
public final class CapeData {
    public static final int MAX_BYTES=768*1024;
    private final byte[] png;
    private final String hash;
    private CapeData(byte[] png,String hash){this.png=png.clone();this.hash=hash;}
    public String hash(){return hash;}
    public int size(){return png.length;}
    public byte[] png(){return png.clone();}
    public String base64(){return Base64.getEncoder().encodeToString(png);}
    public static CapeData fromPng(byte[] png)throws IOException{return fromPng(png,digest(png));}
    public static CapeData fromPng(byte[] png,String expected)throws IOException{
        if(png.length>MAX_BYTES||png.length<45||!expected.matches("[a-f0-9]{64}")||!digest(png).equals(expected))throw new IOException("Invalid cape data");
        validateChunks(png);
        try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(png))){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Invalid cape image");var reader=readers.next();
            try{reader.setInput(stream);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)!=320||reader.getHeight(0)!=512)throw new IOException("Invalid cape dimensions");
                BufferedImage image=reader.read(0);if(image==null)throw new IOException("Unreadable cape");
            }finally{reader.dispose();}
        }
        return new CapeData(png,expected);
    }
    private static void validateChunks(byte[] png)throws IOException{
        ByteBuffer b=ByteBuffer.wrap(png);if(b.getLong()!=0x89504E470D0A1A0AL)throw new IOException("Not PNG");
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();boolean header=false,end=false;
        while(b.remaining()>=12){int length=b.getInt(),start=b.position();if(length<0||length>b.remaining()-8)throw new IOException("Invalid PNG chunk");
            int kind=b.getInt();byte[] data=new byte[length];b.get(data);long crc=Integer.toUnsignedLong(b.getInt());CRC32 check=new CRC32();check.update(png,start,length+4);if(check.getValue()!=crc)throw new IOException("Invalid PNG checksum");
            if(kind==0x49484452&&!header&&start==12&&length==13){ByteBuffer h=ByteBuffer.wrap(data);if(h.getInt()!=320||h.getInt()!=512||h.get()!=8||h.get()!=6||h.get()!=0||h.get()!=0||h.get()!=0)throw new IOException("Expected static RGBA cape");header=true;}
            else if(kind==0x49444154&&header)compressed.writeBytes(data);
            else if(kind==0x49454E44&&length==0){end=true;break;}
            else throw new IOException("Unsupported PNG chunk");
        }
        if(!end||b.hasRemaining())throw new IOException("Incomplete PNG");
        Inflater inflater=new Inflater();inflater.setInput(compressed.toByteArray());byte[] pixels=new byte[(320*4+1)*512+1];int count=0;
        try{while(!inflater.finished()&&count<pixels.length){int n=inflater.inflate(pixels,count,pixels.length-count);if(n==0)throw new IOException("Invalid PNG compression");count+=n;}
            if(!inflater.finished()||count!=pixels.length-1||inflater.getRemaining()!=0)throw new IOException("Invalid PNG pixel data");
            for(int row=0;row<512;row++)if((pixels[row*(320*4+1)]&255)>4)throw new IOException("Invalid PNG filter");
        }catch(DataFormatException e){throw new IOException("Invalid PNG compression",e);}finally{inflater.end();}
    }
    public static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
