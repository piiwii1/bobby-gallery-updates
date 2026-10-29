//! Shared PiiWii Portal weather signal for native Snowhush PiiWii (October-March).
//! Reads public, read-only weather on https://piiwii.ch via HTTPS.
//! No credentials, personal data or upload requests are involved.
use std::ffi::c_void;
use windows::Win32::Networking::WinHttp::{
    WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, WINHTTP_FLAG_SECURE, WINHTTP_QUERY_FLAG_NUMBER, WINHTTP_QUERY_STATUS_CODE,
    WinHttpCloseHandle, WinHttpConnect, WinHttpOpen, WinHttpOpenRequest, WinHttpQueryHeaders,
    WinHttpReadData, WinHttpReceiveResponse, WinHttpSendRequest, WinHttpSetTimeouts,
};
use windows::Win32::System::SystemInformation::GetLocalTime;
use windows::core::{PCWSTR, w};

pub fn in_season() -> bool {
    let now = unsafe { GetLocalTime() };
    now.wMonth >= 10 || now.wMonth <= 3
}

/// None = WordPress plugin/weather unavailable: fail closed rather than invent snow.
pub fn snowing() -> Option<bool> {
    if !in_season() {
        return Some(false);
    }
    let body = fetch_status()?;
    // WordPress' wp_send_json_success response is a flat, compact JSON object.
    // Avoid treating "available":false (upstream forecast failure) as a clear sky.
    if !body.contains("\"success\":true") || !body.contains("\"season_active\":true") ||
       !body.contains("\"available\":true") {
        return None;
    }
    if body.contains("\"snowing\":true") { return Some(true); }
    if body.contains("\"snowing\":false") { return Some(false); }
    None
}

struct Handle(*mut c_void);
impl Drop for Handle {
    fn drop(&mut self) {
        if !self.0.is_null() {
            unsafe { let _ = WinHttpCloseHandle(self.0); }
        }
    }
}

fn fetch_status() -> Option<String> {
    const MAX_REPLY: usize = 16 * 1024;
    unsafe {
        let session = Handle(WinHttpOpen(
            w!("Snowhush-PiiWii/0.5.8"), WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
            PCWSTR::null(), PCWSTR::null(), 0,
        ));
        if session.0.is_null() { return None; }
        let _ = WinHttpSetTimeouts(session.0, 6000, 6000, 6000, 6000);
        let conn = Handle(WinHttpConnect(session.0, w!("piiwii.ch"), 443, 0));
        if conn.0.is_null() { return None; }
        let request = Handle(WinHttpOpenRequest(
            conn.0, w!("GET"), w!("/wp-admin/admin-ajax.php?action=piiwii_portal_snow_status"),
            PCWSTR::null(), PCWSTR::null(), std::ptr::null(), WINHTTP_FLAG_SECURE,
        ));
        if request.0.is_null() { return None; }
        let headers: Vec<u16> = "Accept: application/json\r\n".encode_utf16().collect();
        WinHttpSendRequest(request.0, Some(&headers), None, 0, 0, 0).ok()?;
        WinHttpReceiveResponse(request.0, std::ptr::null_mut()).ok()?;
        let mut status: u32 = 0;
        let mut size: u32 = 4;
        WinHttpQueryHeaders(
            request.0, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
            PCWSTR::null(), Some((&raw mut status).cast()), &mut size, std::ptr::null_mut()
        ).ok()?;
        if status != 200 { return None; }
        let mut response = Vec::new();
        let mut buffer = [0u8; 4096];
        loop {
            let mut read: u32 = 0;
            WinHttpReadData(request.0, buffer.as_mut_ptr().cast(), buffer.len() as u32, &mut read).ok()?;
            if read == 0 { break; }
            response.extend_from_slice(&buffer[..read as usize]);
            if response.len() > MAX_REPLY { return None; }
        }
        String::from_utf8(response).ok()
    }
}
