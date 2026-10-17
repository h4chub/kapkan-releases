// KapKan's native entry point. This starts the existing Java UI; it is not a new UI.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"kapkan.local/bootstrap/internal/runtimeenv"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const version = "1.0.6"

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		if !hasArg("--prepare-java") {
			showError(err.Error())
		}
		os.Exit(1)
	}
}
func hasArg(arg string) bool {
	for _, a := range os.Args[1:] {
		if a == arg {
			return true
		}
	}
	return false
}
func run() error {
	flags := flag.NewFlagSet("KapKan", flag.ContinueOnError)
	prepare := flags.Bool("prepare-java", false, "prepare a game runtime without opening the UI")
	major := flags.Int("major", 21, "required Java major")
	archFlag := flags.String("arch", runtime.GOARCH, "required runtime architecture")
	result := flags.String("result", "", "write the selected Java path as JSON")
	diag := flags.Bool("diagnostics", false, "print local runtime information without downloading")
	if err := flags.Parse(os.Args[1:]); err != nil {
		return err
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	root := filepath.Join(home, ".kapkan")
	logs := filepath.Join(root, "logs")
	if err = os.MkdirAll(logs, 0700); err != nil {
		return fmt.Errorf("cannot create KapKan's log folder: %w", err)
	}
	if *diag {
		info := map[string]any{"release": version, "os": runtime.GOOS, "architecture": runtime.GOARCH, "game_folder": gameRoot(home), "runtime_folder": filepath.Join(root, "runtimes"), "log_folder": logs}
		b, _ := json.MarshalIndent(info, "", "  ")
		fmt.Println(string(b))
		return nil
	}
	logPath := filepath.Join(logs, "bootstrap.log")
	if st, e := os.Stat(logPath); e == nil && st.Size() > 4<<20 {
		_ = os.Rename(logPath, logPath+".previous")
	}
	f, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer f.Close()
	logger := log.New(f, "", log.LstdFlags)
	logger.Printf("KapKan %s | %s/%s | prepare-runtime=%v", version, runtime.GOOS, runtime.GOARCH, *prepare)
	manager := runtimeenv.Manager{Root: filepath.Join(root, "runtimes")}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Minute)
	defer cancel()
	notified := false
	status := func(s string) {
		logger.Println(s)
		fmt.Fprintln(os.Stderr, s)
		if !*prepare && !notified {
			notified = true
			showSetup()
		}
	}
	j, err := manager.Ensure(ctx, *major, *archFlag, status)
	if err != nil {
		logger.Println("Startup failed:", err)
		return fmt.Errorf("%w\n\nDetails: %s", err, logPath)
	}
	if *prepare {
		b, _ := json.Marshal(map[string]string{"java": j})
		if *result != "" {
			return os.WriteFile(*result, b, 0600)
		}
		fmt.Println(string(b))
		return nil
	}
	// Verify even cached Java before starting the UI. No external/system Java is selected.
	a, _ := runtimeenv.Arch(*archFlag)
	if err = runtimeenv.VerifyJava(ctx, j, *major, a); err != nil {
		logger.Println(err)
		return fmt.Errorf("%w\n\nDetails: %s", err, logPath)
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		return err
	}
	res := filepath.Join(filepath.Dir(exe), "Resources")
	if runtime.GOOS == "darwin" {
		res = filepath.Join(filepath.Dir(exe), "..", "Resources")
	}
	res, err = filepath.Abs(res)
	if err != nil {
		return err
	}
	jar := filepath.Join(res, "KapKan-Launcher.jar")
	if st, e := os.Stat(jar); e != nil || st.Size() == 0 {
		return fmt.Errorf("KapKan's application files are incomplete. Reinstall the whole app.\nMissing: %s", jar)
	}
	game := gameRoot(home)
	for _, name := range []string{"mods", "versions", "libraries", "assets", "config"} {
		if err = os.MkdirAll(filepath.Join(game, name), 0755); err != nil {
			return err
		}
	}
	outPath := filepath.Join(logs, "launcher.log")
	if st, e := os.Stat(outPath); e == nil && st.Size() > 8<<20 {
		_ = os.Rename(outPath, outPath+".previous")
	}
	out, err := os.OpenFile(outPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	defer out.Close()
	args := []string{"-Xms64m", "-Xmx512m", "-Dfile.encoding=UTF-8", "-XX:ErrorFile=" + filepath.Join(logs, "launcher-jvm-crash-%p.log"), "-Dkapkan.release.version=" + version, "-Dkapkan.nativeBootstrap=" + exe, "-Dkapkan.clientCoreJar=" + filepath.Join(res, "KapKan-ClientCore-1.21.11-Beta1.jar"), "-Dkapkan.presenceJar=" + filepath.Join(res, "KapKan-1.0.6-mc26.1.2-fabric.jar"), "-Dkapkan.companionDirectory=" + res, "-Dkapkan.build=client-release-1.0.6"}
	if runtime.GOOS == "darwin" {
		// Choose the native dark appearance before Java initializes AWT, including owned dialogs.
		args = append(args, "-Xdock:name=KapKan", "-Xdock:icon="+filepath.Join(res, "KapKan-AppIcon.png"), "-Dapple.awt.application.name=KapKan", "-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua")
	}
	args = append(args, "-cp", jar, "gg.kapkan.release.ReleaseMain")
	cmd := exec.Command(j, args...)
	cmd.Env = append(os.Environ(), "KAPKAN_MINECRAFT_DIR="+game)
	cmd.Dir = game
	cmd.Stdout = out
	cmd.Stderr = out
	runtimeenv.HideWindow(cmd)
	logger.Println("Starting the launcher with native Java", *major, a)
	// Keep this app's direct process ancestry alive. macOS attributes microphone
	// consent from Minecraft voice mods to the responsible KapKan.app bundle.
	// Detaching Java through a shell or `open` breaks that application identity.
	if err = cmd.Run(); err != nil {
		logger.Println("Launcher exited:", err)
		return fmt.Errorf("KapKan closed during startup.\n\nOpen this log for the exact error:\n%s", outPath)
	}
	return nil
}
func gameRoot(home string) string {
	if p := strings.TrimSpace(os.Getenv("KAPKAN_MINECRAFT_DIR")); p != "" {
		if a, e := filepath.Abs(p); e == nil {
			return a
		}
	}
	return filepath.Join(home, ".kapkan", "game")
}
