package runtimeenv

import "encoding/asn1"

const audioInputEntitlement = "com.apple.security.device.audio-input"

// Modern macOS may expose only DER entitlements. Inspect the signed top-level
// dictionary, not a byte substring or a localized codesign display message.
func derAudioInput(data []byte) bool {
	if len(data) == 0 || len(data) > 64<<10 {
		return false
	}
	var outer asn1.RawValue
	remaining, err := asn1.Unmarshal(data, &outer)
	if err != nil || len(remaining) != 0 || outer.Class != asn1.ClassApplication || outer.Tag != 16 || !outer.IsCompound {
		return false
	}
	var version int
	rest, err := asn1.Unmarshal(outer.Bytes, &version)
	if err != nil || version != 1 {
		return false
	}
	var dictionary asn1.RawValue
	rest, err = asn1.Unmarshal(rest, &dictionary)
	if err != nil || len(rest) != 0 || dictionary.Class != asn1.ClassContextSpecific || dictionary.Tag != 16 || !dictionary.IsCompound {
		return false
	}
	entries := dictionary.Bytes
	for len(entries) != 0 {
		var entry asn1.RawValue
		entries, err = asn1.Unmarshal(entries, &entry)
		if err != nil || entry.Class != asn1.ClassUniversal || entry.Tag != asn1.TagSequence || !entry.IsCompound {
			return false
		}
		var key string
		value, err := asn1.Unmarshal(entry.Bytes, &key)
		if err != nil {
			return false
		}
		if key == audioInputEntitlement {
			var granted bool
			tail, err := asn1.Unmarshal(value, &granted)
			return err == nil && len(tail) == 0 && granted
		}
	}
	return false
}
