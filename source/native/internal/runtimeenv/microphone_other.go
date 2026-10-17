//go:build !darwin

package runtimeenv

import "context"

func verifyMicrophoneJava(ctx context.Context, java string) error { return nil }
