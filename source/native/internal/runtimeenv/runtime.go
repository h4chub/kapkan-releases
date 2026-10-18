// Package runtimeenv installs a matching Java runtime into a KapKan-owned cache.
// It never changes system Java, the game directory, or existing Minecraft data.
package runtimeenv

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
)

const MaxArchive = int64(512 << 20)
const MaxExtracted = int64(1536 << 20)
const MetadataURL = "https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&image_type=jre&os=%s&vendor=eclipse"

type Status func(string)
type Manager struct {
	Root   string
	Client *http.Client
	// Metadata is supplied only in tests. Production uses the vendor's HTTPS API.
	Metadata func(int, string, string) string
}
type Release struct {
	Binary struct {
		Architecture string `json:"architecture"`
		OS           string `json:"os"`
		ImageType    string `json:"image_type"`
		Package      struct {
			Checksum string `json:"checksum"`
			Link     string `json:"link"`
			Name     string `json:"name"`
			Size     int64  `json:"size"`
			// Azul's API rounds some archive sizes (the SHA-256 is exact).
			// Adoptium retains exact-size verification with the default zero.
			SizeTolerance int64 `json:"-"`
		} `json:"package"`
	} `json:"binary"`
	ReleaseName string `json:"release_name"`
	Version     struct {
		Major  int    `json:"major"`
		Semver string `json:"semver"`
	} `json:"version"`
}
type Receipt struct {
	Major         int    `json:"major"`
	Architecture  string `json:"architecture"`
	OS            string `json:"os"`
	Java          string `json:"java"`
	Release       string `json:"release"`
	ArchiveSHA256 string `json:"archive_sha256"`
}

func Arch(a string) (string, error) {
	switch strings.ToLower(strings.TrimSpace(a)) {
	case "arm64", "aarch64":
		return "aarch64", nil
	case "amd64", "x86_64", "x64":
		return "x64", nil
	default:
		return "", fmt.Errorf("unsupported processor architecture %q; use a 64-bit Mac or Windows system", a)
	}
}
func Platform() string {
	if runtime.GOOS == "darwin" {
		return "mac"
	}
	return runtime.GOOS
}
func (m Manager) httpClient() *http.Client {
	if m.Client != nil {
		return m.Client
	}
	return &http.Client{Timeout: 15 * time.Minute, CheckRedirect: func(req *http.Request, via []*http.Request) error {
		if len(via) >= 10 {
			return errors.New("too many download redirects")
		}
		if req.URL.Scheme != "https" {
			return errors.New("refusing an insecure download redirect")
		}
		return nil
	}}
}
func (m Manager) Ensure(ctx context.Context, major int, arch string, status Status) (string, error) {
	if status == nil {
		status = func(string) {}
	}
	if major < 8 || major > 99 {
		return "", fmt.Errorf("invalid Java requirement %d", major)
	}
	a, err := Arch(arch)
	if err != nil {
		return "", err
	}
	platform := Platform()
	root, err := filepath.Abs(m.Root)
	if err != nil {
		return "", err
	}
	if err = os.MkdirAll(root, 0700); err != nil {
		return "", err
	}
	target := filepath.Join(root, fmt.Sprintf("%s-%d-%s-%s", runtimeVendor(platform), major, platform, a))
	if j := m.usableCached(target, major, a, platform); j != "" {
		return j, nil
	}
	lock := target + ".install-lock"
	deadline := time.Now().Add(15 * time.Minute)
	for {
		err = os.Mkdir(lock, 0700)
		if err == nil {
			break
		}
		if !os.IsExist(err) {
			return "", err
		}
		if st, e := os.Stat(lock); e == nil && time.Since(st.ModTime()) > 30*time.Minute {
			os.Remove(lock)
			continue
		}
		if j := m.usableCached(target, major, a, platform); j != "" {
			return j, nil
		}
		if time.Now().After(deadline) {
			return "", errors.New("another KapKan Java setup is still running; close it and retry")
		}
		select {
		case <-ctx.Done():
			return "", ctx.Err()
		case <-time.After(time.Second):
		}
	}
	defer os.Remove(lock)
	if j := m.usableCached(target, major, a, platform); j != "" {
		return j, nil
	}
	status(fmt.Sprintf("Preparing Java %d (%s). First setup requires internet.", major, a))
	selected, err := m.release(ctx, major, a, platform)
	if err != nil {
		return "", err
	}
	pkg := selected.Binary.Package
	if _, err = hex.DecodeString(pkg.Checksum); err != nil || len(pkg.Checksum) != 64 {
		return "", errors.New("Java release metadata has no valid SHA-256 checksum")
	}
	u, err := url.Parse(pkg.Link)
	if err != nil || u.Scheme != "https" {
		return "", errors.New("Java download must use HTTPS")
	}
	if pkg.Size <= 0 || pkg.Size > MaxArchive {
		return "", errors.New("Java runtime archive has an invalid size")
	}
	stage, err := os.MkdirTemp(root, ".runtime-staging-")
	if err != nil {
		return "", err
	}
	defer os.RemoveAll(stage)
	archive := filepath.Join(stage, "runtime.archive")
	status(fmt.Sprintf("Downloading Java %d (%d MB)...", major, pkg.Size/(1<<20)))
	if err = m.download(ctx, pkg.Link, archive, pkg.Size, pkg.Checksum, pkg.SizeTolerance); err != nil {
		return "", err
	}
	content := filepath.Join(stage, "content")
	if err = os.Mkdir(content, 0700); err != nil {
		return "", err
	}
	status("Java download verified. Extracting...")
	if strings.HasSuffix(strings.ToLower(pkg.Name), ".zip") {
		err = ExtractZip(archive, content)
	} else if strings.HasSuffix(strings.ToLower(pkg.Name), ".tar.gz") {
		err = ExtractTarGz(archive, content)
	} else {
		err = fmt.Errorf("unsupported Java archive %q", pkg.Name)
	}
	if err != nil {
		return "", fmt.Errorf("cannot extract Java: %w", err)
	}
	j, err := FindJava(content)
	if err != nil {
		return "", err
	}
	if err = VerifyJava(ctx, j, major, a); err != nil {
		return "", err
	}
	if platform == "mac" && m.Metadata == nil {
		if err = verifyMicrophoneJava(ctx, j); err != nil {
			return "", err
		}
	}
	relative, _ := filepath.Rel(content, j)
	receipt := Receipt{major, a, platform, relative, selected.ReleaseName, pkg.Checksum}
	b, _ := json.MarshalIndent(receipt, "", "  ")
	if err = os.WriteFile(filepath.Join(content, "kapkan-runtime.json"), b, 0600); err != nil {
		return "", err
	}
	// Publish atomically. A broken runtime is kept under a new name, never deleted in place.
	backup := ""
	if _, e := os.Stat(target); e == nil {
		backup = target + ".previous-" + strconv.FormatInt(time.Now().UnixNano(), 10)
		if err = os.Rename(target, backup); err != nil {
			return "", fmt.Errorf("cannot replace the old runtime (close other KapKan instances): %w", err)
		}
	}
	if err = os.Rename(content, target); err != nil {
		if backup != "" {
			os.Rename(backup, target)
		}
		return "", err
	}
	status(fmt.Sprintf("Java %d ready.", major))
	return filepath.Join(target, relative), nil
}
func cached(target string, major int, arch, platform string) string {
	b, e := os.ReadFile(filepath.Join(target, "kapkan-runtime.json"))
	if e != nil {
		return ""
	}
	var r Receipt
	if json.Unmarshal(b, &r) != nil || r.Major != major || r.Architecture != arch || r.OS != platform {
		return ""
	}
	j, e := safePath(target, r.Java)
	if e != nil {
		return ""
	}
	st, e := os.Stat(j)
	if e != nil || !st.Mode().IsRegular() || st.Size() == 0 {
		return ""
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if VerifyJava(ctx, j, major, arch) != nil {
		return ""
	}
	return j
}
func (m Manager) download(ctx context.Context, link, dest string, size int64, checksum string, sizeTolerance int64) error {
	req, e := http.NewRequestWithContext(ctx, "GET", link, nil)
	if e != nil {
		return e
	}
	req.Header.Set("User-Agent", "KapKan/1.0.7")
	resp, e := m.httpClient().Do(req)
	if e != nil {
		return fmt.Errorf("Java download failed: %w", e)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("Java download failed: HTTP %d", resp.StatusCode)
	}
	f, e := os.OpenFile(dest, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if e != nil {
		return e
	}
	hash := sha256.New()
	n, e := io.Copy(io.MultiWriter(f, hash), io.LimitReader(resp.Body, MaxArchive+1))
	ce := f.Close()
	if e != nil {
		return e
	}
	if ce != nil {
		return ce
	}
	if n == 0 || n > MaxArchive {
		return errors.New("Java runtime download exceeds its size limit or is empty")
	}
	difference := n - size
	if difference < 0 {
		difference = -difference
	}
	if difference > sizeTolerance {
		return fmt.Errorf("Java download is incomplete: expected %d bytes, received %d", size, n)
	}
	if !strings.EqualFold(hex.EncodeToString(hash.Sum(nil)), checksum) {
		return errors.New("Java download checksum did not match; it was not installed")
	}
	return nil
}
func safePath(root, name string) (string, error) {
	name = strings.ReplaceAll(name, "\\", "/")
	if name == "" || strings.ContainsRune(name, 0) || strings.Contains(name, ":") || strings.HasPrefix(name, "/") {
		return "", fmt.Errorf("unsafe archive path %q", name)
	}
	p := filepath.Join(root, filepath.FromSlash(name))
	rel, e := filepath.Rel(root, p)
	if e != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", fmt.Errorf("archive entry escapes its destination: %q", name)
	}
	return p, nil
}
func regular(root, name string, size int64, mode os.FileMode, src io.Reader, total *int64) error {
	if size < 0 || size > MaxExtracted || *total+size > MaxExtracted {
		return errors.New("archive exceeds extraction limit")
	}
	*total += size
	p, e := safePath(root, name)
	if e != nil {
		return e
	}
	if e = os.MkdirAll(filepath.Dir(p), 0755); e != nil {
		return e
	}
	f, e := os.OpenFile(p, os.O_CREATE|os.O_EXCL|os.O_WRONLY, mode.Perm()&0777)
	if e != nil {
		return e
	}
	n, e := io.CopyN(f, src, size)
	ce := f.Close()
	if e != nil {
		return e
	}
	if n != size {
		return io.ErrUnexpectedEOF
	}
	return ce
}
func ExtractZip(archive, root string) error {
	z, e := zip.OpenReader(archive)
	if e != nil {
		return e
	}
	defer z.Close()
	if len(z.File) > 50000 {
		return errors.New("too many archive entries")
	}
	var total int64
	for _, f := range z.File {
		p, e := safePath(root, f.Name)
		if e != nil {
			return e
		}
		if f.FileInfo().IsDir() {
			if e = os.MkdirAll(p, 0755); e != nil {
				return e
			}
			continue
		}
		if !f.Mode().IsRegular() {
			return fmt.Errorf("unsupported ZIP entry: %s", f.Name)
		}
		rc, e := f.Open()
		if e != nil {
			return e
		}
		e = regular(root, f.Name, int64(f.UncompressedSize64), f.Mode(), rc, &total)
		rc.Close()
		if e != nil {
			return e
		}
	}
	return nil
}
func ExtractTarGz(archive, root string) error {
	f, e := os.Open(archive)
	if e != nil {
		return e
	}
	defer f.Close()
	gz, e := gzip.NewReader(f)
	if e != nil {
		return e
	}
	defer gz.Close()
	tr := tar.NewReader(gz)
	type link struct {
		path, target string
		hard         bool
	}
	var links []link
	var total int64
	count := 0
	for {
		h, e := tr.Next()
		if e == io.EOF {
			break
		}
		if e != nil {
			return e
		}
		count++
		if count > 50000 {
			return errors.New("too many archive entries")
		}
		p, e := safePath(root, h.Name)
		if e != nil {
			return e
		}
		switch h.Typeflag {
		case tar.TypeDir:
			if e = os.MkdirAll(p, 0755); e != nil {
				return e
			}
		case tar.TypeReg, tar.TypeRegA:
			if e = regular(root, h.Name, h.Size, os.FileMode(h.Mode), tr, &total); e != nil {
				return e
			}
		case tar.TypeSymlink:
			if filepath.IsAbs(h.Linkname) || strings.Contains(h.Linkname, ":") {
				return errors.New("unsafe runtime symlink")
			}
			target := filepath.Join(filepath.Dir(p), filepath.FromSlash(h.Linkname))
			rel, e := filepath.Rel(root, target)
			if e != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
				return errors.New("runtime symlink escapes extraction root")
			}
			links = append(links, link{p, h.Linkname, false})
		case tar.TypeLink:
			target, e := safePath(root, h.Linkname)
			if e != nil {
				return e
			}
			links = append(links, link{p, target, true})
		default:
			return fmt.Errorf("unsupported runtime archive entry type %d", h.Typeflag)
		}
	}
	// Create links last, so extracting a subsequent file cannot follow a symlink.
	for _, l := range links {
		if e = os.MkdirAll(filepath.Dir(l.path), 0755); e != nil {
			return e
		}
		if l.hard {
			e = os.Link(l.target, l.path)
		} else {
			e = os.Symlink(l.target, l.path)
		}
		if e != nil {
			return e
		}
	}
	return nil
}
func FindJava(root string) (string, error) {
	name := "java"
	if runtime.GOOS == "windows" {
		name = "java.exe"
	}
	var found string
	err := filepath.WalkDir(root, func(p string, d os.DirEntry, e error) error {
		if e != nil {
			return e
		}
		if d.IsDir() {
			return nil
		}
		if d.Name() == name && filepath.Base(filepath.Dir(p)) == "bin" && d.Type()&os.ModeSymlink == 0 {
			found = p
			return filepath.SkipAll
		}
		return nil
	})
	if err != nil {
		return "", err
	}
	if found == "" {
		return "", errors.New("Java runtime archive contains no executable")
	}
	return found, nil
}
func VerifyJava(ctx context.Context, path string, major int, arch string) error {
	ctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, path, "-XshowSettings:properties", "-version")
	HideWindow(cmd)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("Java could not start on this computer: %w", err)
	}
	props := string(out)
	var vmMajor int
	var vmArch string
	for _, line := range strings.Split(props, "\n") {
		k, v, ok := strings.Cut(strings.TrimSpace(line), " = ")
		if !ok {
			continue
		}
		if k == "java.specification.version" {
			v = strings.TrimPrefix(v, "1.")
			vmMajor, _ = strconv.Atoi(v)
		}
		if k == "os.arch" {
			vmArch, _ = Arch(v)
		}
	}
	if vmMajor != major || vmArch != arch {
		return fmt.Errorf("Java runtime mismatch: expected %d/%s, got %d/%s", major, arch, vmMajor, vmArch)
	}
	return nil
}
