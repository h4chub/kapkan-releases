//go:build !windows

package runtimeenv

import "os/exec"

func HideWindow(c *exec.Cmd) {}
