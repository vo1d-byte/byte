//! JNI bridge for native PTY sessions.
use crate::pty::PtySession;
use crate::terminal_emulator::TerminalEmulator;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};

static NEXT_PTY: AtomicI64 = AtomicI64::new(1);
static PTYS: OnceLock<Mutex<HashMap<i64, PtySession>>> = OnceLock::new();
static SCREENS: OnceLock<Mutex<HashMap<i64, TerminalEmulator>>> = OnceLock::new();

fn sessions() -> &'static Mutex<HashMap<i64, PtySession>> {
    PTYS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn screens() -> &'static Mutex<HashMap<i64, TerminalEmulator>> {
    SCREENS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(|s| s.into())
}
fn make_string<'local>(env: &mut JNIEnv<'local>, value: String) -> jstring {
    env.new_string(value).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeCreate(
    mut env: JNIEnv, _class: JClass, program: JString, working_directory: JString,
    rows: jint, columns: jint,
) -> jlong {
    let (Some(program), Some(directory)) = (read_string(&mut env, &program), read_string(&mut env, &working_directory)) else { return 0; };
    let args: Vec<String> = Vec::new();
    let Ok(session) = PtySession::spawn(&program, &args, &PathBuf::from(directory), rows.max(1) as u16, columns.max(1) as u16) else { return 0; };
    let id = NEXT_PTY.fetch_add(1, Ordering::Relaxed);
    if let Ok(mut map) = sessions().lock() { map.insert(id, session); } else { return 0; }
    if let Ok(mut map) = screens().lock() { map.insert(id, TerminalEmulator::new(rows.max(1) as u16, columns.max(1) as u16, 10_000)); id as jlong } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeWrite(
    mut env: JNIEnv, _class: JClass, handle: jlong, input: JString,
) -> jboolean {
    let Some(input) = read_string(&mut env, &input) else { return 0; };
    let Ok(mut map) = sessions().lock() else { return 0; };
    map.get_mut(&(handle as i64)).map(|session| if session.write(input.as_bytes()).is_ok() { 1 } else { 0 }).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativePoll(
    mut env: JNIEnv, _class: JClass, handle: jlong,
) -> jstring {
    let output = sessions().lock().ok().and_then(|mut map| map.get_mut(&(handle as i64)).map(|s| s.poll().output)).unwrap_or_default();
    make_string(&mut env, String::from_utf8_lossy(&output).into_owned())
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativePollScreen(
    mut env: JNIEnv, _class: JClass, handle: jlong,
) -> jstring {
    let output = sessions().lock().ok()
        .and_then(|mut map| map.get_mut(&(handle as i64)).map(|s| s.poll().output))
        .unwrap_or_default();
    let Ok(mut map) = screens().lock() else { return make_string(&mut env, String::new()); };
    if let Some(screen) = map.get_mut(&(handle as i64)) {
        screen.process(&output);
        make_string(&mut env, screen.snapshot().contents)
    } else {
        make_string(&mut env, String::new())
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativePollGrid(
    mut env: JNIEnv, _class: JClass, handle: jlong,
) -> jstring {
    let output = sessions().lock().ok()
        .and_then(|mut map| map.get_mut(&(handle as i64)).map(|session| session.poll().output))
        .unwrap_or_default();
    // Avoid serializing the entire screen on idle polling ticks.
    if output.is_empty() { return make_string(&mut env, String::new()); }
    let Ok(mut map) = screens().lock() else { return make_string(&mut env, "{}".to_string()); };
    if let Some(screen) = map.get_mut(&(handle as i64)) {
        screen.process(&output);
        make_string(&mut env, screen.grid_json())
    } else {
        make_string(&mut env, "{}".to_string())
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeCursorInfo(
    mut env: JNIEnv, _class: JClass, handle: jlong,
) -> jstring {
    let value = screens().lock().ok()
        .and_then(|map| map.get(&(handle as i64)).map(|screen| {
            let state = screen.snapshot();
            format!("{},{},{},{}", state.cursor_row, state.cursor_column, state.rows, state.columns)
        })).unwrap_or_default();
    make_string(&mut env, value)
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeIsRunning(
    _env: JNIEnv, _class: JClass, handle: jlong,
) -> jboolean {
    let Ok(mut map) = sessions().lock() else { return 0; };
    map.get_mut(&(handle as i64)).map(|s| if s.is_running() { 1 } else { 0 }).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeResize(
    _env: JNIEnv, _class: JClass, handle: jlong, rows: jint, columns: jint,
) -> jboolean {
    let result = {
        let Ok(map) = sessions().lock() else { return 0; };
        map.get(&(handle as i64)).map(|s| if s.resize(rows.max(1) as u16, columns.max(1) as u16).is_ok() { 1 } else { 0 }).unwrap_or(0)
    };
    if let Ok(mut map) = screens().lock() {
        if let Some(screen) = map.get_mut(&(handle as i64)) {
            screen.resize(rows.max(1) as u16, columns.max(1) as u16);
        }
    }
    result
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeSignal(
    _env: JNIEnv, _class: JClass, handle: jlong, signal: jint,
) -> jboolean {
    let Ok(map) = sessions().lock() else { return 0; };
    map.get(&(handle as i64)).map(|s| if s.signal_foreground_group(signal).is_ok() { 1 } else { 0 }).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeTerminalBridge_nativeClose(
    _env: JNIEnv, _class: JClass, handle: jlong,
) {
    if let Ok(mut map) = sessions().lock() {
        if let Some(mut session) = map.remove(&(handle as i64)) { session.terminate(); }
        if let Ok(mut screen_map) = screens().lock() { screen_map.remove(&(handle as i64)); }
    }
}
