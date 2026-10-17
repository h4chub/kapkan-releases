package runtimeenv

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"testing"
)

// Opt-in, read-only native acceptance check. This never asks for microphone
// permission or records audio. Point at an unchanged, vendor-signed executable.
func TestVendorSignedMacRuntime(t *testing.T) {
	java := os.Getenv("KAPKAN_TEST_MAC_JAVA")
	if java == "" {
		t.Skip("set KAPKAN_TEST_MAC_JAVA for vendor signature/capability acceptance")
	}
	if err := verifyMicrophoneJava(context.Background(), java); err != nil {
		t.Fatal(err)
	}
}

func TestVendorRuntimeWithoutAudioCapabilityIsRejected(t *testing.T) {
	java := os.Getenv("KAPKAN_TEST_MAC_UNSUPPORTED_JAVA")
	if java == "" {
		t.Skip("set unchanged vendor runtime without audio-input for negative acceptance")
	}
	if err := verifyMicrophoneJava(context.Background(), java); err == nil || !strings.Contains(err.Error(), "microphone support") {
		t.Fatalf("runtime without signed audio-input was not rejected for the capability: %v", err)
	}
}

// Exercise the complete production provider -> SHA-256 download -> extraction
// -> native Java execution -> vendor signature/capability -> cache reuse path.
// Fixture files are unchanged official archives and their saved HTTPS metadata;
// everything is installed into a temporary directory, never the user's cache.
func TestNativeMacRuntimeInstall(t *testing.T) {
	archivePath, metadataPath := os.Getenv("KAPKAN_TEST_MAC_ARCHIVE"), os.Getenv("KAPKAN_TEST_MAC_METADATA")
	if archivePath == "" || metadataPath == "" {
		t.Skip("set official macOS archive/metadata fixtures for installation acceptance")
	}
	major, err := strconv.Atoi(os.Getenv("KAPKAN_TEST_MAC_MAJOR"))
	if err != nil {
		t.Fatal("KAPKAN_TEST_MAC_MAJOR is required")
	}
	metadata, err := os.ReadFile(metadataPath)
	if err != nil {
		t.Fatal(err)
	}
	var packages []zuluPackage
	if err = json.Unmarshal(metadata, &packages); err != nil || len(packages) == 0 {
		t.Fatal("invalid official metadata fixture")
	}
	arch, err := Arch(runtime.GOARCH)
	if err != nil {
		t.Fatal(err)
	}
	downloads := 0
	manager := Manager{Root: t.TempDir(), Client: &http.Client{Transport: metadataTransport(func(r *http.Request) (*http.Response, error) {
		var body io.ReadCloser
		var size int64
		if r.URL.Host == "api.azul.com" {
			body, size = io.NopCloser(bytes.NewReader(metadata)), int64(len(metadata))
		} else if r.URL.String() == packages[0].URL {
			downloads++
			file, err := os.Open(archivePath)
			if err != nil {
				return nil, err
			}
			body, size = file, packages[0].Size
		} else {
			return nil, fmt.Errorf("unexpected acceptance-test request: %s", r.URL)
		}
		return &http.Response{StatusCode: http.StatusOK, Header: http.Header{}, Body: body, ContentLength: size}, nil
	})}}
	legacy := filepath.Join(manager.Root, fmt.Sprintf("temurin-%d-mac-%s", major, arch), "keep.txt")
	if err = os.MkdirAll(filepath.Dir(legacy), 0700); err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(legacy, []byte("existing runtime untouched"), 0600); err != nil {
		t.Fatal(err)
	}
	java, err := manager.Ensure(context.Background(), major, arch, nil)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(java, fmt.Sprintf("zulu-%d-mac-%s", major, arch)) {
		t.Fatal("old provider cache was selected")
	}
	again, err := manager.Ensure(context.Background(), major, arch, nil)
	if err != nil || again != java || downloads != 1 {
		t.Fatalf("verified vendor cache not reused: %s %v downloads=%d", again, err, downloads)
	}
	existing, err := os.ReadFile(legacy)
	if err != nil || string(existing) != "existing runtime untouched" {
		t.Fatal("legacy runtime changed")
	}
}
