# JNI Integration

## Bridge API

The Rust cdylib exports JNI entry points for document creation/destruction, text retrieval, range replacement, selection updates, undo, and redo. A process-wide synchronized handle registry owns document sessions. Handles are monotonically allocated and must be released when tabs close.

## Coordinate systems

Android Compose uses UTF-16 code-unit offsets. The Rust rope document uses Unicode scalar-value offsets. EditorSession converts UTF-16 positions with String.codePointCount and applies minimal prefix/suffix edits so ordinary typing does not replace the entire document.

This is not grapheme-cluster navigation: combining marks and joined emoji may span multiple scalar values. Grapheme-aware cursor movement is a later editor feature.

## Build the native library manually

Install Rust, Android NDK, and cargo-ndk, then add the Android targets:

    rustup target add aarch64-linux-android
    rustup target add armv7-linux-androideabi
    rustup target add x86_64-linux-android
    cargo install cargo-ndk

Build and copy the shared libraries into Android jniLibs:

    cd core
    cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release

The resulting files should be:
- app/src/main/jniLibs/arm64-v8a/libbyte_core.so
- app/src/main/jniLibs/armeabi-v7a/libbyte_core.so
- app/src/main/jniLibs/x86_64/libbyte_core.so

The Kotlin bridge loads byte_core optionally. Until the native libraries are packaged, the editor keeps its Kotlin state and reports usesNativeEngine=false; it must not crash on startup.

## Safety and lifecycle

JNI calls validate handles and return safe empty/false results for invalid handles. The Kotlin owner implements AutoCloseable and must be closed when its document tab is permanently disposed. Never close it merely because a composable recomposed or an output panel changed visibility.

## Current limitations

- Android ABI packaging is documented but not yet automated by Gradle.
- Native document registry is process-local; document persistence belongs to the workspace layer.
- JNI methods currently serialize access through one mutex. This favors correctness initially; profiling may justify per-document locks later.
- No JNI instrumentation test or Android device build has been run in this change.
