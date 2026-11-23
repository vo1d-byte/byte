//! JNI adapter for the Android editor session.
//!
//! JNI positions are Unicode scalar offsets, matching TextDocument. Kotlin must
//! convert Android UTF-16 indices before calling nativeReplace/nativeSetSelection.

use crate::editor::{Selection, TextDocument};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};
use std::sync::atomic::{AtomicI64, Ordering};

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);
static DOCUMENTS: OnceLock<Mutex<HashMap<i64, TextDocument>>> = OnceLock::new();

fn documents() -> &'static Mutex<HashMap<i64, TextDocument>> {
    DOCUMENTS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(|s| s.into())
}

fn make_string<'local>(env: &mut JNIEnv<'local>, value: String) -> jstring {
    env.new_string(value).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeCreateDocument(
    mut env: JNIEnv,
    _class: JClass,
    initial_text: JString,
) -> jlong {
    let Some(text) = read_string(&mut env, &initial_text) else { return 0 };
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
    if let Ok(mut map) = documents().lock() {
        map.insert(handle, TextDocument::new(&text));
        handle as jlong
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeDestroyDocument(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Ok(mut map) = documents().lock() {
        map.remove(&(handle as i64));
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeGetText(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let text = documents().lock().ok()
        .and_then(|map| map.get(&(handle as i64)).map(TextDocument::text))
        .unwrap_or_default();
    make_string(&mut env, text)
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeReplace(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    start: jint,
    end: jint,
    replacement: JString,
) -> jboolean {
    let Some(value) = read_string(&mut env, &replacement) else { return 0 };
    let Ok(mut map) = documents().lock() else { return 0 };
    let Some(doc) = map.get_mut(&(handle as i64)) else { return 0 };
    let start = (start.max(0) as usize).min(doc.len_chars());
    let end = (end.max(0) as usize).min(doc.len_chars()).max(start);
    doc.replace(start..end, &value);
    1
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeSetSelection(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    anchor: jint,
    head: jint,
) -> jboolean {
    let Ok(mut map) = documents().lock() else { return 0 };
    let Some(doc) = map.get_mut(&(handle as i64)) else { return 0 };
    doc.set_selection(Selection {
        anchor: anchor.max(0) as usize,
        head: head.max(0) as usize,
    });
    1
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeUndo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let text = documents().lock().ok().and_then(|mut map| {
        map.get_mut(&(handle as i64)).map(|doc| {
            doc.undo();
            doc.text()
        })
    }).unwrap_or_default();
    make_string(&mut env, text)
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeEditorBridge_nativeRedo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let text = documents().lock().ok().and_then(|mut map| {
        map.get_mut(&(handle as i64)).map(|doc| {
            doc.redo();
            doc.text()
        })
    }).unwrap_or_default();
    make_string(&mut env, text)
}
