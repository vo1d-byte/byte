//! Native pseudo-terminal. Spawns a program attached to a PTY and exposes
//! raw byte read/write plus resize. ANSI/VT parsing is done by the UI layer
//! (planned: `vte`).
#![cfg(unix)]

use std::ffi::CStr;
use std::fs::File;
use std::io::{self, Write};
use std::os::fd::{FromRawFd, OwnedFd};
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Child, Command, Stdio};

pub struct Pty {
    master: File,
    child: Child,
}

fn set_winsize(fd: i32, rows: u16, cols: u16) -> io::Result<()> {
    let ws = libc::winsize { ws_row: rows, ws_col: cols, ws_xpixel: 0, ws_ypixel: 0 };
    // SAFETY: valid fd and pointer to a properly initialised winsize.
    let rc = unsafe { libc::ioctl(fd, libc::TIOCSWINSZ as _, &ws) };
    if rc == 0 { Ok(()) } else { Err(io::Error::last_os_error()) }
}

impl Pty {
    pub fn spawn(
        program: &str,
        args: &[String],
        cwd: &Path,
        env: &[(String, String)],
        rows: u16,
        cols: u16,
    ) -> io::Result<Pty> {
        // SAFETY: plain libc calls; every fd is closed or owned before return.
        unsafe {
            let m = libc::posix_openpt(libc::O_RDWR | libc::O_NOCTTY);
            if m < 0 {
                return Err(io::Error::last_os_error());
            }
            let fail = |m: i32| {
                let e = io::Error::last_os_error();
                libc::close(m);
                e
            };
            if libc::grantpt(m) != 0 || libc::unlockpt(m) != 0 {
                return Err(fail(m));
            }
            let mut name = [0 as libc::c_char; 128];
            if libc::ptsname_r(m, name.as_mut_ptr(), name.len()) != 0 {
                return Err(fail(m));
            }
            let slave_path = CStr::from_ptr(name.as_ptr()).to_owned();
            let s = libc::open(slave_path.as_ptr(), libc::O_RDWR | libc::O_NOCTTY);
            if s < 0 {
                return Err(fail(m));
            }
            libc::fcntl(m, libc::F_SETFD, libc::FD_CLOEXEC);
            let _ = set_winsize(m, rows, cols);

            let dup = |fd: i32| OwnedFd::from_raw_fd(libc::dup(fd));
            let mut cmd = Command::new(program);
            cmd.args(args)
                .current_dir(cwd)
                .env("TERM", "xterm-256color")
                .envs(env.iter().map(|(k, v)| (k.as_str(), v.as_str())))
                .stdin(Stdio::from(dup(s)))
                .stdout(Stdio::from(dup(s)))
                .stderr(Stdio::from(dup(s)));
            cmd.pre_exec(|| {
                libc::setsid();
                libc::ioctl(0, libc::TIOCSCTTY as _, 0);
                Ok(())
            });
            let spawned = cmd.spawn();
            libc::close(s);
            match spawned {
                Ok(child) => Ok(Pty { master: File::from_raw_fd(m), child }),
                Err(e) => {
                    libc::close(m);
                    Err(e)
                }
            }
        }
    }

    /// An independent handle for a blocking reader thread.
    pub fn reader(&self) -> io::Result<File> {
        self.master.try_clone()
    }

    pub fn write(&self, data: &[u8]) -> io::Result<()> {
        (&self.master).write_all(data)
    }

    pub fn resize(&self, rows: u16, cols: u16) -> io::Result<()> {
        use std::os::fd::AsRawFd;
        set_winsize(self.master.as_raw_fd(), rows, cols)
    }

    pub fn try_wait(&mut self) -> io::Result<Option<i32>> {
        Ok(self.child.try_wait()?.and_then(|s| s.code()))
    }

    pub fn kill(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

impl Drop for Pty {
    fn drop(&mut self) {
        self.kill();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;

    #[test]
    fn echo_roundtrip() {
        let pty = Pty::spawn(
            "/bin/sh",
            &["-c".into(), "echo hello-byte".into()],
            Path::new("/"),
            &[],
            24,
            80,
        )
        .expect("spawn");
        let mut r = pty.reader().unwrap();
        let mut out = String::new();
        let mut buf = [0u8; 256];
        while !out.contains("hello-byte") {
            match r.read(&mut buf) {
                Ok(0) | Err(_) => break,
                Ok(n) => out.push_str(&String::from_utf8_lossy(&buf[..n])),
            }
        }
        assert!(out.contains("hello-byte"));
    }
}
