//! Pseudo-terminal sessions for interactive shells and terminal applications.
//! A PTY gives the child a controlling terminal, unlike piped stdin/stdout.

use nix::pty::openpty;
use std::fs::File;
use std::io::{Read, Write};
use std::os::fd::{AsRawFd, FromRawFd};
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Child, Command};
use std::sync::mpsc::{self, Receiver};
use std::thread;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PtyUpdate {
    pub output: String,
    pub running: bool,
    pub exit_code: Option<i32>,
}

pub struct PtySession {
    child: Child,
    master: File,
    output_rx: Receiver<String>,
    finished: bool,
    exit_code: Option<i32>,
}

impl PtySession {
    pub fn spawn(
        program: &str,
        args: &[String],
        working_directory: &Path,
        rows: u16,
        columns: u16,
    ) -> Result<Self, String> {
        if program.trim().is_empty() { return Err("Program must not be empty".into()); }
        if !working_directory.is_dir() { return Err("Working directory does not exist".into()); }
        let size = nix::pty::Winsize {
            ws_row: rows.max(1),
            ws_col: columns.max(1),
            ws_xpixel: 0,
            ws_ypixel: 0,
        };
        let pair = openpty(Some(&size), None).map_err(|e| format!("openpty failed: {e}"))?;
        let master = File::from(pair.master);
        let slave_fd = pair.slave.as_raw_fd();
        let master_fd = master.as_raw_fd();

        let mut command = Command::new(program);
        command.args(args).current_dir(working_directory);
        // SAFETY: this child-only hook performs async-signal-safe libc calls before exec.
        unsafe {
            command.pre_exec(move || {
                if libc::setsid() == -1 { return Err(std::io::Error::last_os_error()); }
                if libc::ioctl(slave_fd, libc::TIOCSCTTY as _, 0) == -1 {
                    return Err(std::io::Error::last_os_error());
                }
                for target in 0..=2 {
                    if libc::dup2(slave_fd, target) == -1 {
                        return Err(std::io::Error::last_os_error());
                    }
                }
                if master_fd > 2 { libc::close(master_fd); }
                if slave_fd > 2 { libc::close(slave_fd); }
                Ok(())
            });
        }
        let child = command.spawn().map_err(|e| format!("PTY child spawn failed: {e}"))?;
        drop(pair.slave);

        let mut reader = master.try_clone().map_err(|e| format!("PTY reader clone failed: {e}"))?;
        let (tx, output_rx) = mpsc::channel();
        thread::Builder::new().name("byte-pty-reader".into()).spawn(move || {
            let mut buffer = [0_u8; 8192];
            loop {
                match reader.read(&mut buffer) {
                    Ok(0) => break,
                    Ok(count) => {
                        if tx.send(String::from_utf8_lossy(&buffer[..count]).into_owned()).is_err() { break; }
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::Interrupted => continue,
                    // Linux PTY masters commonly return EIO after the slave closes.
                    Err(error) if error.raw_os_error() == Some(libc::EIO) => break,
                    Err(_) => break,
                }
            }
        }).map_err(|e| format!("PTY reader thread failed: {e}"))?;

        Ok(Self { child, master, output_rx, finished: false, exit_code: None })
    }

    pub fn write(&mut self, bytes: &[u8]) -> Result<(), String> {
        self.master.write_all(bytes).and_then(|_| self.master.flush()).map_err(|e| format!("PTY write failed: {e}"))
    }

    pub fn resize(&self, rows: u16, columns: u16) -> Result<(), String> {
        let size = nix::pty::Winsize { ws_row: rows.max(1), ws_col: columns.max(1), ws_xpixel: 0, ws_ypixel: 0 };
        let result = unsafe { libc::ioctl(self.master.as_raw_fd(), libc::TIOCSWINSZ as _, &size) };
        if result == -1 { Err(format!("PTY resize failed: {}", std::io::Error::last_os_error())) } else { Ok(()) }
    }

    pub fn signal_foreground_group(&self, signal: i32) -> Result<(), String> {
        let allowed = [libc::SIGINT, libc::SIGTERM, libc::SIGTSTP, libc::SIGCONT, libc::SIGHUP, libc::SIGWINCH];
        if !allowed.contains(&signal) { return Err("Unsupported terminal signal".into()); }
        let pid = self.child.id() as i32;
        let result = unsafe { libc::kill(-pid, signal) };
        if result == -1 { Err(format!("Signal delivery failed: {}", std::io::Error::last_os_error())) } else { Ok(()) }
    }

    pub fn is_running(&mut self) -> bool {
        if self.finished { return false; }
        match self.child.try_wait() {
            Ok(Some(status)) => {
                self.finished = true;
                self.exit_code = status.code();
                false
            }
            Ok(None) => true,
            Err(_) => {
                self.finished = true;
                false
            }
        }
    }

    pub fn poll(&mut self) -> PtyUpdate {
        let mut output = String::new();
        while let Ok(chunk) = self.output_rx.try_recv() { output.push_str(&chunk); }
        if !self.finished {
            match self.child.try_wait() {
                Ok(Some(status)) => {
                    self.finished = true;
                    self.exit_code = status.code();
                }
                Ok(None) => {}
                Err(_) => {
                    self.finished = true;
                    self.exit_code = None;
                }
            }
        }
        PtyUpdate { output, running: !self.finished, exit_code: self.exit_code }
    }

    pub fn terminate(&mut self) {
        let _ = self.signal_foreground_group(libc::SIGHUP);
        let _ = self.child.kill();
        let _ = self.child.wait();
        self.finished = true;
    }
}

impl Drop for PtySession {
    fn drop(&mut self) {
        if !self.finished {
            let _ = self.child.kill();
            let _ = self.child.wait();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(target_os = "linux")]
    #[test]
    fn creates_controlling_terminal_and_streams_output() {
        let mut session = PtySession::spawn(
            "/system/bin/sh",
            &["-c".into(), "printf byte-pty-ok".into()],
            Path::new("/"),
            24,
            80,
        ).or_else(|_| PtySession::spawn(
            "/bin/sh",
            &["-c".into(), "printf byte-pty-ok".into()],
            Path::new("/"),
            24,
            80,
        )).unwrap();
        let mut output = String::new();
        for _ in 0..100 {
            let update = session.poll();
            output.push_str(&update.output);
            if !update.running { break; }
            std::thread::sleep(std::time::Duration::from_millis(10));
        }
        assert!(output.contains("byte-pty-ok"), "PTY output: {output:?}");
    }

    #[test]
    fn rejects_invalid_working_directory() {
        assert!(PtySession::spawn("sh", &[], Path::new("/not/a/real/byte/path"), 24, 80).is_err());
    }
}
