package installpkg

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"os"
	"path/filepath"
	"testing"
)

// Exercises upgrade publication with the real release payload on the test host.
// This does not simulate Windows UI, registry integration or native gameplay.
func TestBuiltPayloadUpgrade(t *testing.T) {
	payload := os.Getenv("KAPKAN_TEST_PAYLOAD")
	if payload == "" {
		t.Skip("release payload not supplied")
	}
	data, err := os.ReadFile(payload)
	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	expected := hex.EncodeToString(sum[:])
	home := t.TempDir()
	target := filepath.Join(home, "Local", "Programs", "KapKan")
	old, oldHash := makePayload(t, "old-install-sentinel.txt")
	if _, err := Publish(old, oldHash, target, "1.0.4"); err != nil {
		t.Fatal(err)
	}
	game := filepath.Join(home, ".kapkan", "game")
	for _, rel := range []string{"mods/user.jar", "saves/world/level.dat", "options.txt"} {
		p := filepath.Join(game, filepath.FromSlash(rel))
		if err := os.MkdirAll(filepath.Dir(p), 0700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("keep "+rel), 0600); err != nil {
			t.Fatal(err)
		}
	}
	backup, err := Publish(payload, expected, target, "1.0.5")
	if err != nil {
		t.Fatal(err)
	}
	if !Owned(target) || !Owned(backup) {
		t.Fatal("application or backup receipt missing")
	}
	if _, err := os.Stat(filepath.Join(backup, "old-install-sentinel.txt")); err != nil {
		t.Fatal(err)
	}
	archive, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range archive.File {
		if entry.FileInfo().IsDir() {
			continue
		}
		in, err := entry.Open()
		if err != nil {
			t.Fatal(err)
		}
		want, err := io.ReadAll(in)
		in.Close()
		if err != nil {
			t.Fatal(err)
		}
		got, err := os.ReadFile(filepath.Join(target, filepath.FromSlash(entry.Name)))
		if err != nil || !bytes.Equal(want, got) {
			t.Fatalf("installed payload differs: %s", entry.Name)
		}
	}
	jar, err := zip.OpenReader(filepath.Join(target, "Resources", "KapKan-Launcher.jar"))
	if err != nil {
		t.Fatal(err)
	}
	defer jar.Close()
	var fixed bool
	for _, entry := range jar.File {
		if entry.Name == "META-INF/MANIFEST.MF" {
			in, err := entry.Open()
			if err != nil {
				t.Fatal(err)
			}
			manifest, err := io.ReadAll(in)
			in.Close()
			if err != nil {
				t.Fatal(err)
			}
			fixed = bytes.Contains(manifest, []byte("KapKan-Hotfix: fabric-dependency-recovery-3"))
		}
	}
	if !fixed {
		t.Fatal("installer ships a stale launcher without the Fabric fix")
	}
	for _, rel := range []string{"mods/user.jar", "saves/world/level.dat", "options.txt"} {
		got, err := os.ReadFile(filepath.Join(game, filepath.FromSlash(rel)))
		if err != nil || string(got) != "keep "+rel {
			t.Fatalf("game data changed: %s", rel)
		}
	}
}
