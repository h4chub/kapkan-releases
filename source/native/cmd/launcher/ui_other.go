//go:build !windows && !darwin

package main

import (
	"fmt"
	"os"
)

func showError(s string) { fmt.Fprintln(os.Stderr, s) }
func showSetup()         {}
