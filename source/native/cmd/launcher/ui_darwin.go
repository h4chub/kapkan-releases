package main

import "os/exec"

func macDialog(s string) {
	_ = exec.Command("/usr/bin/osascript", "-e", "on run argv", "-e", "display dialog (item 1 of argv) with title \"KapKan\" buttons {\"OK\"} default button \"OK\"", "-e", "end run", s).Run()
}
func showError(s string) { macDialog(s) }
func showSetup() {
	macDialog("KapKan will prepare its native Java runtime automatically. This is needed only on the first start and may take a few minutes.\n\nClick OK to continue. No Java or developer tools need to be installed manually.")
}
