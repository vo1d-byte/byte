//! Minimal Git operations on top of libgit2.
use git2::build::RepoBuilder;
use git2::{
    Commit, Cred, FetchOptions, IndexAddOption, PushOptions, RemoteCallbacks, Repository,
    Signature, Status, StatusOptions,
};
use std::path::Path;

pub type Result<T> = std::result::Result<T, git2::Error>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FileStatus {
    pub path: String,
    /// One of: new, modified, deleted, renamed, conflicted
    pub state: &'static str,
    pub staged: bool,
}

pub fn open_or_init(path: &Path) -> Result<Repository> {
    Repository::open(path).or_else(|_| Repository::init(path))
}

pub fn current_branch(repo: &Repository) -> Option<String> {
    repo.head().ok().and_then(|h| h.shorthand().map(str::to_owned))
}

pub fn status(repo: &Repository) -> Result<Vec<FileStatus>> {
    let mut opts = StatusOptions::new();
    opts.include_untracked(true).recurse_untracked_dirs(true);
    let statuses = repo.statuses(Some(&mut opts))?;
    let mut out = Vec::new();
    for s in statuses.iter() {
        let f = s.status();
        let state = if f.contains(Status::CONFLICTED) {
            "conflicted"
        } else if f.intersects(Status::INDEX_NEW | Status::WT_NEW) {
            "new"
        } else if f.intersects(Status::INDEX_DELETED | Status::WT_DELETED) {
            "deleted"
        } else if f.intersects(Status::INDEX_RENAMED | Status::WT_RENAMED) {
            "renamed"
        } else if f.intersects(
            Status::INDEX_MODIFIED | Status::WT_MODIFIED | Status::INDEX_TYPECHANGE | Status::WT_TYPECHANGE,
        ) {
            "modified"
        } else {
            continue;
        };
        out.push(FileStatus {
            path: s.path().unwrap_or("").to_string(),
            state,
            staged: f.intersects(
                Status::INDEX_NEW | Status::INDEX_MODIFIED | Status::INDEX_DELETED | Status::INDEX_RENAMED,
            ),
        });
    }
    Ok(out)
}

/// Stage everything and commit. Returns the new commit id.
pub fn commit_all(repo: &Repository, message: &str, name: &str, email: &str) -> Result<String> {
    let mut index = repo.index()?;
    index.add_all(["*"].iter(), IndexAddOption::DEFAULT, None)?;
    index.update_all(["*"].iter(), None)?;
    index.write()?;
    let tree = repo.find_tree(index.write_tree()?)?;
    let sig = Signature::now(name, email)?;
    let parents: Vec<Commit> = match repo.head() {
        Ok(h) => vec![h.peel_to_commit()?],
        Err(_) => vec![],
    };
    let parent_refs: Vec<&Commit> = parents.iter().collect();
    let id = repo.commit(Some("HEAD"), &sig, &sig, message, &tree, &parent_refs)?;
    Ok(id.to_string())
}

fn token_callbacks<'a>(token: &'a str) -> RemoteCallbacks<'a> {
    let mut cb = RemoteCallbacks::new();
    cb.credentials(move |_url, _user, _allowed| Cred::userpass_plaintext("x-access-token", token));
    cb
}

pub fn clone(url: &str, dest: &Path, token: Option<&str>) -> Result<Repository> {
    let mut fo = FetchOptions::new();
    if let Some(t) = token {
        fo.remote_callbacks(token_callbacks(t));
    }
    RepoBuilder::new().fetch_options(fo).clone(url, dest)
}

pub fn push(repo: &Repository, remote: &str, branch: &str, token: &str) -> Result<()> {
    let mut remote = repo.find_remote(remote)?;
    let mut po = PushOptions::new();
    po.remote_callbacks(token_callbacks(token));
    let spec = format!("refs/heads/{branch}:refs/heads/{branch}");
    remote.push(&[spec.as_str()], Some(&mut po))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn init_commit_status() {
        let dir = std::env::temp_dir().join(format!("byte-git-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let repo = open_or_init(&dir).unwrap();
        std::fs::write(dir.join("a.txt"), "hi").unwrap();
        assert_eq!(status(&repo).unwrap()[0].state, "new");
        commit_all(&repo, "init", "T", "t@example.com").unwrap();
        assert!(status(&repo).unwrap().is_empty());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
