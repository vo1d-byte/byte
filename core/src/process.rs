//! Native child-process supervisor for build tasks and command execution.
//!
//! Commands are launched directly (never through an implicit shell). Callers must
//! opt into a shell explicitly by launching /system/bin/sh with arguments.
//! This module uses pipes; a real PTY backend is a separate platform adapter.

use std::collections::HashMap;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{mpsc, Arc, Mutex};
use std::thread;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ProcessEvent {
    Output { process_id: u64, stream: OutputStream, text: String },
    Exited { process_id: u64, code: Option<i32> },
    Failed { process_id: u64, message: String },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OutputStream { Stdout, Stderr }

struct ManagedProcess {
    child: Child,
    stdin: ChildStdin,
}

#[derive(Debug, Clone)]
pub struct ProcessSpec {
    pub program: String,
    pub args: Vec<String>,
    pub working_directory: PathBuf,
    pub environment: HashMap<String, String>,
}

impl ProcessSpec {
    pub fn new(program: impl Into<String>, working_directory: impl Into<PathBuf>) -> Self {
        Self { program: program.into(), args: Vec::new(), working_directory: working_directory.into(), environment: HashMap::new() }
    }
}

pub struct ProcessManager {
    next_id: AtomicU64,
    processes: Mutex<HashMap<u64, ManagedProcess>>,
    event_tx: mpsc::Sender<ProcessEvent>,
    event_rx: Mutex<mpsc::Receiver<ProcessEvent>>,
}

impl Default for ProcessManager {
    fn default() -> Self { Self::new() }
}

impl ProcessManager {
    pub fn new() -> Self {
        let (event_tx, event_rx) = mpsc::channel();
        Self { next_id: AtomicU64::new(1), processes: Mutex::new(HashMap::new()), event_tx, event_rx: Mutex::new(event_rx) }
    }

    pub fn spawn(&self, spec: ProcessSpec) -> Result<u64, String> {
        if spec.program.trim().is_empty() { return Err("Program must not be empty".into()); }
        if !spec.working_directory.is_dir() { return Err("Working directory does not exist".into()); }
        for key in spec.environment.keys() {
            if key.is_empty() || key.contains('=') || key.contains('\0') { return Err("Invalid environment variable name".into()); }
        }

        let mut command = Command::new(&spec.program);
        command.args(&spec.args).current_dir(&spec.working_directory)
            .envs(&spec.environment).stdin(Stdio::piped())
            .stdout(Stdio::piped()).stderr(Stdio::piped());
        let mut child = command.spawn().map_err(|e| format!("Unable to start process: {e}"))?;
        let stdin = child.stdin.take().ok_or("Failed to open process stdin")?;
        let stdout = child.stdout.take().ok_or("Failed to open process stdout")?;
        let stderr = child.stderr.take().ok_or("Failed to open process stderr")?;
        let id = self.next_id.fetch_add(1, Ordering::Relaxed);
        Self::forward_output(id, stdout, OutputStream::Stdout, self.event_tx.clone());
        Self::forward_output(id, stderr, OutputStream::Stderr, self.event_tx.clone());
        self.processes.lock().map_err(|_| "Process registry is unavailable")?.insert(id, ManagedProcess { child, stdin });
        Ok(id)
    }

    fn forward_output<R: Read + Send + 'static>(id: u64, mut reader: R, stream: OutputStream, tx: mpsc::Sender<ProcessEvent>) {
        thread::spawn(move || {
            let mut buffer = [0_u8; 4096];
            loop {
                match reader.read(&mut buffer) {
                    Ok(0) => break,
                    Ok(count) => {
                        let text = String::from_utf8_lossy(&buffer[..count]).into_owned();
                        if tx.send(ProcessEvent::Output { process_id: id, stream, text }).is_err() { break; }
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::Interrupted => continue,
                    Err(error) => {
                        let _ = tx.send(ProcessEvent::Failed { process_id: id, message: error.to_string() });
                        break;
                    }
                }
            }
        });
    }

    pub fn write_stdin(&self, id: u64, input: &str) -> Result<(), String> {
        let mut processes = self.processes.lock().map_err(|_| "Process registry is unavailable")?;
        let process = processes.get_mut(&id).ok_or("Unknown process")?;
        process.stdin.write_all(input.as_bytes()).and_then(|_| process.stdin.flush()).map_err(|e| e.to_string())
    }

    pub fn terminate(&self, id: u64) -> Result<(), String> {
        let mut processes = self.processes.lock().map_err(|_| "Process registry is unavailable")?;
        let process = processes.get_mut(&id).ok_or("Unknown process")?;
        process.child.kill().map_err(|e| e.to_string())
    }

    pub fn poll(&self) -> Vec<ProcessEvent> {
        let mut events = Vec::new();
        if let Ok(rx) = self.event_rx.lock() {
            while let Ok(event) = rx.try_recv() { events.push(event); }
        }
        if let Ok(mut processes) = self.processes.lock() {
            let ids: Vec<u64> = processes.keys().copied().collect();
            for id in ids {
                if let Some(process) = processes.get_mut(&id) {
                    match process.child.try_wait() {
                        Ok(Some(status)) => {
                            events.push(ProcessEvent::Exited { process_id: id, code: status.code() });
                        }
                        Ok(None) => {}
                        Err(error) => events.push(ProcessEvent::Failed { process_id: id, message: error.to_string() }),
                    }
                }
            }
            processes.retain(|_, process| process.child.try_wait().ok().flatten().is_none());
        }
        events
    }

    pub fn active_process_ids(&self) -> Vec<u64> {
        self.processes.lock().map(|p| p.keys().copied().collect()).unwrap_or_default()
    }

    pub fn terminate_all(&self) {
        if let Ok(mut processes) = self.processes.lock() {
            for process in processes.values_mut() { let _ = process.child.kill(); }
            processes.clear();
        }
    }
}

impl Drop for ProcessManager {
    fn drop(&mut self) {
        if let Ok(processes) = self.processes.get_mut() {
            for process in processes.values_mut() { let _ = process.child.kill(); }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_missing_working_directory() {
        let manager = ProcessManager::new();
        let spec = ProcessSpec::new("sh", Path::new("/definitely/not/a/byte/workspace"));
        assert!(manager.spawn(spec).is_err());
    }

    #[test]
    fn rejects_empty_program() {
        let manager = ProcessManager::new();
        let spec = ProcessSpec::new("  ", std::env::current_dir().unwrap());
        assert!(manager.spawn(spec).is_err());
    }

    #[cfg(unix)]
    #[test]
    fn launches_process_and_streams_output() {
        let manager = ProcessManager::new();
        let mut spec = ProcessSpec::new("sh", std::env::current_dir().unwrap());
        spec.args = vec!["-c".into(), "printf byte-process-ok".into()];
        let id = manager.spawn(spec).unwrap();
        let mut output = String::new();
        for _ in 0..100 {
            for event in manager.poll() {
                if let ProcessEvent::Output { process_id, text, .. } = event {
                    if process_id == id { output.push_str(&text); }
                }
            }
            if !manager.active_process_ids().contains(&id) { break; }
            std::thread::sleep(std::time::Duration::from_millis(5));
        }
        assert!(output.contains("byte-process-ok"), "captured: {output:?}");
    }
}
