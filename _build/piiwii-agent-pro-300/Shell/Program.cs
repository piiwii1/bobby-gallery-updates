using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using Microsoft.Win32;

namespace PiiWii.Remote.Agent;

internal static class Program
{
    internal const string Product = "PiiWii Remote Agent";
    internal const string Version = "3.0.0";
    internal const int Port = 45821;
    internal const string MutexName = @"Local\PiiWiiRemoteAgent3";
    internal const string ShowEventName = @"Local\PiiWiiRemoteAgent3Show";
    internal const string QuitEventName = @"Local\PiiWiiRemoteAgent3Quit";

    [STAThread]
    static void Main(string[] args)
    {
        if (args.Any(a => a.Equals("--shutdown", StringComparison.OrdinalIgnoreCase))) { Signal(QuitEventName); return; }
        using var mutex = new Mutex(true, MutexName, out var first);
        if (!first) { Signal(ShowEventName); return; }

        ApplicationConfiguration.Initialize();
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => CrashLog.Write("UI", e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => CrashLog.Write("Domain", e.ExceptionObject as Exception);
        using var context = new AgentContext(args.Any(a => a.Equals("--background", StringComparison.OrdinalIgnoreCase)));
        Application.Run(context);
    }

    static void Signal(string name) { try { using var ev = EventWaitHandle.OpenExisting(name); ev.Set(); } catch { } }
}

internal sealed class AgentContext : ApplicationContext
{
    readonly MainForm form;
    readonly NotifyIcon tray;
    readonly RemoteCore core;
    readonly EventWaitHandle showEvent = new(false, EventResetMode.AutoReset, Program.ShowEventName);
    readonly EventWaitHandle quitEvent = new(false, EventResetMode.AutoReset, Program.QuitEventName);
    volatile bool exiting;

    internal AgentContext(bool background)
    {
        core = new RemoteCore();
        form = new MainForm(this, core);
        tray = new NotifyIcon { Icon = form.Icon, Text = Program.Product, Visible = true };
        var menu = new ContextMenuStrip();
        menu.Items.Add("Ouvrir", null, (_, _) => ShowWindow());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Quitter", null, (_, _) => ExitCleanly());
        tray.ContextMenuStrip = menu;
        tray.DoubleClick += (_, _) => ShowWindow();
        tray.MouseClick += (_, e) => { if (e.Button == MouseButtons.Left) ShowWindow(); };

        new Thread(() => WaitLoop(showEvent, ShowWindow)) { IsBackground = true, Name = "ShowEvent" }.Start();
        new Thread(() => WaitLoop(quitEvent, ExitCleanly)) { IsBackground = true, Name = "QuitEvent" }.Start();
        core.StatusChanged += () => { try { form.BeginInvoke(form.RefreshState); } catch { } };
        core.Start();
        if (background) form.Hide(); else ShowWindow();
    }

    void WaitLoop(EventWaitHandle ev, Action action)
    {
        while (!exiting) { try { ev.WaitOne(); } catch { break; } if (!exiting) try { form.BeginInvoke(action); } catch { } }
    }

    internal void ShowWindow()
    {
        if (exiting) return;
        if (!form.Visible) form.Show();
        if (form.WindowState == FormWindowState.Minimized) form.WindowState = FormWindowState.Normal;
        form.ShowInTaskbar = true; form.BringToFront(); form.Activate();
    }

    internal void HideWindow() { form.Hide(); form.ShowInTaskbar = false; }
    internal void RestartCore() => core.Restart();

    internal void ExitCleanly()
    {
        if (exiting) return;
        exiting = true;
        tray.Visible = false;
        core.Dispose();
        try { showEvent.Set(); quitEvent.Set(); } catch { }
        form.AllowRealClose = true; form.Close(); ExitThread();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) { tray.Dispose(); core.Dispose(); showEvent.Dispose(); quitEvent.Dispose(); form.Dispose(); }
        base.Dispose(disposing);
    }
}

internal sealed class MainForm : Form
{
    readonly AgentContext app;
    readonly RemoteCore core;
    readonly Label status = new();
    readonly Label ip = new();
    readonly Label code = new();
    readonly Label lastPhone = new();
    readonly Label engine = new();
    readonly CheckBox autostart = new();
    readonly System.Windows.Forms.Timer timer = new() { Interval = 1000 };
    internal bool AllowRealClose { get; set; }

    internal MainForm(AgentContext app, RemoteCore core)
    {
        this.app = app; this.core = core;
        Text = Program.Product; Width = 610; Height = 500; MinimumSize = new Size(610, 500); StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(18, 21, 27); ForeColor = Color.White; Font = new Font("Segoe UI", 10f);
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;
        FormClosing += (_, e) => { if (!AllowRealClose) { e.Cancel = true; app.HideWindow(); } };

        Controls.Add(new Label { Text = Program.Product, Font = new Font("Segoe UI Semibold", 20f), AutoSize = true, Location = new Point(28, 22) });
        Controls.Add(new Label { Text = "Passerelle locale pour PiiWii Remote", ForeColor = Color.FromArgb(150, 158, 172), AutoSize = true, Location = new Point(31, 61) });
        status.Font = new Font("Segoe UI Semibold", 11f); status.AutoSize = true; status.Location = new Point(31, 102); Controls.Add(status);

        var card = new Panel { BackColor = Color.FromArgb(28, 33, 42), Location = new Point(28, 138), Size = new Size(536, 194), Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        Controls.Add(card);
        AddRow(card, "IP locale", ip, 22);
        AddRow(card, "Port", new Label { Text = "UDP 45821" }, 62);
        AddRow(card, "Version", new Label { Text = Program.Version }, 102);
        AddRow(card, "Code d’appairage", code, 142, true);

        lastPhone.AutoSize = true; lastPhone.ForeColor = Color.FromArgb(182, 188, 199); lastPhone.Location = new Point(31, 352); Controls.Add(lastPhone);
        engine.AutoSize = true; engine.ForeColor = Color.FromArgb(182, 188, 199); engine.Location = new Point(31, 378); Controls.Add(engine);

        autostart.Text = "Démarrer automatiquement avec Windows"; autostart.AutoSize = true; autostart.Location = new Point(31, 414); Controls.Add(autostart);
        var restart = new Button { Text = "Redémarrer le moteur", Size = new Size(175, 34), Location = new Point(389, 405), FlatStyle = FlatStyle.Flat, BackColor = Color.FromArgb(42, 90, 150), ForeColor = Color.White };
        restart.FlatAppearance.BorderSize = 0; restart.Click += (_, _) => app.RestartCore(); Controls.Add(restart);
        autostart.CheckedChanged += (_, _) => { if (Visible) SetAutostart(autostart.Checked); };
        LoadAutostart(); RefreshState();
        timer.Tick += (_, _) => RefreshState(); timer.Start();
    }

    static void AddRow(Control parent, string name, Label value, int y, bool large = false)
    {
        parent.Controls.Add(new Label { Text = name, AutoSize = true, ForeColor = Color.FromArgb(166, 174, 188), Location = new Point(18, y) });
        value.AutoSize = true; value.ForeColor = Color.FromArgb(103, 180, 255); value.Font = new Font("Segoe UI Semibold", large ? 15f : 10.5f); value.Location = new Point(222, large ? y - 6 : y - 1); parent.Controls.Add(value);
    }

    internal void RefreshState()
    {
        if (IsDisposed) return;
        ip.Text = core.LocalIp;
        code.Text = core.PairingCode;
        lastPhone.Text = "Dernier téléphone : " + (string.IsNullOrWhiteSpace(core.LastPhone) ? "—" : core.LastPhone);
        engine.Text = core.IsListening ? "Moteur réseau : actif" : "Moteur réseau : arrêté";
        status.Text = core.IsListening ? "● Prêt — réseau local" : "● Hors ligne";
        status.ForeColor = core.IsListening ? Color.FromArgb(83, 214, 140) : Color.FromArgb(255, 116, 116);
    }

    void LoadAutostart()
    {
        try { using var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run"); autostart.Checked = k?.GetValue(Program.Product) != null; } catch { }
    }

    static void SetAutostart(bool enabled)
    {
        try { using var k = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run"); if (enabled) k.SetValue(Program.Product, $"\"{Application.ExecutablePath}\" --background"); else k.DeleteValue(Program.Product, false); } catch { }
    }
}

internal static class CrashLog
{
    internal static void Write(string area, Exception? ex)
    {
        try { var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PiiWii", "RemoteAgent"); Directory.CreateDirectory(dir); File.AppendAllText(Path.Combine(dir, "agent.log"), $"{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff} [{area}] {ex}\r\n"); } catch { }
    }
}
