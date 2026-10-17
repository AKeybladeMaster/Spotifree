using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace SpotifreeDesktopV2
{
    internal sealed class PlayerForm : Form
    {
        private const string Home = "https://open.spotify.com/";
        private readonly string dataRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SpotifreeDesktopV2");
        private readonly Panel webHost = new Panel();
        private readonly TextBox searchBox = new TextBox();
        private readonly Label status = new Label();
        private HiddenHostFilter hostFilter;
        private WebView2 browser;

        public PlayerForm()
        {
            Text = "Spotifree";
            Width = 1280;
            Height = 840;
            MinimumSize = new Size(760, 540);
            BackColor = Color.FromArgb(18, 18, 18);
            ForeColor = Color.White;
            KeyPreview = true;
            BuildUi();
            Directory.CreateDirectory(dataRoot);
            Shown += async (sender, args) => await InitializeBrowserAsync();
        }

        private Button MakeButton(string label, int width, EventHandler handler)
        {
            Button button = new Button();
            button.Text = label;
            button.Width = width;
            button.Height = 30;
            button.FlatStyle = FlatStyle.Flat;
            button.BackColor = Color.FromArgb(42, 42, 42);
            button.ForeColor = Color.White;
            button.Margin = new Padding(2);
            button.FlatAppearance.BorderColor = Color.FromArgb(70, 70, 70);
            button.Click += handler;
            return button;
        }

        private void BuildUi()
        {
            FlowLayoutPanel toolbar = new FlowLayoutPanel();
            toolbar.Dock = DockStyle.Top;
            toolbar.Height = 40;
            toolbar.WrapContents = false;
            toolbar.AutoScroll = true;
            toolbar.BackColor = Color.FromArgb(27, 27, 27);
            toolbar.Padding = new Padding(4);
            toolbar.Controls.Add(MakeButton("Back", 52, (s, e) => { if (browser != null && browser.CanGoBack) browser.GoBack(); }));
            toolbar.Controls.Add(MakeButton("Forward", 64, (s, e) => { if (browser != null && browser.CanGoForward) browser.GoForward(); }));
            toolbar.Controls.Add(MakeButton("Home", 54, (s, e) => Navigate(Home)));
            toolbar.Controls.Add(MakeButton("Reload", 58, (s, e) => { if (browser != null) browser.Reload(); }));
            searchBox.Width = 240;
            searchBox.Height = 28;
            searchBox.BackColor = Color.FromArgb(45, 45, 45);
            searchBox.ForeColor = Color.White;
            searchBox.BorderStyle = BorderStyle.FixedSingle;
            searchBox.Margin = new Padding(6, 5, 2, 2);
            searchBox.KeyDown += (s, e) => { if (e.KeyCode == Keys.Enter) { Search(); e.SuppressKeyPress = true; } };
            toolbar.Controls.Add(searchBox);
            toolbar.Controls.Add(MakeButton("Search", 62, (s, e) => Search()));
            toolbar.Controls.Add(MakeButton("Previous", 68, (s, e) => PlayerCommand("previous")));
            toolbar.Controls.Add(MakeButton("Play / Pause", 88, (s, e) => PlayerCommand("playpause")));
            toolbar.Controls.Add(MakeButton("Next", 54, (s, e) => PlayerCommand("next")));

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
        private async Task InitializeBrowserAsync()
        {
            try
            {
                string profilePath = Path.Combine(dataRoot, "BrowserProfile");
                Directory.CreateDirectory(profilePath);
                string rulesPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "blocked-hosts.txt");
                string settingsPath = Path.Combine(dataRoot, "settings.json");
                hostFilter = new HiddenHostFilter(rulesPath, settingsPath);
                CoreWebView2Environment environment = await CoreWebView2Environment.CreateAsync(null, profilePath);
                browser = new WebView2();
                browser.Dock = DockStyle.Fill;
                webHost.Controls.Add(browser);
                await browser.EnsureCoreWebView2Async(environment);
                browser.CoreWebView2.Settings.AreDevToolsEnabled = false;
                browser.CoreWebView2.Settings.IsStatusBarEnabled = false;
                browser.CoreWebView2.NavigationStarting += (sender, args) =>
                {
                    Uri target;
                    if (!Uri.TryCreate(args.Uri, UriKind.Absolute, out target) || !String.Equals(target.Scheme, "https", StringComparison.OrdinalIgnoreCase))
                    {
                        args.Cancel = true;
                        status.Text = "Secure HTTPS pages only";
                    }
                };
                browser.CoreWebView2.DocumentTitleChanged += (sender, args) => status.Text = browser.CoreWebView2.DocumentTitle;
                browser.CoreWebView2.NavigationCompleted += (sender, args) =>
                {
                    if (!args.IsSuccess) status.Text = "Page load failed: " + args.WebErrorStatus;
                };
                browser.CoreWebView2.NewWindowRequested += (sender, args) =>
                {
                    args.Handled = true;
                    Navigate(args.Uri);
                };
                if (hostFilter.Enabled)
                {
                    browser.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
                    browser.CoreWebView2.WebResourceRequested += OnResourceRequested;
                }
                Navigate(Home);
            }
            catch (Exception ex)
            {
                status.Text = "WebView2 could not start: " + ex.Message;
            }
        }

        private void OnResourceRequested(object sender, CoreWebView2WebResourceRequestedEventArgs args)
        {
            if (hostFilter == null || !hostFilter.ShouldBlock(args.Request.Uri)) return;
            args.Response = browser.CoreWebView2.Environment.CreateWebResourceResponse(
                new MemoryStream(new byte[0]), 204, "No Content", "Content-Type: text/plain");
        }

        private void Navigate(string address)
        {
            if (browser == null || browser.CoreWebView2 == null) return;
            Uri uri;
            if (!Uri.TryCreate(address, UriKind.Absolute, out uri) || !String.Equals(uri.Scheme, "https", StringComparison.OrdinalIgnoreCase))
            {
                status.Text = "Secure HTTPS pages only";
                return;
            }
            browser.CoreWebView2.Navigate(uri.AbsoluteUri);
        }

        private void Search()
        {
            string query = searchBox.Text.Trim();
            if (query.Length > 0) Navigate("https://open.spotify.com/search/" + Uri.EscapeDataString(query));
        }

        private async void PlayerCommand(string command)
        {
            if (browser == null || browser.CoreWebView2 == null) return;
            string script;
            if (command == "next") script = "(function(){var b=document.querySelector('[data-testid=\"control-button-skip-forward\"]');if(b)b.click();})()";
            else if (command == "previous") script = "(function(){var b=document.querySelector('[data-testid=\"control-button-skip-back\"]');if(b)b.click();})()";
            else script = "(function(){var b=document.querySelector('[data-testid=\"control-button-playpause\"]');if(b){b.click();return;}var m=document.querySelector('audio,video');if(m){if(m.paused)m.play();else m.pause();}})()";
            try { await browser.CoreWebView2.ExecuteScriptAsync(script); }
            catch (Exception ex) { status.Text = "Playback control failed: " + ex.Message; }
        }
        protected override bool ProcessCmdKey(ref Message message, Keys keyData)
        {
            Keys key = keyData & Keys.KeyCode;
            Keys modifiers = keyData & Keys.Modifiers;
            if (modifiers == Keys.Control && key == Keys.K) { searchBox.Focus(); searchBox.SelectAll(); return true; }
            if (modifiers == Keys.Control && key == Keys.H) { Navigate(Home); return true; }
            if (modifiers == Keys.Control && key == Keys.R) { if (browser != null) browser.Reload(); return true; }
            if (modifiers == Keys.Alt && key == Keys.Left) { if (browser != null && browser.CanGoBack) browser.GoBack(); return true; }
            if (modifiers == Keys.Alt && key == Keys.Right) { if (browser != null && browser.CanGoForward) browser.GoForward(); return true; }
            if (modifiers == (Keys.Control | Keys.Shift) && key == Keys.Space) { PlayerCommand("playpause"); return true; }
            if (modifiers == (Keys.Control | Keys.Shift) && key == Keys.Right) { PlayerCommand("next"); return true; }
            if (modifiers == (Keys.Control | Keys.Shift) && key == Keys.Left) { PlayerCommand("previous"); return true; }
            return base.ProcessCmdKey(ref message, keyData);
        }

        protected override void WndProc(ref Message message)
        {
            const int WM_APPCOMMAND = 0x319;
            if (message.Msg == WM_APPCOMMAND)
            {
                int command = ((int)message.LParam.ToInt64() >> 16) & 0x7fff;
                if (command == 14) { PlayerCommand("playpause"); return; }
                if (command == 11) { PlayerCommand("next"); return; }
                if (command == 12) { PlayerCommand("previous"); return; }
            }
            base.WndProc(ref message);
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (browser != null) { browser.Dispose(); browser = null; }
            base.OnFormClosed(e);
        }
    }
}