package runtimeenv

import (
	"encoding/asn1"
	"encoding/binary"
	"testing"
)

func signedEntitlementFixture(key string, granted bool) []byte {
	k, _ := asn1.MarshalWithParams(key, "utf8")
	v, _ := asn1.Marshal(granted)
	entry, _ := asn1.Marshal(asn1.RawValue{Class: asn1.ClassUniversal, Tag: asn1.TagSequence, IsCompound: true, Bytes: append(k, v...)})
	dict, _ := asn1.Marshal(asn1.RawValue{Class: asn1.ClassContextSpecific, Tag: 16, IsCompound: true, Bytes: entry})
	version, _ := asn1.Marshal(1)
	data, _ := asn1.Marshal(asn1.RawValue{Class: asn1.ClassApplication, Tag: 16, IsCompound: true, Bytes: append(version, dict...)})
	return data
}

func signatureFixture(der []byte) []byte {
	data := make([]byte, 28+len(der))
	binary.BigEndian.PutUint32(data, 0xfade0cc0)
	binary.BigEndian.PutUint32(data[4:], uint32(len(data)))
	binary.BigEndian.PutUint32(data[8:], 1)
	binary.BigEndian.PutUint32(data[12:], 7)
	binary.BigEndian.PutUint32(data[16:], 20)
	binary.BigEndian.PutUint32(data[20:], 0xfade7172)
	binary.BigEndian.PutUint32(data[24:], uint32(8+len(der)))
	copy(data[28:], der)
	return data
}

func TestSignatureMicrophoneSlotBoundsAndValue(t *testing.T) {
	valid := signatureFixture(signedEntitlementFixture(audioInputEntitlement, true))
	if !signatureAudioInput(valid) {
		t.Fatal("signed microphone DER slot was not read")
	}
	if signatureAudioInput(signatureFixture(signedEntitlementFixture(audioInputEntitlement, false))) {
		t.Fatal("false microphone capability accepted")
	}
	if signatureAudioInput(signatureFixture(signedEntitlementFixture("other.entitlement", true))) {
		t.Fatal("missing microphone capability accepted")
	}
	for length := 0; length < len(valid); length++ {
		if signatureAudioInput(valid[:length]) {
			t.Fatal("truncated signature accepted")
		}
	}
	for offset, value := range map[int]uint32{0: 0, 4: 0xffffffff, 8: 0xffffffff, 12: 5, 16: 0xffffffff, 20: 0, 24: 0xffffffff} {
		altered := append([]byte(nil), valid...)
		binary.BigEndian.PutUint32(altered[offset:], value)
		if signatureAudioInput(altered) {
			t.Fatal("altered/absent DER slot accepted")
		}
	}
}

func TestSignedMicrophoneEntitlement(t *testing.T) {
	if !derAudioInput(signedEntitlementFixture(audioInputEntitlement, true)) {
		t.Fatal("valid audio-input capability not recognized")
	}
	for _, data := range [][]byte{
		nil,
		[]byte(audioInputEntitlement),
		signedEntitlementFixture(audioInputEntitlement, false),
		signedEntitlementFixture("com.apple.security.cs.allow-jit", true),
		append(signedEntitlementFixture(audioInputEntitlement, true), 0),
	} {
		if derAudioInput(data) {
			t.Fatal("missing, false or malformed microphone entitlement accepted")
		}
	}
	valid := signedEntitlementFixture(audioInputEntitlement, true)
	for length := 0; length < len(valid); length++ {
		if derAudioInput(valid[:length]) {
			t.Fatal("truncated entitlements accepted")
		}
	}
}
