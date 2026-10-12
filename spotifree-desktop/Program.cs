using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace SpotifreeDesktop
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new PlayerWindow());
        }
    }

    public sealed class PlayerWindow : Form
    {
        private const string Home = "https://open.spotify.com/";
        private readonly string dataRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SpotifreeDesktop");
        private readonly ComboBox profiles = new ComboBox();
        private readonly TextBox search = new TextBox();
        private readonly Label status = new Label();
        private readonly Panel webHost = new Panel();
        private readonly CheckBox adBlock = new CheckBox();
        private readonly HashSet<string> blockedHosts = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        private WebView2 browser;
        private string activeProfile = "Default";

        public PlayerWindow()
        {
            Text = "Spotifree";
            Width = 1280;
            Height = 840;
            MinimumSize = new Size(750, 500);
            BackColor = Color.FromArgb(18, 18, 18);
            ForeColor = Color.White;
            KeyPreview = true;
            BuildUi();
            Directory.CreateDirectory(dataRoot);
            LoadBlockList();
            LoadProfiles();
            Shown += async (sender, args) => await OpenProfile(activeProfile);
        }

        private Button MakeButton(string text, int width, EventHandler handler)
        {
            var button = new Button { Text = text, Width = width, Height = 30, FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(42, 42, 42), ForeColor = Color.White, Margin = new Padding(2) };
            button.FlatAppearance.BorderColor = Color.FromArgb(70, 70, 70);
            button.Click += handler;
            return button;
        }

        private void BuildUi()
        {
            var toolbar = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 40, WrapContents = false, AutoScroll = true,
                BackColor = Color.FromArgb(27, 27, 27), Padding = new Padding(4) };
            toolbar.Controls.Add(MakeButton("◀", 36, (s, e) => { if (browser != null && browser.CanGoBack) browser.GoBack(); }));
            toolbar.Controls.Add(MakeButton("▶", 36, (s, e) => { if (browser != null && browser.CanGoForward) browser.GoForward(); }));
            toolbar.Controls.Add(MakeButton("⌂", 36, (s, e) => Navigate(Home)));
            toolbar.Controls.Add(MakeButton("⟳", 36, (s, e) => { if (browser != null) browser.Reload(); }));
            search.Width = 260;
            search.Height = 28;
            search.BackColor = Color.FromArgb(45, 45, 45);
            search.ForeColor = Color.White;
            search.BorderStyle = BorderStyle.FixedSingle;
            search.Margin = new Padding(6, 5, 2, 2);
            search.KeyDown += (s, e) => { if (e.KeyCode == Keys.Enter) { Search(); e.SuppressKeyPress = true; } };
            toolbar.Controls.Add(search);
            toolbar.Controls.Add(MakeButton("Search", 60, (s, e) => Search()));
            toolbar.Controls.Add(MakeButton("⏮", 42, (s, e) => PlayerCommand("previous")));
            toolbar.Controls.Add(MakeButton("⏯", 42, (s, e) => PlayerCommand("playpause")));
            toolbar.Controls.Add(MakeButton("⏭", 42, (s, e) => PlayerCommand("next")));
            profiles.DropDownStyle = ComboBoxStyle.DropDownList;
            profiles.Width = 120;
            profiles.Margin = new Padding(8, 5, 2, 2);
            profiles.SelectedIndexChanged += async (s, e) => {
                if (profiles.SelectedItem != null && activeProfile != profiles.SelectedItem.ToString())
                    await OpenProfile(profiles.SelectedItem.ToString());
            };
            toolbar.Controls.Add(profiles);
            toolbar.Controls.Add(MakeButton("+ Profile", 80, (s, e) => NewProfile()));
            toolbar.Controls.Add(MakeButton("Delete", 55, (s, e) => DeleteProfile()));
            adBlock.Text = "Block ads";
            adBlock.Width = 90;
            adBlock.ForeColor = Color.White;
            adBlock.Margin = new Padding(8, 9, 0, 0);
            adBlock.Checked = true;
            toolbar.Controls.Add(adBlock);
            status.Dock = DockStyle.Bottom;
            status.Height = 24;
            status.TextAlign = ContentAlignment.MiddleLeft;
            status.Padding = new Padding(8, 0, 0, 0);
            status.Text = "Ready";
            webHost.Dock = DockStyle.Fill;
            Controls.Add(webHost);
            Controls.Add(status);
            Controls.Add(toolbar);
        }

        private void LoadProfiles()
        {
            string file = Path.Combine(dataRoot, "profiles.txt");
            var names = File.Exists(file) ? File.ReadAllLines(file).Where(ValidProfile).Distinct(StringComparer.OrdinalIgnoreCase).ToList() : new List<string>();
            if (names.Count == 0) names.Add("Default");
            profiles.Items.Clear();
            foreach (var name in names) profiles.Items.Add(name);
            string selected = Path.Combine(dataRoot, "active.txt");
            string saved = File.Exists(selected) ? File.ReadAllText(selected).Trim() : "Default";
            activeProfile = names.FirstOrDefault(x => String.Equals(x, saved, StringComparison.OrdinalIgnoreCase)) ?? names[0];
            profiles.SelectedItem = activeProfile;
            SaveProfiles();
        }

        private void SaveProfiles()
        {
            File.WriteAllLines(Path.Combine(dataRoot, "profiles.txt"), profiles.Items.Cast<string>().ToArray());
            File.WriteAllText(Path.Combine(dataRoot, "active.txt"), activeProfile);
        }

        private static bool ValidProfile(string value)
        {
            return !String.IsNullOrWhiteSpace(value) && value.Length <= 40 &&
                value.All(c => Char.IsLetterOrDigit(c) || c == ' ' || c == '-' || c == '_');
        }

        private void NewProfile()
        {
            string name = Microsoft.VisualBasic.Interaction.InputBox("Name this isolated Spotify browser profile:", "New profile", "Profile " + (profiles.Items.Count + 1)).Trim();
            if (name.Length == 0) return;
            if (!ValidProfile(name) || profiles.Items.Cast<string>().Any(x => String.Equals(x, name, StringComparison.OrdinalIgnoreCase)))
            {
                MessageBox.Show("Use a unique name of up to 40 letters, digits, spaces, hyphens or underscores.");
                return;
            }
            profiles.Items.Add(name);
            SaveProfiles();
            profiles.SelectedItem = name;
        }

        private void DeleteProfile()
        {
            if (profiles.Items.Count <= 1) { MessageBox.Show("Keep at least one profile."); return; }
            string name = activeProfile;
            if (MessageBox.Show("Delete the '" + name + "' browser profile and its saved login?", "Delete profile", MessageBoxButtons.YesNo) != DialogResult.Yes) return;
            // WebView2 must be disposed before the profile directory is removed.
            if (browser != null) { browser.Dispose(); browser = null; }
            profiles.Items.Remove(name);
            activeProfile = profiles.Items[0].ToString();
            SaveProfiles();
            string folder = Path.Combine(dataRoot, "Profiles", name);
            try { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
            catch (IOException) { status.Text = "Profile removed; restart to clear its browser files."; }
            profiles.SelectedItem = activeProfile;
        }

        private async System.Threading.Tasks.Task OpenProfile(string name)
        {
            if (!ValidProfile(name)) return;
            activeProfile = name;
            SaveProfiles();
            if (browser != null) { browser.Dispose(); browser = null; }
            webHost.Controls.Clear();
            status.Text = "Opening " + name + "…";
            try
            {
                string folder = Path.Combine(dataRoot, "Profiles", name);
                Directory.CreateDirectory(folder);
                var environment = await CoreWebView2Environment.CreateAsync(null, folder);
                browser = new WebView2 { Dock = DockStyle.Fill };
                webHost.Controls.Add(browser);
                await browser.EnsureCoreWebView2Async(environment);
                browser.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
                browser.CoreWebView2.WebResourceRequested += OnResourceRequested;
                browser.CoreWebView2.DocumentTitleChanged += (s, e) => status.Text = browser.CoreWebView2.DocumentTitle;
                browser.CoreWebView2.NavigationCompleted += (s, e) => { if (!e.IsSuccess) status.Text = "Page load failed: " + e.WebErrorStatus; };
                browser.CoreWebView2.NewWindowRequested += (s, e) => {
                    e.Handled = true;
                    Uri target;
                    if (Uri.TryCreate(e.Uri, UriKind.Absolute, out target) && target.Scheme == "https") Navigate(e.Uri);
                };
                browser.Source = new Uri(Home);
            }
            catch (Exception ex) { status.Text = "WebView2 failed: " + ex.Message; }
        }

        private void LoadBlockList()
        {
            string file = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "blocked-hosts.txt");
            if (!File.Exists(file)) return;
            foreach (string line in File.ReadAllLines(file))
            {
                string host = line.Split('#')[0].Trim().ToLowerInvariant();
                if (host.Length > 0 && !host.Contains("/") && !host.Contains(" ")) blockedHosts.Add(host);
            }
        }

        private void OnResourceRequested(object sender, CoreWebView2WebResourceRequestedEventArgs e)
        {
            if (!adBlock.Checked) return;
            Uri uri;
            if (!Uri.TryCreate(e.Request.Uri, UriKind.Absolute, out uri)) return;
            string host = uri.Host;
            if (blockedHosts.Any(rule => host.Equals(rule, StringComparison.OrdinalIgnoreCase) || host.EndsWith("." + rule, StringComparison.OrdinalIgnoreCase)))
                e.Response = browser.CoreWebView2.Environment.CreateWebResourceResponse(new MemoryStream(new byte[0]), 204, "No Content", "Content-Type: text/plain");
        }

        private void Navigate(string url) { if (browser != null && browser.CoreWebView2 != null) browser.CoreWebView2.Navigate(url); }
        private void Search()
        {
            string query = search.Text.Trim();
            if (query.Length > 0) Navigate("https://open.spotify.com/search/" + Uri.EscapeDataString(query));
        }

        private async void PlayerCommand(string command)
        {
            if (browser == null || browser.CoreWebView2 == null) return;
            string testId = command == "next" ? "control-button-skip-forward" : command == "previous" ? "control-button-skip-back" : "control-button-playpause";
            string script = "(function(){var b=document.querySelector('[data-testid=\"" + testId + "\"]');if(b){b.click();return true;}var m=document.querySelector('audio,video');if(!m)return false;if('" + command + "'==='playpause'){if(m.paused)m.play();else m.pause();return true;}return false;})()";
            try { await browser.CoreWebView2.ExecuteScriptAsync(script); }
            catch (Exception ex) { status.Text = "Control failed: " + ex.Message; }
        }

        protected override void WndProc(ref Message message)
        {
            const int WM_APPCOMMAND = 0x319;
            if (message.Msg == WM_APPCOMMAND)
            {
                int action = ((int)message.LParam.ToInt64() >> 16) & 0x7fff;
                if (action == 14) { PlayerCommand("playpause"); return; }
                if (action == 11) { PlayerCommand("next"); return; }
                if (action == 12) { PlayerCommand("previous"); return; }
            }
            base.WndProc(ref message);
        }
    }
}
