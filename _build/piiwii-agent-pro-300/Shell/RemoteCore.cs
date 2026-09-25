using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Channels;
using Microsoft.Win32;

namespace PiiWii.Remote.Agent;

internal sealed class RemoteCore : IDisposable
{
    const string Protocol = "PIIWII_REMOTE/1";
    readonly object gate = new();
    readonly Channel<(string cmd, string payload)> commands = Channel.CreateBounded<(string, string)>(new BoundedChannelOptions(256) { FullMode = BoundedChannelFullMode.DropOldest, SingleReader = true, SingleWriter = false });
    readonly ConcurrentDictionary<string, (DateTime at, string ack)> replay = new();
    CancellationTokenSource? cts;
    UdpClient? udp;
    Task? networkTask, commandTask, pointerTask;
    int pointerX, pointerY, pointerDirty;
    string code = "------", lastPhone = "";
    volatile bool listening;

    internal event Action? StatusChanged;
    internal bool IsListening => listening;
    internal string PairingCode => code;
    internal string LastPhone => lastPhone;
    internal string LocalIp => FindLocalIPv4();

    internal void Start()
    {
        lock (gate)
        {
            if (cts != null) return;
            cts = new CancellationTokenSource();
            code = NewPairingCode();
            lastPhone = ReadReg(@"Software\PiiWii\Remote2", "LastPhone") ?? "";
            commandTask = Task.Run(() => CommandLoop(cts.Token));
            pointerTask = Task.Run(() => PointerLoop(cts.Token));
            networkTask = Task.Run(() => NetworkLoop(cts.Token));
        }
    }

    internal void Restart()
    {
        DisposeCore();
        Thread.Sleep(250);
        Start();
    }

    async Task NetworkLoop(CancellationToken token)
    {
        try
        {
            udp = new UdpClient(AddressFamily.InterNetwork);
            udp.Client.ReceiveBufferSize = 512 * 1024;
            udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, false);
            udp.Client.Bind(new IPEndPoint(IPAddress.Any, Program.Port));
            listening = true; StatusChanged?.Invoke();
            while (!token.IsCancellationRequested)
            {
                UdpReceiveResult r;
                try { r = await udp.ReceiveAsync(token); }
                catch (OperationCanceledException) { break; }
                catch (ObjectDisposedException) { break; }
                if (!IsPrivateOrLoopback(r.RemoteEndPoint.Address)) continue;
                string raw = Encoding.UTF8.GetString(r.Buffer);
                if (!TryParseFrame(raw, out var requestId, out var cmd, out var payload)) continue;

                if (cmd == "PING")
                {
                    await Reply(r.RemoteEndPoint, requestId, "PONG", $"PiiWii Remote Agent\n{Program.Version}\n{Environment.MachineName}");
                    continue;
                }
                if (cmd == "PAIR_REQUEST")
                {
                    var p = payload.Split('\n', 3);
                    if (p.Length != 3 || string.IsNullOrWhiteSpace(p[0]) || p[2].Trim() != code)
                        await Reply(r.RemoteEndPoint, requestId, "PAIR_RESPONSE", "ERROR\nCode invalide. Vérifie l’IP locale et le code affiché dans l’Agent.");
                    else
                    {
                        string client = p[0].Trim(); string tokenValue = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();
                        SaveToken(client, tokenValue); SetLastPhone(r.RemoteEndPoint.Address.ToString());
                        await Reply(r.RemoteEndPoint, requestId, "PAIR_RESPONSE", "OK\n" + tokenValue);
                    }
                    continue;
                }

                if (!TryAuth(payload, out var clientId, out var securedPayload)) continue;
                SetLastPhone(r.RemoteEndPoint.Address.ToString());
                if (cmd == "UNPAIR") { DeleteToken(clientId); await Reply(r.RemoteEndPoint, requestId, "UNPAIR_RESPONSE", "OK"); continue; }
                if (cmd == "APP_LIST_REQUEST") { await Reply(r.RemoteEndPoint, requestId, "APP_LIST_RESPONSE", BuildAppList()); continue; }

                if (cmd == "POINTER_MOVE")
                {
                    var p = securedPayload.Split(',', 2);
                    if (p.Length == 2 && int.TryParse(p[0], out var dx) && int.TryParse(p[1], out var dy))
                    { Interlocked.Add(ref pointerX, Math.Clamp(dx, -500, 500)); Interlocked.Add(ref pointerY, Math.Clamp(dy, -500, 500)); Interlocked.Exchange(ref pointerDirty, 1); }
                    continue;
                }
                if (cmd == "POINTER_SCROLL" || cmd == "VOICE_FRAME") { commands.Writer.TryWrite((cmd, securedPayload)); continue; }

                CleanupReplay();
                string key = clientId + "|" + requestId + "|" + cmd;
                if (replay.TryGetValue(key, out var old)) { await Reply(r.RemoteEndPoint, requestId, "COMMAND_ACK", old.ack); continue; }
                commands.Writer.TryWrite((cmd, securedPayload));
                replay[key] = (DateTime.UtcNow, "OK");
                await Reply(r.RemoteEndPoint, requestId, "COMMAND_ACK", "OK");
            }
        }
        catch (Exception ex) { CrashLog.Write("Network", ex); }
        finally { listening = false; StatusChanged?.Invoke(); try { udp?.Dispose(); } catch { } udp = null; }
    }

    async Task Reply(IPEndPoint ep, string id, string cmd, string payload)
    {
        try { var bytes = Encoding.UTF8.GetBytes(MakeFrame(id, cmd, payload)); if (udp != null) await udp.SendAsync(bytes, ep); } catch { }
    }

    async Task CommandLoop(CancellationToken token)
    {
        try { await foreach (var j in commands.Reader.ReadAllAsync(token)) Execute(j.cmd, j.payload); }
        catch (OperationCanceledException) { }
        catch (Exception ex) { CrashLog.Write("CommandLoop", ex); }
    }

    async Task PointerLoop(CancellationToken token)
    {
        try
        {
            while (!token.IsCancellationRequested)
            {
                await Task.Delay(16, token);
                if (Interlocked.Exchange(ref pointerDirty, 0) == 0) continue;
                int dx = Interlocked.Exchange(ref pointerX, 0), dy = Interlocked.Exchange(ref pointerY, 0);
                if (dx != 0 || dy != 0) MouseMove(Math.Clamp(dx, -5000, 5000), Math.Clamp(dy, -5000, 5000));
            }
        }
        catch (OperationCanceledException) { }
    }

    static bool TryParseFrame(string raw, out string id, out string cmd, out string payload)
    {
        id = cmd = payload = ""; var p = raw.Split('|', 4); if (p.Length != 4 || p[0] != Protocol) return false;
        try { id = p[1]; cmd = p[2]; payload = Encoding.UTF8.GetString(Convert.FromBase64String(p[3])); return true; } catch { return false; }
    }
    static string MakeFrame(string id, string cmd, string payload) => $"{Protocol}|{id}|{cmd}|{Convert.ToBase64String(Encoding.UTF8.GetBytes(payload))}";

    static bool TryAuth(string payload, out string client, out string data)
    {
        client = data = ""; if (!payload.StartsWith("AUTH1\n", StringComparison.Ordinal)) return false;
        var p = payload[6..].Split('\n', 3); if (p.Length != 3) return false;
        client = p[0].Trim(); string expected = ReadToken(client); if (expected.Length == 0 || !CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(p[1].Trim()))) return false;
        data = p[2]; return true;
    }

    void Execute(string cmd, string payload)
    {
        try
        {
            if (TryTvAction(cmd, payload, out var action)) { DispatchTvAction(action); return; }
            if (KeyMap.TryGetValue(cmd, out var vk)) { TapKey(vk); return; }
            switch (cmd)
            {
                case "POINTER_LEFT_CLICK": MouseClick(false); break;
                case "POINTER_RIGHT_CLICK": MouseClick(true); break;
                case "POINTER_DOUBLE_CLICK": MouseClick(false); MouseClick(false); break;
                case "POINTER_SCROLL": if (int.TryParse(payload, out var wheel)) MouseWheel(Math.Clamp(wheel, -2400, 2400)); break;
                case "KEYBOARD_TEXT": SendUnicode(payload); break;
                case "KEYBOARD_ENTER": TapKey(0x0D); break;
                case "KEYBOARD_BACKSPACE": TapKey(0x08); break;
                case "KEYBOARD_ESCAPE": TapKey(0x1B); break;
                case "KEYBOARD_TAB": TapKey(0x09); break;
                case "KEYBOARD_KEY": SendKeyCombo(payload); break;
                case "APP_LAUNCH": LaunchKnown(payload); break;
                case "APP_CUSTOM": LaunchCustom(payload); break;
                case "POWER_SLEEP": SetSuspendState(false, false, false); break;
                case "POWER_LOCK": LockWorkStation(); break;
                case "POWER_RESTART": StartHidden("shutdown.exe", "/r /t 0"); break;
                case "POWER_SHUTDOWN": StartHidden("shutdown.exe", "/s /t 0"); break;
                case "VOICE_START": case "VOICE_FRAME": case "VOICE_STOP": break;
            }
        }
        catch (Exception ex) { CrashLog.Write("Execute:" + cmd, ex); }
    }

    static readonly Dictionary<string, ushort> KeyMap = new(StringComparer.Ordinal)
    {
        ["NAV_UP"] = 0x26, ["NAV_DOWN"] = 0x28, ["NAV_LEFT"] = 0x25, ["NAV_RIGHT"] = 0x27, ["NAV_OK"] = 0x0D,
        ["NAV_BACK"] = 0xA6, ["NAV_HOME"] = 0x24, ["NAV_MENU"] = 0x5D, ["MEDIA_PLAY_PAUSE"] = 0xB3, ["MEDIA_STOP"] = 0xB2,
        ["MEDIA_NEXT"] = 0xB0, ["MEDIA_PREVIOUS"] = 0xB1, ["VOLUME_UP"] = 0xAF, ["VOLUME_DOWN"] = 0xAE, ["VOLUME_MUTE"] = 0xAD
    };

    static string NewPairingCode()
    {
        int n = RandomNumberGenerator.GetInt32(100000, 1000000); string c = n.ToString("000000");
        try { using var k = Registry.CurrentUser.CreateSubKey(@"Software\PiiWii\Remote2\Pairing"); k.SetValue("Code", c); } catch { }
        return c;
    }
    void SetLastPhone(string p) { lastPhone = p; try { using var k = Registry.CurrentUser.CreateSubKey(@"Software\PiiWii\Remote2"); k.SetValue("LastPhone", p); } catch { } StatusChanged?.Invoke(); }
    static void SaveToken(string id, string token) { try { using var k = Registry.CurrentUser.CreateSubKey(@"Software\PiiWii\Remote2\Pairing\Clients"); k.SetValue(id, token); } catch { } }
    static string ReadToken(string id) { try { using var k = Registry.CurrentUser.OpenSubKey(@"Software\PiiWii\Remote2\Pairing\Clients"); return k?.GetValue(id)?.ToString() ?? ""; } catch { return ""; } }
    static void DeleteToken(string id) { try { using var k = Registry.CurrentUser.CreateSubKey(@"Software\PiiWii\Remote2\Pairing\Clients"); k.DeleteValue(id, false); } catch { } }
    static string? ReadReg(string path, string name) { try { using var k = Registry.CurrentUser.OpenSubKey(path); return k?.GetValue(name)?.ToString(); } catch { return null; } }

    void CleanupReplay() { var now = DateTime.UtcNow; foreach (var x in replay) if ((now - x.Value.at).TotalSeconds > 30) replay.TryRemove(x.Key, out _); }
    static bool IsPrivateOrLoopback(IPAddress ip) { if (IPAddress.IsLoopback(ip)) return true; var b = ip.GetAddressBytes(); return b.Length == 4 && (b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 192 && b[1] == 168)); }
    static string FindLocalIPv4()
    {
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces()) if (ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType is not (NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel))
            foreach (var ua in ni.GetIPProperties().UnicastAddresses) if (ua.Address.AddressFamily == AddressFamily.InterNetwork && IsPrivateOrLoopback(ua.Address) && !IPAddress.IsLoopback(ua.Address)) return ua.Address.ToString();
        return "Indisponible";
    }

    static string BuildAppList()
    {
        var a = new List<string>(); if (FindPiiWiiTvExe() != null) a.Add("piiwii_tv\tPiiWii TV"); a.Add("browser\tNavigateur Internet");
        if (File.Exists(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Spotify", "Spotify.exe"))) a.Add("spotify\tSpotify");
        if (FindProgram("VideoLAN", "VLC", "vlc.exe") != null) a.Add("vlc\tVLC"); if (FindProgram("Kodi", "kodi.exe") != null) a.Add("kodi\tKodi"); return string.Join('\n', a);
    }
    static string? FindProgram(params string[] parts) { foreach (var root in new[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86) }) { var p = Path.Combine(new[] { root }.Concat(parts).ToArray()); if (File.Exists(p)) return p; } return null; }
    static void LaunchKnown(string id)
    {
        switch (id.Trim().ToLowerInvariant())
        {
            case "piiwii_tv": if (FindPiiWiiTvExe() is string tv) Process.Start(new ProcessStartInfo(tv) { UseShellExecute = true }); break;
            case "browser": Process.Start(new ProcessStartInfo("https://piiwii.ch") { UseShellExecute = true }); break;
            case "spotify": var sp = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Spotify", "Spotify.exe"); if (File.Exists(sp)) Process.Start(sp); break;
            case "vlc": if (FindProgram("VideoLAN", "VLC", "vlc.exe") is string vlc) Process.Start(vlc); break;
            case "kodi": if (FindProgram("Kodi", "kodi.exe") is string kodi) Process.Start(kodi); break;
        }
    }
    static void LaunchCustom(string data) { var p = data.Split('\n', 2); if (p.Length != 2) return; if (p[0].Trim().Equals("URL", StringComparison.OrdinalIgnoreCase)) Process.Start(new ProcessStartInfo(p[1].Trim()) { UseShellExecute = true }); else if (p[0].Trim().Equals("EXE", StringComparison.OrdinalIgnoreCase) && File.Exists(p[1].Trim())) Process.Start(new ProcessStartInfo(p[1].Trim()) { UseShellExecute = true }); }

    static bool TryTvAction(string cmd, string payload, out string action)
    {
        action = cmd switch { "PIIWII_TV_APP_ACTION" => payload.Trim(), "PIIWII_TV_CHANNEL_UP" => "channel_up", "PIIWII_TV_CHANNEL_DOWN" => "channel_down", "PIIWII_TV_VOLUME_UP" => "volume_up", "PIIWII_TV_VOLUME_DOWN" => "volume_down", "PIIWII_TV_MUTE" => "mute", _ => "" };
        return action.Length > 0 && SafeTvAction(action);
    }
    static bool SafeTvAction(string a)
    {
        a = a.Trim().ToLowerInvariant();
        if (a.StartsWith("channel:")) { var id = a[8..]; return id.Length is > 0 and <= 160 && id.All(ch => char.IsAsciiLetterOrDigit(ch) || ch is '-' or '_'); }
        return new HashSet<string> { "power","direct","channel_up","channel_down","volume_up","volume_down","mute","play_pause","seek_back","seek_forward","record","guide","recordings","radio","settings","home","nav_up","nav_down","nav_left","nav_right","nav_ok","back","fullscreen","browser_fullscreen","windowed","remote_test" }.Contains(a);
    }
    static bool DispatchTvAction(string action)
    {
        foreach (var p in Process.GetProcessesByName("PiiWiiTV")) if (p.MainWindowHandle != IntPtr.Zero && SendCopyData(p.MainWindowHandle, action)) return true;
        var exe = FindPiiWiiTvExe(); if (exe == null) return false; try { Process.Start(new ProcessStartInfo(exe, "--action=" + action) { UseShellExecute = true }); return true; } catch { return false; }
    }
    static string? FindPiiWiiTvExe()
    {
        var p = ReadReg(@"Software\PiiWii\PiiWiiTV", "InstallPath"); if (!string.IsNullOrWhiteSpace(p) && File.Exists(p)) return p;
        p = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "PiiWii TV", "PiiWiiTV.exe"); return File.Exists(p) ? p : null;
    }

    static bool SendCopyData(IntPtr hwnd, string action)
    {
        var bytes = Encoding.UTF8.GetBytes(action + "\0"); var ptr = Marshal.AllocHGlobal(bytes.Length); Marshal.Copy(bytes, 0, ptr, bytes.Length);
        try { var cds = new COPYDATASTRUCT { dwData = (IntPtr)0x50545731, cbData = bytes.Length, lpData = ptr }; var p = Marshal.AllocHGlobal(Marshal.SizeOf<COPYDATASTRUCT>()); Marshal.StructureToPtr(cds, p, false); try { return SendMessageTimeout(hwnd, 0x004A, IntPtr.Zero, p, 0x0003, 250, out var result) != IntPtr.Zero && result != IntPtr.Zero; } finally { Marshal.FreeHGlobal(p); } } finally { Marshal.FreeHGlobal(ptr); }
    }

    static void StartHidden(string file, string args) { try { Process.Start(new ProcessStartInfo(file, args) { UseShellExecute = false, CreateNoWindow = true }); } catch { } }

    static void TapKey(ushort vk) { SendInputs(Kb(vk, false), Kb(vk, true)); }
    static void SendUnicode(string s) { var list = new List<INPUT>(); foreach (char ch in s) { list.Add(KbUnicode(ch, false)); list.Add(KbUnicode(ch, true)); } SendInputs(list.ToArray()); }
    static void SendKeyCombo(string s)
    {
        var keys = s.Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).Select(KeyToken).ToArray(); if (keys.Length == 0 || keys.Any(x => x == 0)) return;
        var list = new List<INPUT>(); foreach (var k in keys) list.Add(Kb(k, false)); for (int i = keys.Length - 1; i >= 0; i--) list.Add(Kb(keys[i], true)); SendInputs(list.ToArray());
    }
    static ushort KeyToken(string s)
    {
        s = s.ToUpperInvariant(); var map = new Dictionary<string, ushort> { ["CTRL"] = 0x11,["ALT"] = 0x12,["SHIFT"] = 0x10,["WIN"] = 0x5B,["ENTER"] = 0x0D,["ESC"] = 0x1B,["ESCAPE"] = 0x1B,["TAB"] = 0x09,["BACKSPACE"] = 0x08,["UP"] = 0x26,["DOWN"] = 0x28,["LEFT"] = 0x25,["RIGHT"] = 0x27 };
        if (map.TryGetValue(s, out var v)) return v; if (s.Length == 1 && char.IsAsciiLetterOrDigit(s[0])) return s[0]; if (s.StartsWith('F') && int.TryParse(s[1..], out var n) && n is >= 1 and <= 24) return (ushort)(0x70 + n - 1); return 0;
    }
    static void MouseMove(int dx, int dy) => SendInputs(Mouse(dx, dy, 0, 0x0001));
    static void MouseClick(bool right) => SendInputs(Mouse(0,0,0,right ? 0x0008u : 0x0002u), Mouse(0,0,0,right ? 0x0010u : 0x0004u));
    static void MouseWheel(int v) => SendInputs(Mouse(0,0,unchecked((uint)v),0x0800));
    static INPUT Kb(ushort vk, bool up) => new() { type = 1, U = new InputUnion { ki = new KEYBDINPUT { wVk = vk, dwFlags = up ? 0x0002u : 0u } } };
    static INPUT KbUnicode(char ch, bool up) => new() { type = 1, U = new InputUnion { ki = new KEYBDINPUT { wScan = ch, dwFlags = 0x0004u | (up ? 0x0002u : 0u) } } };
    static INPUT Mouse(int dx, int dy, uint data, uint flags) => new() { type = 0, U = new InputUnion { mi = new MOUSEINPUT { dx = dx, dy = dy, mouseData = data, dwFlags = flags } } };
    static void SendInputs(params INPUT[] a) { if (a.Length > 0) SendInput((uint)a.Length, a, Marshal.SizeOf<INPUT>()); }

    void DisposeCore()
    {
        CancellationTokenSource? old; lock (gate) { old = cts; cts = null; }
        if (old == null) return; try { old.Cancel(); udp?.Dispose(); } catch { } try { Task.WaitAll(new[] { networkTask, commandTask, pointerTask }.Where(x => x != null).Cast<Task>().ToArray(), 1500); } catch { } old.Dispose(); listening = false; StatusChanged?.Invoke();
    }
    public void Dispose() => DisposeCore();

    [StructLayout(LayoutKind.Sequential)] struct COPYDATASTRUCT { public IntPtr dwData; public int cbData; public IntPtr lpData; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)] struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }
    [DllImport("user32.dll", SetLastError = true)] static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
    [DllImport("user32.dll")] static extern bool LockWorkStation();
    [DllImport("PowrProf.dll")] static extern bool SetSuspendState(bool hibernate, bool forceCritical, bool disableWakeEvent);
    [DllImport("user32.dll", SetLastError = true)] static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam, uint fuFlags, uint uTimeout, out IntPtr lpdwResult);
}
