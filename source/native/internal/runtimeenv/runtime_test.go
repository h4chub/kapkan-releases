package runtimeenv

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestArchitectureAliases(t *testing.T) {
	for k, v := range map[string]string{"arm64": "aarch64", "aarch64": "aarch64", "amd64": "x64", "x86_64": "x64", "x64": "x64"} {
		got, e := Arch(k)
		if e != nil || got != v {
			t.Fatalf("%s: %s %v", k, got, e)
		}
	}
	if _, e := Arch("386"); e == nil {
		t.Fatal("32-bit architecture accepted")
	}
}
func makeTar(t *testing.T, entries []*tar.Header, body [][]byte) []byte {
	t.Helper()
	var b bytes.Buffer
	gz := gzip.NewWriter(&b)
	tw := tar.NewWriter(gz)
	for i, h := range entries {
		if e := tw.WriteHeader(h); e != nil {
			t.Fatal(e)
		}
		if i < len(body) && body[i] != nil {
			tw.Write(body[i])
		}
	}
	tw.Close()
	gz.Close()
	return b.Bytes()
}
func TestTarTraversal(t *testing.T) {
	for _, name := range []string{"../escape", "/tmp/escape", "C:\\escape", "a/../../escape"} {
		t.Run(name, func(t *testing.T) {
			b := makeTar(t, []*tar.Header{{Name: name, Mode: 0644, Size: 1, Typeflag: tar.TypeReg}}, [][]byte{{1}})
			root := t.TempDir()
			a := filepath.Join(root, "bad.tgz")
			os.WriteFile(a, b, 0600)
			if e := ExtractTarGz(a, filepath.Join(root, "out")); e == nil {
				t.Fatal("unsafe path accepted")
			}
		})
	}
}
func TestTarSymlinkEscape(t *testing.T) {
	b := makeTar(t, []*tar.Header{{Name: "bundle/link", Linkname: "../../outside", Typeflag: tar.TypeSymlink}}, nil)
	root := t.TempDir()
	a := filepath.Join(root, "bad.tgz")
	os.WriteFile(a, b, 0600)
	if e := ExtractTarGz(a, filepath.Join(root, "out")); e == nil {
		t.Fatal("escaping symlink accepted")
	}
}
func TestZipTraversal(t *testing.T) {
	var b bytes.Buffer
	z := zip.NewWriter(&b)
	f, _ := z.Create("../../bad")
	f.Write([]byte("bad"))
	z.Close()
	root := t.TempDir()
	a := filepath.Join(root, "bad.zip")
	os.WriteFile(a, b.Bytes(), 0600)
	if e := ExtractZip(a, filepath.Join(root, "out")); e == nil {
		t.Fatal("ZIP traversal accepted")
	}
}
func TestArchiveLimits(t *testing.T) {
	var total int64
	if e := regular(t.TempDir(), "x", MaxExtracted+1, 0644, bytes.NewReader(nil), &total); e == nil {
		t.Fatal("size limit ignored")
	}
}
func TestInstallVerifyReuseAndRepair(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("test VM executable is a POSIX fixture")
	}
	arch, _ := Arch(runtime.GOARCH)
	script := []byte(fmt.Sprintf("#!/bin/sh\nprintf '    java.specification.version = 21\\n    os.arch = %s\\n' >&2\n", runtime.GOARCH))
	payload := makeTar(t, []*tar.Header{{Name: "jdk/Contents/Home/bin/java", Mode: 0755, Size: int64(len(script)), Typeflag: tar.TypeReg}}, [][]byte{script})
	sum := sha256.Sum256(payload)
	sha := hex.EncodeToString(sum[:])
	downloads := 0
	metadata := 0
	badChecksum := false
	var srv *httptest.Server
	srv = httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/runtime.tar.gz" {
			downloads++
			w.Write(payload)
			return
		}
		metadata++
		checksum := sha
		if badChecksum {
			checksum = strings.Repeat("a", 64)
		}
		fmt.Fprintf(w, `[{"binary":{"architecture":%q,"os":%q,"image_type":"jre","package":{"checksum":%q,"link":%q,"name":"runtime.tar.gz","size":%d}},"release_name":"test-fixture","version":{"major":21}}]`, arch, Platform(), checksum, srv.URL+"/runtime.tar.gz", len(payload))
	}))
	defer srv.Close()
	root := t.TempDir()
	m := Manager{Root: filepath.Join(root, "runtimes"), Client: srv.Client(), Metadata: func(int, string, string) string { return srv.URL + "/metadata" }}
	sentinel := filepath.Join(root, "game", "mods", "keep.txt")
	os.MkdirAll(filepath.Dir(sentinel), 0755)
	os.WriteFile(sentinel, []byte("unchanged"), 0600)
	j, e := m.Ensure(context.Background(), 21, arch, nil)
	if e != nil {
		t.Fatal(e)
	}
	if downloads != 1 || metadata != 1 {
		t.Fatal("unexpected first-install request count")
	}
	again, e := m.Ensure(context.Background(), 21, arch, nil)
	if e != nil || again != j || downloads != 1 || metadata != 1 {
		t.Fatalf("cache was not reused: %s %v", again, e)
	}
	os.WriteFile(j, []byte("broken"), 0755)
	repaired, e := m.Ensure(context.Background(), 21, arch, nil)
	if e != nil || repaired != j || downloads != 2 {
		t.Fatalf("cache repair failed: %s %v", repaired, e)
	}
	if b, e := os.ReadFile(sentinel); e != nil || string(b) != "unchanged" {
		t.Fatal("game data modified")
	}
	// A mismatched hash never publishes a managed runtime.
	m.Root = filepath.Join(root, "bad-runtimes")
	badChecksum = true
	if _, e = m.Ensure(context.Background(), 21, arch, nil); e == nil {
		t.Fatal("bad checksum accepted")
	}
	matches, _ := filepath.Glob(filepath.Join(m.Root, runtimeVendor(Platform())+"-*"))
	if len(matches) != 0 {
		t.Fatal("invalid archive was published")
	}
}
func TestCachedReceiptCannotEscape(t *testing.T) {
	dir := t.TempDir()
	b, _ := json.Marshal(Receipt{Major: 21, Architecture: "x64", OS: Platform(), Java: "../../elsewhere"})
	os.WriteFile(filepath.Join(dir, "kapkan-runtime.json"), b, 0600)
	if cached(dir, 21, "x64", Platform()) != "" {
		t.Fatal("receipt escaped root")
	}
}
