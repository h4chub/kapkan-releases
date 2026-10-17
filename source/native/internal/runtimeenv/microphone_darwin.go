package runtimeenv

import (
	"context"
	"fmt"
	"os/exec"
)

// Verify the vendor's signature and microphone capability without changing it,
// requesting microphone access, capturing audio or touching privacy settings.
func verifyMicrophoneJava(ctx context.Context, java string) error {
	if output, err := exec.CommandContext(ctx, "/usr/bin/codesign", "--verify", "--strict", java).CombinedOutput(); err != nil {
		return fmt.Errorf("Java's macOS vendor signature could not be verified: %w (%s)", err, output)
	}
	if !signedAudioInput(java) {
		return fmt.Errorf("the downloaded macOS Java runtime does not declare microphone support")
	}
	return nil
}
