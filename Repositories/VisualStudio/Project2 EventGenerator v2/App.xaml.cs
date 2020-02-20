using Hardcodet.Wpf.TaskbarNotification;
using System;
using System.Windows;

namespace EventGenerator
{
    /// <summary>
    /// App.xaml에 대한 상호 작용 논리
    /// </summary>
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            System.Globalization.CultureInfo c = new System.Globalization.CultureInfo("ko-KR");
            c.DateTimeFormat.FullDateTimePattern = "yyyy-MM-dd HH:mm:ss";
            c.DateTimeFormat.LongDatePattern = "yyyy-MM-dd";
            c.DateTimeFormat.LongTimePattern = "HH:mm:ss";
            System.Threading.Thread.CurrentThread.CurrentCulture = c;

            base.OnStartup(e);
            SingleIcon.Instance((TaskbarIcon)FindResource("Icon"));
        }

        protected override void OnExit(ExitEventArgs e)
        {
            Utility.NaiveHttpServer.CloseAll();
            SingleIcon.GetIcon().Dispose();
            Utility.MSExcel.Workbooks.Close();
            Utility.MSExcel.App.Quit();
            base.OnExit(e);
        }
    }

    public sealed class SingleIcon
    {
        public TaskbarIcon Icon { get; private set; }
        private static readonly Lazy<SingleIcon> lazy = new Lazy<SingleIcon>(() => new SingleIcon());

        private SingleIcon() { }
        public static SingleIcon Instance(TaskbarIcon icon)
        {
            lazy.Value.Icon = icon;
            return lazy.Value;
        }
        public static TaskbarIcon GetIcon() => lazy.Value.Icon;
        public static async void Toast(string title, string msg)
        {
            Console.WriteLine($"Toast │ {title} │ {msg}");
            if (System.AppDomain.CurrentDomain.BaseDirectory.Contains("Visual Studio"))
                return;
            await Utility.ActionUtility.UI(() => GetIcon().ShowBalloonTip(title, msg, GetIcon().Icon));
        }
    }
}
