using System;
using System.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;
namespace SpotifreeDesktopV2
{
    internal sealed class HiddenHostFilter
    {
        private readonly HashSet<string> hosts = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        public bool Enabled { get; private set; }
        public HiddenHostFilter(string rulesPath, string settingsPath)
        {
            try { Enabled = File.Exists(settingsPath) && Regex.IsMatch(File.ReadAllText(settingsPath), "\\\"adFilteringEnabled\\\"\\s*:\\s*true", RegexOptions.IgnoreCase); }
            catch (IOException) { Enabled = false; }
            if (!Enabled || !File.Exists(rulesPath)) return;
            foreach (string line in File.ReadAllLines(rulesPath))
            {
                string host = line.Split('#')[0].Trim();
                if (host.Length > 0 && host.IndexOf('/') < 0 && host.IndexOf(' ') < 0) hosts.Add(host);
            }
        }
        public bool ShouldBlock(string requestUrl)
        {
            if (!Enabled) return false;
            Uri uri;
            if (!Uri.TryCreate(requestUrl, UriKind.Absolute, out uri)) return false;
            foreach (string rule in hosts)
                if (uri.Host.Equals(rule, StringComparison.OrdinalIgnoreCase) || uri.Host.EndsWith("." + rule, StringComparison.OrdinalIgnoreCase)) return true;
            return false;
        }
    }
}