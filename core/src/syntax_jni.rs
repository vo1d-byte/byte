//! JNI surface for the Kotlin Tree-sitter syntax document.
use crate::syntax::KotlinSyntaxDocument;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};

static NEXT_SYNTAX_HANDLE: AtomicI64 = AtomicI64::new(1);
static SYNTAX_DOCUMENTS: OnceLock<Mutex<HashMap<i64, KotlinSyntaxDocument>>> = OnceLock::new();

fn syntax_documents() -> &'static Mutex<HashMap<i64, KotlinSyntaxDocument>> {
    SYNTAX_DOCUMENTS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn read<'a>(env: &mut JNIEnv<'a>, value: &JString<'a>) -> Option<String> {
    env.get_string(value).ok().map(|s| s.into())
}
fn output(env: &mut JNIEnv<'_>, value: String) -> jstring {
    env.new_string(value).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}
fn encode_spans(source: &str, spans: &[crate::syntax::SyntaxSpan]) -> String {
    spans.iter().map(|span| {
        let start = source[..span.start_byte].encode_utf16().count();
        let end = source[..span.end_byte].encode_utf16().count();
        format!("{start}:{end}:{}", span.kind)
    }).collect::<Vec<_>>().join("\n")
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeKotlinSyntaxBridge_nativeCreate(
    mut env: JNIEnv, _class: JClass, source: JString,
) -> jlong {
    let Some(source) = read(&mut env, &source) else { return 0 };
    let Ok(document) = KotlinSyntaxDocument::new(source) else { return 0 };
    let handle = NEXT_SYNTAX_HANDLE.fetch_add(1, Ordering::Relaxed);
    if let Ok(mut map) = syntax_documents().lock() {
        map.insert(handle, document);
        handle as jlong
    } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeKotlinSyntaxBridge_nativeDestroy(
    _env: JNIEnv, _class: JClass, handle: jlong,
) {
    if let Ok(mut map) = syntax_documents().lock() { map.remove(&(handle as i64)); }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeKotlinSyntaxBridge_nativeReplace(
    mut env: JNIEnv, _class: JClass, handle: jlong,
    start: jint, end: jint, replacement: JString,
) -> jboolean {
    let Some(replacement) = read(&mut env, &replacement) else { return 0 };
    let Ok(mut map) = syntax_documents().lock() else { return 0 };
    let Some(document) = map.get_mut(&(handle as i64)) else { return 0 };
    let start = start.max(0) as usize;
    let end = end.max(0) as usize;
    if document.replace(start..end, &replacement).is_ok() { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_dev_byteide_app_NativeKotlinSyntaxBridge_nativeGetSpans(
    mut env: JNIEnv, _class: JClass, handle: jlong,
) -> jstring {
    let result = syntax_documents().lock().ok().and_then(|map| {
        map.get(&(handle as i64)).map(|document| {
            let update = document.update();
            encode_spans(document.source(), &update.spans)
        })
    }).unwrap_or_default();
    output(&mut env, result)
}
