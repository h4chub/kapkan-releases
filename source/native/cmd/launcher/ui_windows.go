package main

import (
	"syscall"
	"unsafe"
)

var messageBox = syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW")

func message(s, title string, flags uintptr) {
	p, _ := syscall.UTF16PtrFromString(s)
	t, _ := syscall.UTF16PtrFromString(title)
	messageBox.Call(0, uintptr(unsafe.Pointer(p)), uintptr(unsafe.Pointer(t)), flags)
}
func showError(s string) { message(s, "KapKan", 0x10) }
func showSetup() {
	message("KapKan will prepare its Java runtime automatically. This is needed only on the first start and may take a few minutes.\n\nClick OK to continue. No system Java installation is needed.", "KapKan setup", 0x40)
}
