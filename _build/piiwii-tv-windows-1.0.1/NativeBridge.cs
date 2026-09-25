using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace PiiWiiTV;

internal static class NativeBridge
{
    internal const int WM_COPYDATA = 0x004A;
    internal const nint CommandSignature = 0x50545731;
    [StructLayout(LayoutKind.Sequential)] internal struct COPYDATASTRUCT { public nint dwData; public int cbData; public nint lpData; }
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, nint lParam);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(nint hWnd);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(nint hWnd, out uint processId);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern nint SendMessage(nint hWnd, uint msg, nint wParam, ref COPYDATASTRUCT lParam);
    private delegate bool EnumWindowsProc(nint hWnd, nint lParam);

    internal static bool SendActionToRunningInstance(string action)
    {
        nint target = 0;
        EnumWindows((hwnd, _) => {
            if (!IsWindowVisible(hwnd)) return true;
            GetWindowThreadProcessId(hwnd, out var pid);
            try { using var p = Process.GetProcessById((int)pid); if (string.Equals(p.ProcessName, "PiiWiiTV", StringComparison.OrdinalIgnoreCase)) { target = hwnd; return false; } } catch { }
            return true;
        }, 0);
        return target != 0 && SendAction(target, action);
    }

    internal static bool SendAction(nint target, string action)
    {
        var bytes = Encoding.UTF8.GetBytes(action + "\0");
        var ptr = Marshal.AllocHGlobal(bytes.Length);
        try { Marshal.Copy(bytes,0,ptr,bytes.Length); var cds = new COPYDATASTRUCT{dwData=CommandSignature,cbData=bytes.Length,lpData=ptr}; SendMessage(target,WM_COPYDATA,0,ref cds); return true; }
        finally { Marshal.FreeHGlobal(ptr); }
    }
}
