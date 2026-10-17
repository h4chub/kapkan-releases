package gg.kapkan.presence.relay;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RelayPayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<RelayPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("kapkan","cosmetics_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RelayPayload> CODEC=StreamCodec.of(
        (buffer,payload)->buffer.writeByteArray(payload.bytes),buffer->new RelayPayload(buffer.readByteArray(RelayProtocol.MAX_PACKET)));
    @Override public Type<RelayPayload> type(){return TYPE;}
    public static RelayPayload of(RelayProtocol.Message message){try{return new RelayPayload(RelayProtocol.encode(message));}catch(java.io.IOException invalid){throw new IllegalArgumentException(invalid);}}
}
