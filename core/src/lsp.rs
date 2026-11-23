//! Minimal Language Server Protocol transport primitives.
//! This module implements JSON-RPC 2.0 messages and LSP Content-Length framing.
//! It deliberately does not spawn language-server processes; that belongs to the
//! Android process supervisor / stdio transport layer.
use serde_json::{json, Value};
use std::collections::BTreeMap;
use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LspError {
    MissingHeaderTerminator,
    MissingContentLength,
    InvalidContentLength,
    PayloadTooLarge,
    InvalidUtf8,
    InvalidJson(String),
}
impl fmt::Display for LspError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { write!(f, "{self:?}") }
}
impl std::error::Error for LspError {}

#[derive(Debug, Default)]
pub struct LspFrameDecoder {
    buffer: Vec<u8>,
    max_payload: usize,
}
impl LspFrameDecoder {
    pub fn new(max_payload: usize) -> Self { Self { buffer: Vec::new(), max_payload } }
    pub fn push(&mut self, bytes: &[u8]) -> Result<Vec<Value>, LspError> {
        self.buffer.extend_from_slice(bytes);
        let mut messages = Vec::new();
        loop {
            let Some(header_end) = find_header_end(&self.buffer) else {
                if self.buffer.len() > 8192 { return Err(LspError::MissingHeaderTerminator); }
                break;
            };
            let header = std::str::from_utf8(&self.buffer[..header_end]).map_err(|_| LspError::InvalidUtf8)?;
            let mut content_length = None;
            for line in header.lines() {
                let (key, value) = line.split_once(':').ok_or(LspError::InvalidContentLength)?;
                if key.trim().eq_ignore_ascii_case("Content-Length") {
                    if content_length.is_some() { return Err(LspError::InvalidContentLength); }
                    content_length = Some(value.trim().parse::<usize>().map_err(|_| LspError::InvalidContentLength)?);
                }
            }
            let length = content_length.ok_or(LspError::MissingContentLength)?;
            if length > self.max_payload { return Err(LspError::PayloadTooLarge); }
            let body_start = header_end + 4;
            if self.buffer.len() < body_start + length { break; }
            let body = self.buffer[body_start..body_start + length].to_vec();
            self.buffer.drain(..body_start + length);
            let text = std::str::from_utf8(&body).map_err(|_| LspError::InvalidUtf8)?;
            let value = serde_json::from_str(text).map_err(|e| LspError::InvalidJson(e.to_string()))?;
            messages.push(value);
        }
        Ok(messages)
    }
    pub fn buffered_len(&self) -> usize { self.buffer.len() }
}
fn find_header_end(bytes: &[u8]) -> Option<usize> {
    bytes.windows(4).position(|window| window == b"\r\n\r\n")
}

pub fn encode_message(value: &Value) -> Vec<u8> {
    let body = value.to_string();
    let mut frame = format!("Content-Length: {}\r\n\r\n", body.as_bytes().len()).into_bytes();
    frame.extend_from_slice(body.as_bytes());
    frame
}

#[derive(Debug, Default)]
pub struct LspClientState {
    next_id: i64,
    pending: BTreeMap<i64, String>,
    opened: BTreeMap<String, i64>,
}
impl LspClientState {
    pub fn request(&mut self, method: &str, params: Value) -> (i64, Value) {
        self.next_id += 1;
        let id = self.next_id;
        self.pending.insert(id, method.to_owned());
        (id, json!({"jsonrpc":"2.0","id":id,"method":method,"params":params}))
    }
    pub fn notification(method: &str, params: Value) -> Value {
        json!({"jsonrpc":"2.0","method":method,"params":params})
    }
    pub fn complete_response(&mut self, message: &Value) -> Option<(String, Value)> {
        let id = message.get("id")?.as_i64()?;
        let method = self.pending.remove(&id)?;
        Some((method, message.clone()))
    }
    pub fn pending_count(&self) -> usize { self.pending.len() }
    pub fn did_open(&mut self, uri: &str, language_id: &str, version: i64, text: &str) -> Value {
        self.opened.insert(uri.to_owned(), version);
        Self::notification("textDocument/didOpen", json!({"textDocument":{"uri":uri,"languageId":language_id,"version":version,"text":text}}))
    }
    pub fn did_change(&mut self, uri: &str, version: i64, text: &str) -> Option<Value> {
        let current = self.opened.get_mut(uri)?;
        if version <= *current { return None; }
        *current = version;
        Some(Self::notification("textDocument/didChange", json!({
            "textDocument":{"uri":uri,"version":version},
            "contentChanges":[{"text":text}]
        })))
    }
    pub fn did_close(&mut self, uri: &str) -> Option<Value> {
        self.opened.remove(uri)?;
        Some(Self::notification("textDocument/didClose", json!({"textDocument":{"uri":uri}})))
    }
    pub fn shutdown_request(&mut self) -> (i64, Value) { self.request("shutdown", Value::Null) }
    pub fn exit_notification() -> Value { Self::notification("exit", Value::Null) }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn frame_round_trip_and_partial_chunks() {
        let expected = json!({"jsonrpc":"2.0","method":"initialized","params":{}});
        let frame = encode_message(&expected);
        let mut decoder = LspFrameDecoder::new(1024);
        assert!(decoder.push(&frame[..9]).unwrap().is_empty());
        assert_eq!(decoder.push(&frame[9..]).unwrap(), vec![expected]);
    }
    #[test]
    fn decodes_multiple_messages_from_one_chunk() {
        let mut bytes = encode_message(&json!({"n":1}));
        bytes.extend(encode_message(&json!({"n":2})));
        assert_eq!(LspFrameDecoder::new(1024).push(&bytes).unwrap().len(), 2);
    }
    #[test]
    fn rejects_invalid_content_length() {\n        let frame = b"Content-Length: nope\\r\\n\\r\\n{}";\n        assert_eq!(LspFrameDecoder::new(1024).push(frame), Err(LspError::InvalidContentLength));\n    }\n    #[test]\n    fn enforces_payload_limit() {
        let frame = encode_message(&json!({"data":"0123456789"}));
        assert_eq!(LspFrameDecoder::new(2).push(&frame), Err(LspError::PayloadTooLarge));
    }
    #[test]
    fn tracks_requests_and_document_versions() {
        let mut state = LspClientState::default();
        let (id, _) = state.request("initialize", json!({}));
        assert_eq!(state.pending_count(), 1);
        assert!(state.complete_response(&json!({"jsonrpc":"2.0","id":id,"result":{}})).is_some());
        state.did_open("file:///main.py", "python", 1, "print(1)");
        assert!(state.did_change("file:///main.py", 2, "print(2)").is_some());
        assert!(state.did_change("file:///main.py", 2, "print(3)").is_none());
        assert!(state.did_close("file:///main.py").is_some());
    }
}
