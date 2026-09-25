using System;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace PiiWiiTV;

internal static class Program
{
    private const string MutexName = @"Local\PiiWiiTV.Singleton.1";

    [STAThread]
    private static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        using var mutex = new Mutex(true, MutexName, out var firstInstance);
        var action = ParseAction(args);

        if (!firstInstance)
        {
            if (!string.IsNullOrWhiteSpace(action)) NativeBridge.SendActionToRunningInstance(action);
            return;
        }

        Application.Run(new MainForm(action));
    }

    private static string? ParseAction(string[] args)
    {
        foreach (var arg in args)
            if (arg.StartsWith("--action=", StringComparison.OrdinalIgnoreCase)) return arg.Substring("--action=".Length).Trim();
        return null;
    }
}
