# KapKan 1.0.5 build and GUI environment

Source `environment.sh` in this folder. The build tools and GUI utilities are isolated here; project sources were not modified.

- `jdk25/bin/java`, `jdk25/bin/javac`: Corretto 25.0.4.10.1, runtime/javac 25.0.4.1. Official SHA256: `a45f1d385da8221fe1225dde5b6afa8e932a6acb93571b55eff4a39a2ebe7378`.
- `jdk21/bin/java`, `jdk21/bin/javac`: Corretto 21.0.12.12.1, runtime/javac 21.0.12.1. Official SHA256: `8785082c2fb999c024c8821e4a7c5391bda28f1667cceadafd78e2965b7669d2`.
- `go/bin/go`: Go 1.23.2 linux/amd64. Official archive SHA256: `542d3c1705f1c6a1c5a80d5dc62e2e45171af291e755d591c5e6531ef63b454e`. Use `GOFLAGS=-buildvcs=false` in this recovered archive checkout.
- `gradle-9.2.1/bin/gradle`: Gradle 9.2.1, JVM25 verified. Official archive SHA256: `72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`.
- `sysroot/usr/bin/Xvfb`, `xdotool`, `glxinfo`, `xdpyinfo`: official Ubuntu24.04 snapshot packages, isolated via `dpkg-deb -x`. Package provenance and checksums in `gui-package-uris.txt`; `fetch_gui_packages.py` reproduces downloads and extraction.
- `/usr/bin/import`: ImageMagick screenshot utility already installed.
- `/usr/bin/xkbcomp` symlink points at the isolated xkbcomp because Xserver compiles a hardcoded binary path.

## Verified display

`verify_gui.py` launches a temporary display, confirms geometry and GLX, takes a screenshot, then stops the display. It does not start Minecraft.

```sh
. /workspace/scratch/d3a6b894bee6/toolchains105/environment.sh
Xvfb :105 -screen 0 1600x1000x24 -nolisten unix -listen tcp -ac -fp "$KAPKAN_TOOLCHAINS/sysroot/usr/share/fonts/X11/misc"
# In the same orchestration environment:
export DISPLAY=127.0.0.1:105
```

UNIX-domain sockets are blocked by this sandbox. TCP loopback X11 works when the Xserver and child clients are in the same orchestration session. Mesa25.2.8 llvmpipe reports OpenGL4.5 core and compatibility, GLES3.2, no hardware acceleration. xdotool1600x1000 geometry and screenshot capture passed. Actual Minecraft testing belongs to the gameplay verification agent.

## Download notes

Initial downloads via services.gradle.org and go.dev returned truncated data despite curl success; checksum checks rejected those files. Redownloading directly from downloads.gradle.org and dl.google.com produced correct archives. Corretto official archives verified on first download.

Apt standard install cannot drop privileges under container UID mappings, and /var/cache/apt/archives/partial is inaccessible. Download-only URI resolution plus isolated package extraction avoids altering installed package databases. The package update listing succeeded using `APT::Sandbox::User=root`.
