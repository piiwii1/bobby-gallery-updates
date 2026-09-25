using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace PiiWiiTV;

public sealed class MainForm : Form
{
    private const string TvUrl = "https://piiwii.ch/tv/?piiwii_app=1";
    private readonly WebView2 web = new() { Dock = DockStyle.Fill };
    private readonly Queue<string> pendingActions = new();
    private readonly string? startupAction;
    private bool webReady;
    private bool startupActionConsumed;
    private bool borderlessFullscreen;
    private FormBorderStyle previousBorderStyle = FormBorderStyle.Sizable;
    private Rectangle previousBounds;

    public MainForm(string? startupAction)
    {
        this.startupAction = startupAction;
        Text = "PiiWii TV";
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application; } catch { }
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(900, 600);
        Size = new Size(1360, 820);
        BackColor = Color.Black;
        Controls.Add(web);
        Shown += async (_, _) => await InitializeAsync();
        FormClosing += (_, _) => web.Dispose();
    }

    private async Task InitializeAsync()
    {
        try
        {
            var userData = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PiiWii", "PiiWiiTV", "WebView2");
            var env = await CoreWebView2Environment.CreateAsync(null, userData);
            await web.EnsureCoreWebView2Async(env);
            web.CoreWebView2.Settings.AreDevToolsEnabled = false;
            web.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
            web.CoreWebView2.Settings.IsStatusBarEnabled = false;
            web.CoreWebView2.NewWindowRequested += (_, e) => { e.Handled = true; try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(e.Uri) { UseShellExecute = true }); } catch { } };
            web.CoreWebView2.DocumentTitleChanged += (_, _) => BeginInvoke(new Action(() => Text = string.IsNullOrWhiteSpace(web.CoreWebView2.DocumentTitle) ? "PiiWii TV" : web.CoreWebView2.DocumentTitle));
            web.NavigationCompleted += async (_, e) => {
                if (!e.IsSuccess) return;
                webReady = true;
                while (pendingActions.Count > 0) await DispatchActionAsync(pendingActions.Dequeue());
                if (!startupActionConsumed && !string.IsNullOrWhiteSpace(startupAction)) { startupActionConsumed = true; await DispatchActionAsync(startupAction!); }
            };
            web.Source = new Uri(TvUrl);
        }
        catch (Exception ex)
        {
            MessageBox.Show("PiiWii TV n'a pas pu démarrer.\n\n" + ex.Message + "\n\nRéinstalle PiiWii TV afin de réparer Microsoft WebView2.", "PiiWii TV", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    protected override void WndProc(ref Message m)
    {
        if (m.Msg == NativeBridge.WM_COPYDATA)
        {
            try
            {
                var data = Marshal.PtrToStructure<NativeBridge.COPYDATASTRUCT>(m.LParam);
                if (data.dwData == NativeBridge.CommandSignature && data.cbData > 1 && data.cbData < 4096)
                {
                    var bytes = new byte[data.cbData]; Marshal.Copy(data.lpData, bytes, 0, bytes.Length);
                    var action = Encoding.UTF8.GetString(bytes).TrimEnd('\0').Trim();
                    if (!string.IsNullOrWhiteSpace(action)) { BeginInvoke(new Action(async () => await ReceiveActionAsync(action))); m.Result = 1; return; }
                }
            }
            catch { }
        }
        base.WndProc(ref m);
    }

    private async Task ReceiveActionAsync(string action) { if (!webReady) { pendingActions.Enqueue(action); return; } await DispatchActionAsync(action); }

    private async Task DispatchActionAsync(string action)
    {
        action = action.Trim().ToLowerInvariant();
        switch (action) { case "power": case "fullscreen": EnterBorderlessFullscreen(); break; case "browser_fullscreen": ExitBorderlessFullscreen(true); break; case "windowed": ExitBorderlessFullscreen(false); break; }
        if (web.CoreWebView2 == null) return;
        var webAction = action == "browser_fullscreen" ? "windowed" : action;
        var json = JsonSerializer.Serialize(webAction);
        try { await web.CoreWebView2.ExecuteScriptAsync($"window.PiiWiiTVAppCommand ? window.PiiWiiTVAppCommand({json}) : false;"); } catch { }
    }

    private void EnterBorderlessFullscreen()
    {
        if (borderlessFullscreen) return;
        previousBorderStyle = FormBorderStyle; previousBounds = Bounds; borderlessFullscreen = true;
        WindowState = FormWindowState.Normal; FormBorderStyle = FormBorderStyle.None; Bounds = Screen.FromControl(this).Bounds; TopMost = false;
    }

    private void ExitBorderlessFullscreen(bool maximize)
    {
        if (borderlessFullscreen)
        {
            borderlessFullscreen = false; FormBorderStyle = previousBorderStyle == FormBorderStyle.None ? FormBorderStyle.Sizable : previousBorderStyle;
            if (maximize) WindowState = FormWindowState.Maximized; else { WindowState = FormWindowState.Normal; if (!previousBounds.IsEmpty) Bounds = previousBounds; }
            return;
        }
        FormBorderStyle = FormBorderStyle.Sizable; WindowState = maximize ? FormWindowState.Maximized : FormWindowState.Normal;
    }
}
