//! JNI bridge: `dev.byte.ide.core.Native`.
//!
//! Conventions: handles are `jlong`; string results starting with `ERR:` are
//! errors; panics are caught so they never unwind across the FFI boundary.
use byte_core::Session;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::collections::HashMap;
use std::fs::File;
use std::io::Read;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::ptr;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, OnceLock};

static NEXT: AtomicI64 = AtomicI64::new(1);

struct Term {
    pty: Mutex<byte_term::Pty>,
    reader: Mutex<File>,
}

fn sessions() -> &'static Mutex<HashMap<i64, Session>> {
    static S: OnceLock<Mutex<HashMap<i64, Session>>> = OnceLock::new();
    S.get_or_init(|| Mutex::new(HashMap::new()))
}

fn terms() -> &'static Mutex<HashMap<i64, Arc<Term>>> {
    static T: OnceLock<Mutex<HashMap<i64, Arc<Term>>>> = OnceLock::new();
    T.get_or_init(|| Mutex::new(HashMap::new()))
}

fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|e| e.into_inner())
}

fn with_session<R>(h: i64, f: impl FnOnce(&mut Session) -> R) -> Option<R> {
    lock(sessions()).get_mut(&h).map(f)
}

fn get_string(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(String::from).unwrap_or_default()
}

fn ret_str(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s).map(|j| j.into_raw()).unwrap_or(ptr::null_mut())
}

fn guard<R>(default: R, f: impl FnOnce() -> R) -> R {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or(default)
}

const NO_SESSION: &str = "ERR:no such session";

// ------------------------------------------------------------------ session

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_sessionOpen(
    mut env: JNIEnv,
    _c: JClass,
    root: JString,
) -> jlong {
    let root = get_string(&mut env, &root);
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    lock(sessions()).insert(id, Session::new(root));
    id
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_sessionClose(_e: JNIEnv, _c: JClass, h: jlong) {
    lock(sessions()).remove(&h);
}

/// Lines of `d|name` or `f|name`.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_listDir(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    rel: JString,
) -> jstring {
    let rel = get_string(&mut env, &rel);
    let out = guard(NO_SESSION.to_string(), || {
        with_session(h, |s| match s.project().list(&rel) {
            Ok(list) => list
                .iter()
                .map(|e| format!("{}|{}", if e.is_dir { 'd' } else { 'f' }, e.name))
                .collect::<Vec<_>>()
                .join("\n"),
            Err(e) => format!("ERR:{e}"),
        })
        .unwrap_or_else(|| NO_SESSION.to_string())
    });
    ret_str(&mut env, &out)
}

// --------------------------------------------------------------------- tabs

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_tabOpen(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    path: JString,
) -> jint {
    let path = get_string(&mut env, &path);
    guard(-1, || {
        with_session(h, |s| s.open(&path).map(|i| i as jint).unwrap_or(-1)).unwrap_or(-1)
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_tabClose(_e: JNIEnv, _c: JClass, h: jlong, idx: jint) {
    guard((), || {
        with_session(h, |s| s.close(idx.max(0) as usize));
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_tabActivate(_e: JNIEnv, _c: JClass, h: jlong, idx: jint) {
    guard((), || {
        with_session(h, |s| s.set_active(idx.max(0) as usize));
    })
}

/// `activeIndex\n` followed by one `dirty|path` line per tab.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_tabList(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
) -> jstring {
    let out = guard(NO_SESSION.to_string(), || {
        with_session(h, |s| {
            let mut lines = vec![s.active_index().to_string()];
            for t in s.tabs() {
                lines.push(format!("{}|{}", if t.buffer.is_dirty() { 1 } else { 0 }, t.path));
            }
            lines.join("\n")
        })
        .unwrap_or_else(|| NO_SESSION.to_string())
    });
    ret_str(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_saveDirty(_e: JNIEnv, _c: JClass, h: jlong) -> jint {
    guard(-1, || {
        with_session(h, |s| s.save_dirty().map(|n| n as jint).unwrap_or(-1)).unwrap_or(-1)
    })
}

// ------------------------------------------------------------------- buffer

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_bufText(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
) -> jstring {
    let out = guard(String::new(), || {
        with_session(h, |s| s.active_mut().map(|t| t.buffer.text()).unwrap_or_default())
            .unwrap_or_default()
    });
    ret_str(&mut env, &out)
}

/// `anchor,head` in UTF-16 units.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_bufSelection(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
) -> jstring {
    let out = guard("0,0".to_string(), || {
        with_session(h, |s| {
            s.active_mut()
                .map(|t| {
                    let (a, b) = t.buffer.selection_utf16();
                    format!("{a},{b}")
                })
                .unwrap_or_else(|| "0,0".into())
        })
        .unwrap_or_else(|| "0,0".into())
    });
    ret_str(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_bufSync(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    text: JString,
    anchor: jint,
    head: jint,
) {
    let text = get_string(&mut env, &text);
    guard((), || {
        with_session(h, |s| {
            if let Some(t) = s.active_mut() {
                t.buffer.sync_from_text(&text, anchor.max(0) as usize, head.max(0) as usize);
            }
        });
    })
}

/// Editor commands. `arg` formats: `insert` text; `insertPair` two chars;
/// `toggleComment` prefix; `moveH`/`moveV` `delta,extend(0|1)`.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_bufOp(
    mut env: JNIEnv,
    _c: JClass,
    h: jlong,
    op: JString,
    arg: JString,
) -> jstring {
    let op = get_string(&mut env, &op);
    let arg = get_string(&mut env, &arg);
    let out = guard("ERR:panic".to_string(), || {
        with_session(h, |s| {
            let Some(t) = s.active_mut() else { return "ERR:no active tab".to_string() };
            let b = &mut t.buffer;
            let motion = || {
                let mut it = arg.split(',');
                let d: isize = it.next().and_then(|x| x.parse().ok()).unwrap_or(0);
                let ext = it.next() == Some("1");
                (d, ext)
            };
            match op.as_str() {
                "undo" => {
                    b.undo();
                }
                "redo" => {
                    b.redo();
                }
                "insert" => b.insert(&arg),
                "backspace" => b.delete_backward(),
                "insertPair" => {
                    let mut c = arg.chars();
                    let (o, cl) = (c.next(), c.next());
                    match (o, cl) {
                        (Some(o), Some(cl)) => b.insert_pair(&o.to_string(), &cl.to_string()),
                        _ => return "ERR:insertPair needs two chars".to_string(),
                    }
                }
                "duplicateLine" => b.duplicate_line(),
                "toggleComment" => b.toggle_line_comment(if arg.is_empty() { "//" } else { &arg }),
                "selectWord" => b.select_word(),
                "selectLine" => b.select_line(),
                "selectAll" => b.select_all(),
                "moveH" => {
                    let (d, e) = motion();
                    b.move_horizontal(d, e)
                }
                "moveV" => {
                    let (d, e) = motion();
                    b.move_vertical(d, e)
                }
                other => return format!("ERR:unknown op {other}"),
            }
            "ok".to_string()
        })
        .unwrap_or_else(|| NO_SESSION.to_string())
    });
    ret_str(&mut env, &out)
}

// ----------------------------------------------------------------- terminal

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_termSpawn(
    mut env: JNIEnv,
    _c: JClass,
    program: JString,
    cwd: JString,
    rows: jint,
    cols: jint,
) -> jlong {
    let program = get_string(&mut env, &program);
    let cwd = get_string(&mut env, &cwd);
    guard(-1, || {
        let pty = match byte_term::Pty::spawn(
            &program,
            &[],
            Path::new(&cwd),
            &[],
            rows.clamp(1, 500) as u16,
            cols.clamp(1, 500) as u16,
        ) {
            Ok(p) => p,
            Err(_) => return -1,
        };
        let Ok(reader) = pty.reader() else { return -1 };
        let id = NEXT.fetch_add(1, Ordering::Relaxed);
        lock(terms()).insert(
            id,
            Arc::new(Term { pty: Mutex::new(pty), reader: Mutex::new(reader) }),
        );
        id
    })
}

fn term(h: i64) -> Option<Arc<Term>> {
    lock(terms()).get(&h).cloned()
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_termWrite(
    env: JNIEnv,
    _c: JClass,
    h: jlong,
    data: JByteArray,
) {
    let Ok(bytes) = env.convert_byte_array(&data) else { return };
    guard((), || {
        if let Some(t) = term(h) {
            let _ = lock(&t.pty).write(&bytes);
        }
    })
}

/// Blocking read of up to 4 KiB. Returns null at EOF or on error.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_termRead(
    env: JNIEnv,
    _c: JClass,
    h: jlong,
) -> jbyteArray {
    let Some(t) = term(h) else { return ptr::null_mut() };
    let mut buf = [0u8; 4096];
    let n = match lock(&t.reader).read(&mut buf) {
        Ok(n) if n > 0 => n,
        _ => return ptr::null_mut(),
    };
    env.byte_array_from_slice(&buf[..n]).map(|a| a.into_raw()).unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_termResize(
    _e: JNIEnv,
    _c: JClass,
    h: jlong,
    rows: jint,
    cols: jint,
) {
    if let Some(t) = term(h) {
        let _ = lock(&t.pty).resize(rows.clamp(1, 500) as u16, cols.clamp(1, 500) as u16);
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_termClose(_e: JNIEnv, _c: JClass, h: jlong) {
    // Dropping the last Arc kills and reaps the child.
    lock(terms()).remove(&h);
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_termAlive(_e: JNIEnv, _c: JClass, h: jlong) -> jboolean {
    match term(h) {
        Some(t) => match lock(&t.pty).try_wait() {
            Ok(None) => JNI_TRUE,
            _ => JNI_FALSE,
        },
        None => JNI_FALSE,
    }
}

// ---------------------------------------------------------------- toolchain

/// Returns the install directory, or `ERR:...`.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_toolchainInstall(
    mut env: JNIEnv,
    _c: JClass,
    manifest_toml: JString,
    archive: JString,
    abi: JString,
    root: JString,
) -> jstring {
    let manifest_toml = get_string(&mut env, &manifest_toml);
    let archive = get_string(&mut env, &archive);
    let abi = get_string(&mut env, &abi);
    let root = get_string(&mut env, &root);
    let out = guard("ERR:panic".to_string(), || {
        let m = match byte_toolchain::Manifest::parse(&manifest_toml) {
            Ok(m) => m,
            Err(e) => return format!("ERR:{e}"),
        };
        match byte_toolchain::install_from_archive(&m, Path::new(&archive), &abi, Path::new(&root)) {
            Ok(dir) => dir.display().to_string(),
            Err(e) => format!("ERR:{e}"),
        }
    });
    ret_str(&mut env, &out)
}

// ---------------------------------------------------------------------- git

/// Lines of `state|staged(0|1)|path`, first line is the branch name.
#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_gitStatus(
    mut env: JNIEnv,
    _c: JClass,
    root: JString,
) -> jstring {
    let root = get_string(&mut env, &root);
    let out = guard("ERR:panic".to_string(), || {
        let repo = match byte_git::open_or_init(Path::new(&root)) {
            Ok(r) => r,
            Err(e) => return format!("ERR:{e}"),
        };
        let mut lines = vec![byte_git::current_branch(&repo).unwrap_or_else(|| "(no commits)".into())];
        match byte_git::status(&repo) {
            Ok(list) => {
                for f in list {
                    lines.push(format!("{}|{}|{}", f.state, if f.staged { 1 } else { 0 }, f.path));
                }
                lines.join("\n")
            }
            Err(e) => format!("ERR:{e}"),
        }
    });
    ret_str(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_gitCommit(
    mut env: JNIEnv,
    _c: JClass,
    root: JString,
    message: JString,
    name: JString,
    email: JString,
) -> jstring {
    let root = get_string(&mut env, &root);
    let message = get_string(&mut env, &message);
    let name = get_string(&mut env, &name);
    let email = get_string(&mut env, &email);
    let out = guard("ERR:panic".to_string(), || {
        let repo = match byte_git::open_or_init(Path::new(&root)) {
            Ok(r) => r,
            Err(e) => return format!("ERR:{e}"),
        };
        byte_git::commit_all(&repo, &message, &name, &email).unwrap_or_else(|e| format!("ERR:{e}"))
    });
    ret_str(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_dev_byte_ide_core_Native_gitPush(
    mut env: JNIEnv,
    _c: JClass,
    root: JString,
    remote: JString,
    branch: JString,
    token: JString,
) -> jstring {
    let root = get_string(&mut env, &root);
    let remote = get_string(&mut env, &remote);
    let branch = get_string(&mut env, &branch);
    let token = get_string(&mut env, &token);
    let out = guard("ERR:panic".to_string(), || {
        let repo = match byte_git::open_or_init(Path::new(&root)) {
            Ok(r) => r,
            Err(e) => return format!("ERR:{e}"),
        };
        match byte_git::push(&repo, &remote, &branch, &token) {
            Ok(()) => "ok".to_string(),
            Err(e) => format!("ERR:{e}"),
        }
    });
    ret_str(&mut env, &out)
}
