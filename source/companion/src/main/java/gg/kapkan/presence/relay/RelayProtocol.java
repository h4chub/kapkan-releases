package gg.kapkan.presence.relay;

import gg.kapkan.presence.CapeData;
import java.io.*;
import java.util.*;

/** Versioned, bounded transport. UUIDs are present only in server-authored messages. */
public final class RelayProtocol {
    public static final int VERSION=1, CHUNK=24*1024, MAX_PACKET=CHUNK+256, MAX_PLAYERS=256;
    public sealed interface Message permits Offer,Upload,Request,State,Chunk,Gone {}
    public record Offer(int revision,boolean badge,String hash,String material,int length) implements Message {}
    public record Upload(int revision,int offset,byte[] bytes) implements Message {}
    public record Request(String hash) implements Message {}
    public record State(UUID uuid,int revision,boolean badge,String hash,String material,int length) implements Message {}
    public record Chunk(String hash,int offset,byte[] bytes) implements Message {}
    public record Gone(UUID uuid) implements Message {}
    private RelayProtocol(){}
    public static boolean material(String value){return Set.of("Flow","Silk","Heavy").contains(value);}
    private static void appearance(String hash,String material,int length)throws IOException {
        if(!material(material)||!(hash.isEmpty()&&length==0||hash.matches("[a-f0-9]{64}")&&length>=45&&length<=CapeData.MAX_BYTES))throw new IOException("Invalid appearance");
    }
    public static byte[] encode(Message message)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(VERSION);
        switch(message){
            case Offer m->{out.writeByte(1);out.writeInt(m.revision);out.writeBoolean(m.badge);writeAppearance(out,m.hash,m.material,m.length);}
            case Upload m->{out.writeByte(2);out.writeInt(m.revision);out.writeInt(m.offset);writeChunk(out,m.bytes);}
            case Request m->{out.writeByte(3);writeHash(out,m.hash);}
            case State m->{out.writeByte(4);writeUuid(out,m.uuid);out.writeInt(m.revision);out.writeBoolean(m.badge);writeAppearance(out,m.hash,m.material,m.length);}
            case Chunk m->{out.writeByte(5);writeHash(out,m.hash);out.writeInt(m.offset);writeChunk(out,m.bytes);}
            case Gone m->{out.writeByte(6);writeUuid(out,m.uuid);}
        }
        out.flush();return bytes.toByteArray();
    }
    public static Message decode(byte[] bytes)throws IOException {
        if(bytes.length>MAX_PACKET)throw new IOException("Packet limit");var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readUnsignedByte()!=VERSION)throw new IOException("Protocol version");Message value=switch(in.readUnsignedByte()){
            case 1->new Offer(in.readInt(),in.readBoolean(),in.readUTF(),in.readUTF(),in.readInt());
            case 2->new Upload(in.readInt(),in.readInt(),readChunk(in));
            case 3->new Request(in.readUTF());
            case 4->new State(readUuid(in),in.readInt(),in.readBoolean(),in.readUTF(),in.readUTF(),in.readInt());
            case 5->new Chunk(in.readUTF(),in.readInt(),readChunk(in));
            case 6->new Gone(readUuid(in));
            default->throw new IOException("Packet type");};
        if(in.available()!=0)throw new IOException("Trailing packet data");
        switch(value){
            case Offer m->appearance(m.hash,m.material,m.length);
            case State m->appearance(m.hash,m.material,m.length);
            case Request m->{if(!m.hash.matches("[a-f0-9]{64}"))throw new IOException("Hash");}
            case Chunk m->{if(!m.hash.matches("[a-f0-9]{64}")||m.offset<0||m.offset>CapeData.MAX_BYTES)throw new IOException("Chunk offset");}
            case Upload m->{if(m.offset<0||m.offset>CapeData.MAX_BYTES)throw new IOException("Upload offset");}
            default->{}
        }
        return value;
    }
    private static void writeAppearance(DataOutputStream out,String hash,String material,int length)throws IOException{appearance(hash,material,length);out.writeUTF(hash);out.writeUTF(material);out.writeInt(length);}
    private static void writeHash(DataOutputStream out,String hash)throws IOException{if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Hash");out.writeUTF(hash);}
    private static void writeChunk(DataOutputStream out,byte[] bytes)throws IOException{if(bytes.length<1||bytes.length>CHUNK)throw new IOException("Chunk limit");out.writeShort(bytes.length);out.write(bytes);}
    private static byte[] readChunk(DataInputStream in)throws IOException{int n=in.readUnsignedShort();if(n<1||n>CHUNK||n>in.available())throw new IOException("Chunk limit");return in.readNBytes(n);}
    private static void writeUuid(DataOutputStream out,UUID uuid)throws IOException{out.writeLong(uuid.getMostSignificantBits());out.writeLong(uuid.getLeastSignificantBits());}
    private static UUID readUuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
}
