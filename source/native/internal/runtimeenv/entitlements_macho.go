package runtimeenv

import (
	"debug/macho"
	"encoding/binary"
	"io"
	"os"
)

// Read the signed DER blob from the executable after Apple's codesign tool has
// verified its seal. This works on macOS 12 too, without depending on newer
// codesign display flags or on the language used for its diagnostic messages.
func signedAudioInput(path string) bool {
	reader, err := os.Open(path)
	if err != nil {
		return false
	}
	defer reader.Close()
	stat, err := reader.Stat()
	if err != nil {
		return false
	}
	if file, err := macho.NewFile(reader); err == nil {
		return sliceAudioInput(reader, file, 0, stat.Size())
	}
	fat, err := macho.NewFatFile(reader)
	if err != nil || len(fat.Arches) == 0 {
		return false
	}
	for _, arch := range fat.Arches {
		if !sliceAudioInput(reader, arch.File, int64(arch.Offset), int64(arch.Size)) {
			return false
		}
	}
	return true
}

func sliceAudioInput(reader io.ReaderAt, file *macho.File, base, size int64) bool {
	for _, load := range file.Loads {
		command := load.Raw()
		if len(command) < 16 || file.ByteOrder.Uint32(command) != 0x1d {
			continue
		}
		offset, length := int64(file.ByteOrder.Uint32(command[8:])), int64(file.ByteOrder.Uint32(command[12:]))
		if length < 12 || length > 4<<20 || offset < 0 || offset+length > size {
			return false
		}
		signature := make([]byte, int(length))
		if _, err := reader.ReadAt(signature, base+offset); err != nil {
			return false
		}
		return signatureAudioInput(signature)
	}
	return false
}

func signatureAudioInput(signature []byte) bool {
	if len(signature) < 12 || binary.BigEndian.Uint32(signature) != 0xfade0cc0 {
		return false
	}
	length, count := int(binary.BigEndian.Uint32(signature[4:])), int(binary.BigEndian.Uint32(signature[8:]))
	if length > len(signature) || length < 12 || count > 1024 || 12+count*8 > length {
		return false
	}
	for index := 0; index < count; index++ {
		entry := signature[12+index*8:]
		if binary.BigEndian.Uint32(entry) != 7 {
			continue
		} // CSSLOT_DER_ENTITLEMENTS
		offset := int(binary.BigEndian.Uint32(entry[4:]))
		if offset < 12+count*8 || offset > length-8 {
			return false
		}
		blob := signature[offset:]
		blobLength := int(binary.BigEndian.Uint32(blob[4:]))
		if binary.BigEndian.Uint32(blob) != 0xfade7172 || blobLength < 8 || blobLength > length-offset {
			return false
		}
		return derAudioInput(blob[8:blobLength])
	}
	return false
}
