//go:build windows

// Native per-user installer. Requires no admin access, Java installation, or development tools.
package main

import (
	"context"
	"crypto/sha256"
	_ "embed"
	"encoding/hex"
	"fmt"
	"kapkan.local/bootstrap/internal/installpkg"
	"kapkan.local/bootstrap/internal/runtimeenv"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

//go:embed payload.zip
var payload []byte

//go:embed payload.sha256
var expectedHash string

const version = "1.0.5"

var box = syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW")

func message(title, text string, flags uintptr) uintptr {
	t, _ := syscall.UTF16PtrFromString(title)
	m, _ := syscall.UTF16PtrFromString(text)
	r, _, _ := box.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), flags)
	return r
}
func main() {
	if err := run(); err != nil {
		message("KapKan setup", err.Error(), 0x10)
		os.Exit(1)
	}
}
func run() error {
	local := os.Getenv("LOCALAPPDATA")
	if local == "" || !filepath.IsAbs(local) {
		return fmt.Errorf("Windows did not provide a valid local application folder")
	}
	target := filepath.Join(local, "Programs", "KapKan")
	if len(os.Args) > 1 && os.Args[1] == "--uninstall" {
		if !installpkg.Owned(target) {
			return fmt.Errorf("KapKan's installer receipt was not found. No files have been removed.")
		}
		if message("Uninstall KapKan", "Remove the KapKan application?\n\nYour Minecraft worlds, mods, settings and downloaded runtimes in .kapkan will be kept.", 0x24) != 6 {
			return nil
		}
		self, e := os.Executable()
		if e != nil {
			return e
		}
		b, e := os.ReadFile(self)
		if e != nil {
			return e
		}
		dir, e := os.MkdirTemp("", "KapKan-uninstall-")
		if e != nil {
			return e
		}
		helper := filepath.Join(dir, "Uninstall.exe")
		if e = os.WriteFile(helper, b, 0700); e != nil {
			return e
		}
		c := exec.Command(helper, "--finish-uninstall")
		runtimeenv.HideWindow(c)
		return c.Start()
	}
	if len(os.Args) > 1 && os.Args[1] == "--finish-uninstall" {
		// Only a copied uninstaller, in its generated temporary folder, may do this phase.
		self, e := os.Executable()
		if e != nil {
			return e
		}
		rel, e := filepath.Rel(os.TempDir(), self)
		if e != nil || strings.HasPrefix(rel, "..") || !strings.HasPrefix(filepath.Base(filepath.Dir(self)), "KapKan-uninstall-") {
			return fmt.Errorf("invalid uninstall helper location")
		}
		time.Sleep(2 * time.Second)
		if !installpkg.Owned(target) {
			return fmt.Errorf("No managed KapKan application found; nothing removed")
		}
		if e = os.RemoveAll(target); e != nil {
			return fmt.Errorf("Close KapKan and retry uninstalling: %w", e)
		}
		removeIntegration()
		message("KapKan", "The application was removed. Your Minecraft data was kept.", 0x40)
		// The temporary executable is locked while running; Windows can clean its temporary folder later.
		return nil
	}
	if len(os.Args) > 1 {
		return fmt.Errorf("unknown setup argument")
	}
	if message("Install KapKan", "Install KapKan "+version+" for your Windows account?\n\nLocation: "+target+"\n\nMinecraft worlds, mods and controls will not be deleted. Internet is needed on first launch to prepare Java. This build is not code-signed. Publication is pending device testing.", 0x24) != 6 {
		return nil
	}
	sum := sha256.Sum256(payload)
	if !strings.EqualFold(hex.EncodeToString(sum[:]), strings.TrimSpace(expectedHash)) {
		return fmt.Errorf("installer payload verification failed")
	}
	tmp, e := os.MkdirTemp("", "KapKan-setup-")
	if e != nil {
		return e
	}
	defer os.RemoveAll(tmp)
	archive := filepath.Join(tmp, "payload.zip")
	if e = os.WriteFile(archive, payload, 0600); e != nil {
		return e
	}
	backup, e := installpkg.Publish(archive, strings.TrimSpace(expectedHash), target, version)
	if e != nil {
		return e
	}
	self, e := os.Executable()
	if e != nil {
		return e
	}
	binary, e := os.ReadFile(self)
	if e != nil {
		return e
	}
	if e = os.WriteFile(filepath.Join(target, "Uninstall.exe"), binary, 0700); e != nil {
		return fmt.Errorf("application copied, but uninstaller creation failed: %w", e)
	}
	warnings := installIntegration(target)
	text := "KapKan is installed.\n\nOpen it now?"
	if backup != "" {
		text += "\n\nThe previous application is backed up at:\n" + backup
	}
	if warnings != "" {
		text += "\n\nShortcut/registration notice: " + warnings + "\nYou can open KapKan.exe directly in the install folder."
	}
	if message("KapKan setup", text, 0x24) == 6 {
		c := exec.Command(filepath.Join(target, "KapKan.exe"))
		c.Dir = target
		runtimeenv.HideWindow(c)
		return c.Start()
	}
	return nil
}
func command(name string, args ...string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	c := exec.CommandContext(ctx, name, args...)
	runtimeenv.HideWindow(c)
	b, e := c.CombinedOutput()
	if e != nil {
		return fmt.Errorf("%s: %s", name, strings.TrimSpace(string(b)))
	}
	return nil
}
func installIntegration(target string) string {
	script := `$ErrorActionPreference='Stop'; $root=$env:KAPKAN_INSTALL_ROOT; $exe=Join-Path $root 'KapKan.exe'; $icon=Join-Path $root 'KapKan.ico'; $w=New-Object -ComObject WScript.Shell; $folder=Join-Path ([Environment]::GetFolderPath('Programs')) 'KapKan'; New-Item -ItemType Directory -Path $folder -Force|Out-Null; $s=$w.CreateShortcut((Join-Path $folder 'KapKan.lnk')); $s.TargetPath=$exe; $s.WorkingDirectory=$root; $s.IconLocation=$icon; $s.Save(); $key='HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\KapKan'; New-Item -Path $key -Force|Out-Null; New-ItemProperty -Path $key -Name DisplayName -Value 'KapKan Client' -Force|Out-Null; New-ItemProperty -Path $key -Name DisplayVersion -Value '1.0.5' -Force|Out-Null; New-ItemProperty -Path $key -Name DisplayIcon -Value $icon -Force|Out-Null; New-ItemProperty -Path $key -Name InstallLocation -Value $root -Force|Out-Null; New-ItemProperty -Path $key -Name UninstallString -Value ('"'+(Join-Path $root 'Uninstall.exe')+'" --uninstall') -Force|Out-Null; New-ItemProperty -Path $key -Name NoModify -PropertyType DWord -Value 1 -Force|Out-Null; New-ItemProperty -Path $key -Name NoRepair -PropertyType DWord -Value 1 -Force|Out-Null;`
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	c := exec.CommandContext(ctx, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
	c.Env = append(os.Environ(), "KAPKAN_INSTALL_ROOT="+target)
	runtimeenv.HideWindow(c)
	if b, e := c.CombinedOutput(); e != nil {
		return strings.TrimSpace(string(b))
	}
	return ""
}
func removeIntegration() {
	_ = command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", `$p=Join-Path ([Environment]::GetFolderPath('Programs')) 'KapKan\KapKan.lnk'; if(Test-Path -LiteralPath $p){Remove-Item -LiteralPath $p -Force}; Remove-Item -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\KapKan' -Recurse -Force -ErrorAction SilentlyContinue`)
}
