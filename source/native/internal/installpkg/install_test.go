package installpkg

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"testing"
)

func makePayload(t *testing.T, extra string) (string, string) {
	t.Helper()
	p := filepath.Join(t.TempDir(), "payload.zip")
	f, _ := os.Create(p)
	w := zip.NewWriter(f)
	for _, n := range []string{"KapKan.exe", "Resources/KapKan-Launcher.jar", "Resources/KapKan-AppIcon.png", extra} {
		if n == "" {
			continue
		}
		z, _ := w.Create(n)
		z.Write([]byte("test fixture"))
	}
	w.Close()
	f.Close()
	b, _ := os.ReadFile(p)
	h := sha256.Sum256(b)
	return p, hex.EncodeToString(h[:])
}
func TestInstallUpgradePreserveAndReject(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(dir, "Programs", "KapKan")
	game := filepath.Join(dir, ".kapkan", "game", "mods")
	os.MkdirAll(game, 0755)
	sentinel := filepath.Join(game, "keep.jar")
	os.WriteFile(sentinel, []byte("unchanged"), 0600)
	p, h := makePayload(t, "")
	if _, e := Publish(p, h, target, "test-1"); e != nil {
		t.Fatal(e)
	}
	if !Owned(target) {
		t.Fatal("missing marker")
	}
	os.WriteFile(filepath.Join(target, "old-only"), []byte("old"), 0600)
	back, e := Publish(p, h, target, "test-2")
	if e != nil {
		t.Fatal(e)
	}
	if !Owned(back) {
		t.Fatal("previous program not backed up")
	}
	if _, e = os.Stat(filepath.Join(back, "old-only")); e != nil {
		t.Fatal("backup lost old files")
	}
	if _, e = os.Stat(filepath.Join(target, "old-only")); !os.IsNotExist(e) {
		t.Fatal("mixed stale files into new release")
	}
	if b, _ := os.ReadFile(sentinel); string(b) != "unchanged" {
		t.Fatal("game data changed")
	}
	if _, e = Publish(p, "wrong", target, "test-3"); e == nil {
		t.Fatal("accepted bad checksum")
	}
	if !Owned(target) {
		t.Fatal("bad update broke previous install")
	}
	unknown := filepath.Join(t.TempDir(), "KapKan")
	os.Mkdir(unknown, 0755)
	os.WriteFile(filepath.Join(unknown, "keep"), []byte("x"), 0600)
	if _, e = Publish(p, h, unknown, "test"); e == nil {
		t.Fatal("overwrote unmanaged dir")
	}
}
func TestTraversalAndSymlink(t *testing.T) {
	for _, s := range []string{"../outside", "/outside", "C:/outside", "sub/../../outside"} {
		p, h := makePayload(t, s)
		target := filepath.Join(t.TempDir(), "KapKan")
		if _, e := Publish(p, h, target, "test"); e == nil {
			t.Fatal("accepted", s)
		}
	}
	p, h := makePayload(t, "")
	dir := t.TempDir()
	target := filepath.Join(dir, "KapKan")
	outside := t.TempDir()
	os.Symlink(outside, target)
	if _, e := Publish(p, h, target, "test"); e == nil {
		t.Fatal("accepted symlink dest")
	}
}
