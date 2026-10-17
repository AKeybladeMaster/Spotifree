using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text.RegularExpressions;

namespace SpotifreeDesktop
{
    internal sealed class HiddenHostFilter
    {
        private readonly HashSet<string> domains = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        private readonly HashSet<string> addresses = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        public bool Enabled { get; private set; }

        public HiddenHostFilter(string rulesPath, string settingsPath)
        {
            try { Enabled = File.Exists(settingsPath) && Regex.IsMatch(File.ReadAllText(settingsPath), "\\\"adFilteringEnabled\\\"\\s*:\\s*true", RegexOptions.IgnoreCase); }
            catch (IOException) { Enabled = false; }
            if (!Enabled || !File.Exists(rulesPath)) return;
            foreach (string line in File.ReadAllLines(rulesPath)) AddRulesFromLine(line, domains, addresses);
        }

        public bool ShouldBlock(string requestUrl)
        {
            if (!Enabled) return false;
            Uri uri;
            if (!Uri.TryCreate(requestUrl, UriKind.Absolute, out uri)) return false;
            string host = NormalizeHost(uri.Host);
            IPAddress requestAddress;
            if (IPAddress.TryParse(host, out requestAddress))
                return addresses.Contains(requestAddress.ToString().ToLowerInvariant());
            foreach (string domain in domains)
                if (host.Equals(domain, StringComparison.OrdinalIgnoreCase) ||
                    host.EndsWith("." + domain, StringComparison.OrdinalIgnoreCase)) return true;
            return false;
        }

        internal static void AddRulesFromLine(string rawLine, HashSet<string> domainRules, HashSet<string> ipRules)
        {
            if (rawLine == null) return;
            int comment = rawLine.IndexOf('#');
            string line = (comment >= 0 ? rawLine.Substring(0, comment) : rawLine).Trim();
            if (line.Length == 0) return;
            string[] fields = line.Split((char[])null, StringSplitOptions.RemoveEmptyEntries);
            int firstTarget = fields.Length > 1 && IsIpAddress(NormalizeHost(fields[0])) ? 1 : 0;
            for (int i = firstTarget; i < fields.Length; i++)
            {
                string host = NormalizeHost(fields[i]);
                if (IsIpAddress(host))
                {
                    IPAddress address;
                    IPAddress.TryParse(host, out address);
                    ipRules.Add(address.ToString().ToLowerInvariant());
                }
                else if (Uri.CheckHostName(host) == UriHostNameType.Dns) domainRules.Add(host);
            }
        }

        private static string NormalizeHost(string value)
        {
            if (value == null) return String.Empty;
            string host = value.Trim();
            if (host.StartsWith("[", StringComparison.Ordinal) && host.EndsWith("]", StringComparison.Ordinal))
                host = host.Substring(1, host.Length - 2);
            return host.TrimEnd('.').ToLowerInvariant();
        }

        private static bool IsIpAddress(string host)
        {
            IPAddress address;
            return IPAddress.TryParse(host, out address);
        }
    }
}
