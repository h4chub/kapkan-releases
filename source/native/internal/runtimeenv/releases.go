package runtimeenv

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const zuluMetadataURL = "https://api.azul.com/metadata/v1/zulu/packages/"

// macOS uses the vendor-signed runtime with audio-input capability. Keep its
// cache separate: an upgrade never edits or removes an existing Temurin runtime.
func runtimeVendor(platform string) string {
	if platform == "mac" {
		return "zulu"
	}
	return "temurin"
}

func (m Manager) usableCached(target string, major int, arch, platform string) string {
	java := cached(target, major, arch, platform)
	if java != "" && platform == "mac" && m.Metadata == nil {
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		defer cancel()
		if verifyMicrophoneJava(ctx, java) != nil {
			return ""
		}
	}
	return java
}

func (m Manager) metadataJSON(ctx context.Context, address string, into any) error {
	req, err := http.NewRequestWithContext(ctx, "GET", address, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "KapKan/1.0.7")
	resp, err := m.httpClient().Do(req)
	if err != nil {
		return fmt.Errorf("cannot reach Java download metadata: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("Java download metadata is unavailable (HTTP %d)", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, (2<<20)+1))
	if err != nil {
		return err
	}
	if len(data) > 2<<20 {
		return fmt.Errorf("Java download metadata exceeds its size limit")
	}
	if err = json.Unmarshal(data, into); err != nil {
		return fmt.Errorf("invalid Java release metadata: %w", err)
	}
	return nil
}

func (m Manager) release(ctx context.Context, major int, arch, platform string) (*Release, error) {
	if platform == "mac" && m.Metadata == nil {
		return m.zuluRelease(ctx, major, arch)
	}
	address := fmt.Sprintf(MetadataURL, major, arch, platform)
	if m.Metadata != nil {
		address = m.Metadata(major, arch, platform)
	}
	var releases []Release
	if err := m.metadataJSON(ctx, address, &releases); err != nil {
		return nil, err
	}
	for i := range releases {
		b := &releases[i]
		if b.Version.Major == major && b.Binary.Architecture == arch && b.Binary.OS == platform && b.Binary.ImageType == "jre" {
			return b, nil
		}
	}
	return nil, fmt.Errorf("no matching Java %d runtime for %s/%s", major, platform, arch)
}

type zuluPackage struct {
	Architecture string   `json:"arch"`
	Bitness      int      `json:"hw_bitness"`
	OS           string   `json:"os"`
	Kind         string   `json:"java_package_type"`
	ArchiveType  string   `json:"archive_type"`
	Availability string   `json:"availability_type"`
	Status       string   `json:"release_status"`
	Product      string   `json:"product"`
	JavaFX       bool     `json:"javafx_bundled"`
	CRaC         bool     `json:"crac_supported"`
	Features     []string `json:"java_package_features"`
	JavaVersion  []int    `json:"java_version"`
	URL          string   `json:"download_url"`
	Name         string   `json:"name"`
	Checksum     string   `json:"sha256_hash"`
	Size         int64    `json:"size"`
}

func (m Manager) zuluRelease(ctx context.Context, major int, arch string) (*Release, error) {
	query := url.Values{
		"java_version": {fmt.Sprint(major)}, "os": {"macos"}, "arch": {arch},
		"hw_bitness": {"64"}, "archive_type": {"tar.gz"}, "java_package_type": {"jre"},
		"javafx_bundled": {"false"}, "crac_supported": {"false"}, "release_status": {"ga"},
		"availability_types": {"CA"}, "latest": {"true"},
		"include_fields": {"sha256_hash,size,os,arch,hw_bitness,javafx_bundled,release_status,java_package_type,archive_type,crac_supported,java_package_features"},
	}
	var packages []zuluPackage
	if err := m.metadataJSON(ctx, zuluMetadataURL+"?"+query.Encode(), &packages); err != nil {
		return nil, err
	}
	for _, p := range packages {
		expectedArch := p.Architecture == "arm" && arch == "aarch64" || p.Architecture == "x86" && arch == "x64"
		if !expectedArch || p.Bitness != 64 || p.OS != "macos" || p.Kind != "jre" ||
			p.ArchiveType != "tar.gz" || p.Availability != "CA" || p.Status != "ga" ||
			p.Product != "zulu" || p.JavaFX || p.CRaC || len(p.JavaVersion) == 0 || p.JavaVersion[0] != major {
			continue
		}
		headful := false
		for _, feature := range p.Features {
			if feature == "headfull" || feature == "headful" {
				headful = true
			}
		}
		if !headful || !strings.HasSuffix(p.Name, ".tar.gz") {
			continue
		}
		r := &Release{}
		r.Binary.Architecture, r.Binary.OS, r.Binary.ImageType = arch, "mac", "jre"
		r.Binary.Package.Checksum, r.Binary.Package.Link = p.Checksum, p.URL
		r.Binary.Package.Name, r.Binary.Package.Size = p.Name, p.Size
		// The official API currently reports 56496600 for a 56496636-byte
		// archive. Bound the rounding difference, but always require its exact
		// SHA-256 before any extraction or execution.
		r.Binary.Package.SizeTolerance = 1024
		r.Version.Major, r.ReleaseName = major, strings.TrimSuffix(p.Name, ".tar.gz")
		return r, nil
	}
	return nil, fmt.Errorf("no matching microphone-capable Java %d runtime for mac/%s", major, arch)
}
