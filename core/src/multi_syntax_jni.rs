//! JNI bridge for the path-selected multi-language syntax document.
use crate::multi_syntax::MultiLanguageDocument;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};
static NEXT: AtomicI64 = AtomicI64::new(1);
static DOCS: OnceLock<Mutex<HashMap<i64, MultiLanguageDocument>>> = OnceLock::new();
fn docs() -> &'static Mutex<HashMap<i64, MultiLanguageDocument>> { DOCS.get_or_init(|| Mutex::new(HashMap::new())) }
fn read(env: &mut JNIEnv, value: &JString) -> Option<String> { env.get_string(value).ok().map(|s| s.into()) }
fn output(env: &mut JNIEnv, value: String) -> jstring { env.new_string(value).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()) }
fn spans(source: &str, doc: &MultiLanguageDocument) -> String {
    doc.spans().iter().map(|s| {
        let start = source[..s.start_byte].encode_utf16().count();
        let end = source[..s.end_byte].encode_utf16().count();
        format!("{start}:{end}:{}", s.kind)
    }).collect::<Vec<_>>().join("\n")
}
#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeSyntaxBridge_nativeCreate(mut env: JNIEnv, _class: JClass, path: JString, source: JString) -> jlong {
    let (Some(path), Some(source)) = (read(&mut env, &path), read(&mut env, &source)) else { return 0 };
    let Ok(doc) = MultiLanguageDocument::new(&path, source) else { return 0 };
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    if let Ok(mut map) = docs().lock() { map.insert(id, doc); id as jlong } else { 0 }
}
#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeSyntaxBridge_nativeDestroy(_env: JNIEnv, _class: JClass, handle: jlong) {
    if let Ok(mut map) = docs().lock() { map.remove(&(handle as i64)); }
}
#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeSyntaxBridge_nativeReplace(mut env: JNIEnv, _class: JClass, handle: jlong, start: jint, end: jint, replacement: JString) -> jboolean {
    let Some(replacement) = read(&mut env, &replacement) else { return 0 };
    let Ok(mut map) = docs().lock() else { return 0 };
    let Some(doc) = map.get_mut(&(handle as i64)) else { return 0 };
    if doc.replace(start.max(0) as usize..end.max(0) as usize, &replacement).is_ok() { 1 } else { 0 }
}
#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeSyntaxBridge_nativeGetSpans(mut env: JNIEnv, _class: JClass, handle: jlong) -> jstring {
    let value = docs().lock().ok().and_then(|map| map.get(&(handle as i64)).map(|doc| spans(doc.source(), doc))).unwrap_or_default();
    output(&mut env, value)
}
#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeSyntaxBridge_nativeGetDiagnostics(mut env: JNIEnv, _class: JClass, handle: jlong) -> jstring {
    let value = docs().lock().ok().and_then(|map| map.get(&(handle as i64)).map(|doc| doc.diagnostics().iter().map(|d| {
        let start = doc.source()[..d.start_byte].encode_utf16().count();
        let end = doc.source()[..d.end_byte].encode_utf16().count();
        format!("{start}:{end}:{}", d.message.replace(':', "_"))
    }).collect::<Vec<_>>().join("\n"))).unwrap_or_default();
    output(&mut env, value)
}
