using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Globalization;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;

namespace MangaRenamerPro;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<FileRow> _rows = new();
    private string? _folder;
    private bool _loading;

    public MainWindow()
    {
        InitializeComponent();
        FilesGrid.ItemsSource = _rows;
        PaddingBox.SelectedIndex = 1;
        UpdateModeUi();
    }

    private void ChooseFolder_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new Microsoft.Win32.OpenFolderDialog
        {
            Title = "پوشه حاوی فایل‌های مانگا را انتخاب کنید",
            Multiselect = false
        };
        if (dialog.ShowDialog(this) != true) return;
        _folder = dialog.FolderName;
        FolderPathText.Text = _folder;
        LoadFiles();
    }

    private void LoadFiles()
    {
        if (string.IsNullOrWhiteSpace(_folder) || !Directory.Exists(_folder)) return;
        try
        {
            _loading = true;
            _rows.Clear();
            var option = IncludeSubfoldersCheck.IsChecked == true
                ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
            var paths = Directory.EnumerateFiles(_folder, "*", option).ToList();
            if (NaturalSortCheck.IsChecked == true)
                paths.Sort((a, b) => NaturalCompare(Path.GetFileName(a), Path.GetFileName(b)));
            else
                paths.Sort(StringComparer.CurrentCultureIgnoreCase);

            foreach (var path in paths)
            {
                var name = Path.GetFileName(path);
                _rows.Add(new FileRow(path, name, name));
            }
            UpdateCount();
            StatusText.Text = $"فهرست با موفقیت بارگذاری شد؛ { _rows.Count } فایل پیدا شد.";
        }
        catch (Exception ex)
        {
            MessageBox.Show($"خواندن پوشه با خطا مواجه شد:\n{ex.Message}", "خطا", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally { _loading = false; }
    }

    private static int NaturalCompare(string? left, string? right)
    {
        var a = Regex.Split(left ?? "", "(\\d+)");
        var b = Regex.Split(right ?? "", "(\\d+)");
        for (var i = 0; i < Math.Min(a.Length, b.Length); i++)
        {
            var na = long.TryParse(a[i], out var av);
            var nb = long.TryParse(b[i], out var bv);
            var cmp = na && nb ? av.CompareTo(bv) : StringComparer.CurrentCultureIgnoreCase.Compare(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return a.Length.CompareTo(b.Length);
    }

    private void Preview_Click(object sender, RoutedEventArgs e) => BuildPreview(_rows);

    private void BuildPreview(IEnumerable<FileRow> rows)
    {
        if (_rows.Count == 0) { StatusText.Text = "ابتدا یک پوشه انتخاب کنید."; return; }
        if (!int.TryParse(StartNumberBox.Text, out var start) || start < 0)
        {
            MessageBox.Show("عدد شروع باید یک عدد صحیح صفر یا بزرگ‌تر باشد.", "ورودی نامعتبر", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        var pad = Math.Clamp(PaddingBox.SelectedIndex + 1, 1, 4);
        var index = 0;
        foreach (var row in rows)
        {
            var ext = Path.GetExtension(row.OriginalName);
            var oldStem = Path.GetFileNameWithoutExtension(row.OriginalName);
            var number = (start + index).ToString().PadLeft(pad, '0');
            string stem;
            if (AutoModeRadio.IsChecked == true)
                stem = PrefixBox.Text + number;
            else
                stem = TemplateBox.Text
                    .Replace("{n}", number, StringComparison.OrdinalIgnoreCase)
                    .Replace("{old}", oldStem, StringComparison.OrdinalIgnoreCase)
                    .Replace("{chapter}", ExtractChapter(oldStem), StringComparison.OrdinalIgnoreCase);

            row.NewName = stem + ext;
            row.Status = "آماده بررسی";
            index++;
        }
        ValidateRows(rows);
        FilesGrid.Items.Refresh();
        StatusText.Text = "پیش‌نمایش ساخته شد. نام‌های قرمز/دارای هشدار را قبل از اجرا اصلاح کنید.";
    }

    private static string ExtractChapter(string name)
    {
        var match = Regex.Match(name, @"(?i)(?:chapter|chap|ch|episode|ep|第|c)[ ._ -]*0*(\\d+(?:[.]\\d+)?)");
        if (!match.Success)
        {
            var matches = Regex.Matches(name, @"\\d+(?:[.]\\d+)?");
            if (matches.Count > 0) match = matches[matches.Count - 1];
        }
        return match.Success ? match.Groups.Count > 1 ? match.Groups[1].Value : match.Value : "0";
    }

    private void ValidateRows(IEnumerable<FileRow> rows)
    {
        var list = rows.ToList();
        var movingPaths = list.Select(r => Path.GetFullPath(r.FullPath)).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var targets = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        foreach (var row in list)
        {
            row.Status = "آماده";
            if (string.IsNullOrWhiteSpace(row.NewName) || row.NewName.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0 ||
                row.NewName is "." or ".." || row.NewName.Contains(Path.DirectorySeparatorChar) || row.NewName.Contains(Path.AltDirectorySeparatorChar))
            {
                row.Status = "نام نامعتبر";
                continue;
            }
            var target = Path.GetFullPath(Path.Combine(Path.GetDirectoryName(row.FullPath)!, row.NewName));
            if (string.Equals(target, Path.GetFullPath(row.FullPath), StringComparison.Ordinal))
            {
                row.Status = "بدون تغییر";
                continue;
            }
            targets[target] = targets.GetValueOrDefault(target) + 1;
            if (File.Exists(target) && !movingPaths.Contains(target))
                row.Status = "فایل مقصد موجود است";
        }
        foreach (var row in list)
        {
            if (string.IsNullOrWhiteSpace(row.NewName)) continue;
            var target = Path.GetFullPath(Path.Combine(Path.GetDirectoryName(row.FullPath)!, row.NewName));
            if (targets.GetValueOrDefault(target) > 1) row.Status = "نام تکراری";
        }
    }

    private void RenameSelected_Click(object sender, RoutedEventArgs e)
    {
        var selected = FilesGrid.SelectedItems.Cast<FileRow>().ToList();
        if (selected.Count == 0)
        {
            MessageBox.Show("ابتدا یک یا چند ردیف را انتخاب کنید. می‌توانید نام جدید را مستقیماً در ستون دوم ویرایش کنید.", "انتخاب فایل", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        RenameRows(selected, "تغییر نام انتخابی");
    }

    private void RenameAll_Click(object sender, RoutedEventArgs e)
    {
        if (_rows.Count == 0) { StatusText.Text = "ابتدا یک پوشه انتخاب کنید."; return; }
        if (_rows.All(r => r.Status == "آماده بررسی" || r.Status == ""))
            BuildPreview(_rows);
        RenameRows(_rows.ToList(), "تغییر نام گروهی");
    }

    private void RenameRows(List<FileRow> rows, string title)
    {
        FilesGrid.CommitEdit(DataGridEditingUnit.Cell, true);
        FilesGrid.CommitEdit(DataGridEditingUnit.Row, true);
        ValidateRows(rows);
        var invalid = rows.Where(r => r.Status is "نام نامعتبر" or "فایل مقصد موجود است" or "نام تکراری").ToList();
        if (invalid.Count > 0)
        {
            MessageBox.Show($"{invalid.Count} مورد مشکل دارد. نام‌های تکراری، نامعتبر یا مقصدهای موجود را اصلاح کنید.", "بررسی نام‌ها", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        var changes = rows.Where(r => r.Status != "بدون تغییر").ToList();
        if (changes.Count == 0) { StatusText.Text = "هیچ تغییری برای اعمال وجود ندارد."; return; }
        var answer = MessageBox.Show($"آیا از تغییر نام {changes.Count} فایل مطمئن هستید؟\n\nاین عملیات نام فایل‌ها را روی دیسک تغییر می‌دهد.", title, MessageBoxButton.YesNo, MessageBoxImage.Question);
        if (answer != MessageBoxResult.Yes) return;

        var staged = new List<(FileRow Row, string Temp, string Target)>();
        var completed = new List<(FileRow Row, string Target)>();
        try
        {
            foreach (var row in changes)
            {
                var directory = Path.GetDirectoryName(row.FullPath)!;
                var temp = Path.Combine(directory, $".mangarenamer-{Guid.NewGuid():N}.tmp");
                File.Move(row.FullPath, temp);
                staged.Add((row, temp, Path.Combine(directory, row.NewName)));
            }
            foreach (var item in staged)
            {
                File.Move(item.Temp, item.Target);
                completed.Add((item.Row, item.Target));
            }
            foreach (var item in completed)
            {
                item.Row.FullPath = item.Target;
                item.Row.OriginalName = Path.GetFileName(item.Target);
                item.Row.NewName = item.Row.OriginalName;
                item.Row.Status = "انجام شد";
            }
            StatusText.Text = $"با موفقیت {completed.Count} فایل تغییر نام داده شد.";
            UpdateCount();
        }
        catch (Exception ex)
        {
            foreach (var item in completed.AsEnumerable().Reverse())
            {
                try { if (File.Exists(item.Target)) File.Move(item.Target, item.Row.FullPath); } catch { }
            }
            foreach (var item in staged.AsEnumerable().Reverse())
            {
                try { if (File.Exists(item.Temp) && !File.Exists(item.Row.FullPath)) File.Move(item.Temp, item.Row.FullPath); } catch { }
            }
            MessageBox.Show($"عملیات کامل نشد. تلاش شد فایل‌ها به نام قبلی برگردند.\n{ex.Message}", "خطا در تغییر نام", MessageBoxButton.OK, MessageBoxImage.Error);
            LoadFiles();
        }
    }

    private void Refresh_Click(object sender, RoutedEventArgs e) => LoadFiles();
    private void Clear_Click(object sender, RoutedEventArgs e)
    {
        _rows.Clear();
        UpdateCount();
        StatusText.Text = "فهرست پاک شد؛ فایل‌های روی دیسک تغییری نکردند.";
    }
    private void UpdateCount() => CountText.Text = $"{_rows.Count.ToString(CultureInfo.InvariantCulture)} فایل";
    private void IncludeSubfolders_Changed(object sender, RoutedEventArgs e)
    {
        if (!_loading && IsLoaded && _folder is not null) LoadFiles();
    }
    private void Mode_Changed(object sender, RoutedEventArgs e) => UpdateModeUi();
    private void UpdateModeUi()
    {
        if (TemplatePanel is not null)
            TemplatePanel.Visibility = TemplateModeRadio.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;
    }
    private void Settings_Changed(object sender, RoutedEventArgs e)
    {
        if (!_loading && IsLoaded && _rows.Count > 0) StatusText.Text = "تنظیمات تغییر کرد؛ برای ساخت پیش‌نمایش جدید کلیک کنید.";
    }
}

public sealed class FileRow : INotifyPropertyChanged
{
    private string _originalName;
    private string _newName;
    private string _status;
    public FileRow(string fullPath, string originalName, string newName)
    {
        FullPath = fullPath;
        _originalName = originalName;
        _newName = newName;
        _status = "در انتظار";
    }
    public string FullPath { get; set; }
    public string OriginalName { get => _originalName; set { _originalName = value; OnChanged(nameof(OriginalName)); } }
    public string NewName { get => _newName; set { _newName = value; OnChanged(nameof(NewName)); } }
    public string Status { get => _status; set { _status = value; OnChanged(nameof(Status)); } }
    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged(string name) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
