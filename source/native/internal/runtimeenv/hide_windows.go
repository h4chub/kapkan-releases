package runtimeenv

import (
	"os/exec"
	"syscall"
)

func HideWindow(c *exec.Cmd) {
	c.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
}
