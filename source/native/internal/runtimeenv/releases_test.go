package runtimeenv

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"path/filepath"
	"strings"
	"testing"
)

type metadataTransport func(*http.Request) (*http.Response, error)

func (f metadataTransport) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestMacRuntimeProviderUsesOfficialChecksumAndCorrectArchitecture(t *testing.T) {
	for arch, vendorArch := range map[string]string{"aarch64": "arm", "x64": "x86"} {
		t.Run(arch, func(t *testing.T) {
			body := `[{"arch":"` + vendorArch + `","hw_bitness":64,"os":"macos","java_package_type":"jre","archive_type":"tar.gz","availability_type":"CA","release_status":"ga","product":"zulu","java_package_features":["headfull"],"java_version":[25,0,4,1],"name":"zulu-runtime.tar.gz","download_url":"https://cdn.azul.com/zulu/bin/zulu-runtime.tar.gz","sha256_hash":"` + strings.Repeat("a", 64) + `","size":12345}]`
			m := Manager{Client: &http.Client{Transport: metadataTransport(func(r *http.Request) (*http.Response, error) {
				if r.URL.Scheme != "https" || r.URL.Host != "api.azul.com" || r.URL.Path != "/metadata/v1/zulu/packages/" {
					t.Fatalf("unexpected metadata endpoint: %s", r.URL)
				}
				q := r.URL.Query()
				for key, expected := range map[string]string{"java_version": "25", "os": "macos", "arch": arch, "hw_bitness": "64", "java_package_type": "jre", "javafx_bundled": "false", "release_status": "ga", "availability_types": "CA"} {
					if q.Get(key) != expected {
						t.Fatalf("%s filter missing", key)
					}
				}
				return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(strings.NewReader(body)), Header: http.Header{}}, nil
			})}}
			release, err := m.release(context.Background(), 25, arch, "mac")
			if err != nil {
				t.Fatal(err)
			}
			if release.Binary.Architecture != arch || release.Binary.OS != "mac" || release.Version.Major != 25 || release.Binary.Package.Checksum != strings.Repeat("a", 64) || release.Binary.Package.Size != 12345 {
				t.Fatal("vendor metadata lost its verified runtime identity/checksum")
			}
		})
	}
}

func TestMacRuntimeProviderRejectsIncompatibleBuilds(t *testing.T) {
	base := `{"arch":"arm","hw_bitness":64,"os":"macos","java_package_type":"jre","archive_type":"tar.gz","availability_type":"CA","release_status":"ga","product":"zulu","java_package_features":["headfull"],"java_version":[25],"name":"zulu.tar.gz"}`
	for _, body := range []string{
		strings.ReplaceAll(base, `"arm"`, `"x86"`),
		strings.ReplaceAll(base, `64`, `32`),
		strings.ReplaceAll(base, `"macos"`, `"windows"`),
		strings.ReplaceAll(base, `"jre"`, `"jdk"`),
		strings.ReplaceAll(base, `"ga"`, `"ea"`),
		strings.ReplaceAll(base, `"CA"`, `"SA"`),
		strings.ReplaceAll(base, `[25]`, `[21]`),
		strings.ReplaceAll(base, `"headfull"`, `"headless"`),
	} {
		m := Manager{Client: &http.Client{Transport: metadataTransport(func(r *http.Request) (*http.Response, error) {
			return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(strings.NewReader("[" + body + "]")), Header: http.Header{}}, nil
		})}}
		if _, err := m.release(context.Background(), 25, "aarch64", "mac"); err == nil {
			t.Fatal("incompatible macOS runtime accepted")
		}
	}
}

func TestRuntimeVendorsHaveSeparateCaches(t *testing.T) {
	if runtimeVendor("mac") != "zulu" || runtimeVendor("windows") != "temurin" {
		t.Fatal("runtime vendor migration lost its platform boundary")
	}
}

func TestRoundedVendorSizeStillRequiresExactChecksum(t *testing.T) {
	payload := strings.Repeat("verified fixture", 128)
	digest := sha256.Sum256([]byte(payload))
	checksum := hex.EncodeToString(digest[:])
	m := Manager{Client: &http.Client{Transport: metadataTransport(func(r *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(strings.NewReader(payload)), Header: http.Header{}}, nil
	})}}
	root := t.TempDir()
	if err := m.download(context.Background(), "https://cdn.azul.com/test", filepath.Join(root, "rounded"), int64(len(payload))-36, checksum, 1024); err != nil {
		t.Fatal(err)
	}
	if err := m.download(context.Background(), "https://cdn.azul.com/test", filepath.Join(root, "corrupt"), int64(len(payload))-36, strings.Repeat("a", 64), 1024); err == nil {
		t.Fatal("rounded size bypassed SHA-256 verification")
	}
	if err := m.download(context.Background(), "https://cdn.azul.com/test", filepath.Join(root, "wrong-size"), int64(len(payload))-1025, checksum, 1024); err == nil {
		t.Fatal("unbounded size mismatch accepted")
	}
	if err := m.download(context.Background(), "https://example.test/test", filepath.Join(root, "exact"), int64(len(payload))-1, checksum, 0); err == nil {
		t.Fatal("Adoptium exact size check weakened")
	}
}
