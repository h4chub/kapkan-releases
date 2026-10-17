// Package installpkg publishes an application directory without touching game data.
package installpkg

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const Marker = ".kapkan-install.json"
const Product = "gg.kapkan.launcher"

type Receipt struct {
	Product      string `json:"product"`
	Version      string `json:"version"`
	InstalledUTC string `json:"installed_utc"`
}

func Owned(path string) bool {
	st, err := os.Lstat(path)
	if err != nil || !st.IsDir() || st.Mode()&os.ModeSymlink != 0 {
		return false
	}
	b, err := os.ReadFile(filepath.Join(path, Marker))
	if err != nil {
		return false
	}
	var r Receipt
	return json.Unmarshal(b, &r) == nil && r.Product == Product
}

// Publish keeps any previous managed application as a dated backup. No overwrite of unknown folders.
func Publish(payload, expectedHash, target, version string) (string, error) {
	target, err := filepath.Abs(target)
	if err != nil {
		return "", err
	}
	if filepath.Base(target) != "KapKan" {
		return "", errors.New("unexpected application folder")
	}
	parent := filepath.Dir(target)
	if err = os.MkdirAll(parent, 0755); err != nil {
		return "", err
	}
	lock := target + ".install-lock"
	if err = os.Mkdir(lock, 0700); err != nil {
		return "", errors.New("another installation is running; finish it before retrying")
	}
	defer os.Remove(lock)
	if _, err = os.Lstat(target); err == nil && !Owned(target) {
		return "", errors.New("an unmanaged KapKan folder already exists; rename that application folder before installing (do not delete your .kapkan game folder)")
	} else if err != nil && !os.IsNotExist(err) {
		return "", err
	}
	f, err := os.Open(payload)
	if err != nil {
		return "", err
	}
	h := sha256.New()
	_, err = io.Copy(h, f)
	f.Close()
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(hex.EncodeToString(h.Sum(nil)), expectedHash) {
		return "", errors.New("installer payload checksum mismatch")
	}
	stage, err := os.MkdirTemp(parent, ".KapKan-stage-")
	if err != nil {
		return "", err
	}
	defer os.RemoveAll(stage)
	if err = Extract(payload, stage); err != nil {
		return "", err
	}
	for _, rel := range []string{"KapKan.exe", "Resources/KapKan-Launcher.jar", "Resources/KapKan-AppIcon.png"} {
		st, e := os.Stat(filepath.Join(stage, filepath.FromSlash(rel)))
		if e != nil || st.Size() == 0 {
			return "", fmt.Errorf("installer is missing %s", rel)
		}
	}
	b, _ := json.MarshalIndent(Receipt{Product, version, time.Now().UTC().Format(time.RFC3339)}, "", "  ")
	if err = os.WriteFile(filepath.Join(stage, Marker), b, 0600); err != nil {
		return "", err
	}
	backup := ""
	if Owned(target) {
		backup = target + ".backup-" + time.Now().UTC().Format("20060102-150405.000000000")
		if err = os.Rename(target, backup); err != nil {
			return "", fmt.Errorf("close KapKan before updating: %w", err)
		}
	}
	if err = os.Rename(stage, target); err != nil {
		if backup != "" {
			_ = os.Rename(backup, target)
		}
		return "", err
	}
	return backup, nil
}
func Extract(archive, dest string) error {
	z, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer z.Close()
	if len(z.File) > 20000 {
		return errors.New("too many application files")
	}
	var total uint64
	seen := map[string]bool{}
	for _, f := range z.File {
		rel := strings.ReplaceAll(f.Name, "\\", "/")
		if strings.HasPrefix(rel, "/") || strings.ContainsAny(rel, ":\x00") {
			return errors.New("invalid application archive path")
		}
		parts := strings.Split(rel, "/")
		for _, p := range parts {
			if p == ".." {
				return errors.New("application archive leaves its destination")
			}
		}
		rel = filepath.Clean(filepath.FromSlash(rel))
		if rel == "." {
			continue
		}
		key := strings.ToLower(rel)
		if seen[key] {
			return errors.New("duplicate application archive entry")
		}
		seen[key] = true
		total += f.UncompressedSize64
		if total > 512<<20 {
			return errors.New("application archive is too large")
		}
		if f.Mode()&os.ModeSymlink != 0 {
			return errors.New("application archives cannot contain symlinks")
		}
		path := filepath.Join(dest, rel)
		if f.FileInfo().IsDir() {
			if err = os.MkdirAll(path, 0755); err != nil {
				return err
			}
			continue
		}
		if err = os.MkdirAll(filepath.Dir(path), 0755); err != nil {
			return err
		}
		in, e := f.Open()
		if e != nil {
			return e
		}
		out, e := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0755)
		if e != nil {
			in.Close()
			return e
		}
		n, e := io.Copy(out, io.LimitReader(in, 512<<20))
		ce := out.Close()
		in.Close()
		if e != nil {
			return e
		}
		if ce != nil {
			return ce
		}
		if uint64(n) != f.UncompressedSize64 {
			return errors.New("application archive size mismatch")
		}
	}
	return nil
}
