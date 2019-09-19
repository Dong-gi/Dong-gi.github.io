using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Text;
using System.Windows.Controls;
using System.Windows.Input;
using EventGenerator.Utility;
using System.Text.RegularExpressions;
using MoreLinq;
using System.IO;
using System.Threading;
using System.Diagnostics;
using EventGenerator.Dao;
using EventGenerator.Model;
using System.Reflection;
using Newtonsoft.Json;
using EventGenerator.Model.CustomAttribute;
using static EventGenerator.Model.Constants;
using System.Threading.Tasks;
using EventGenerator.Service;
using Hardcodet.Wpf.TaskbarNotification;

namespace EventGenerator.ViewModel
{
    class MainViewModel : INotifyPropertyChanged
    {
        public Dictionary<string, ICommand> Commands { get; private set; } = new Dictionary<string, ICommand>();
        private readonly Dictionary<string, object> Properties = new Dictionary<string, object>();
        public T Get<T>(string propertyName) => (T)Properties[propertyName];
        public T Get<T>(string propertyName, T defaultValue)
        {
            try
            {
                return Get<T>(propertyName);
            }
            catch (Exception)
            {
                Set<T>(propertyName, defaultValue);
                return defaultValue;
            }
        }
        private T Set<T>(string propertyName, T value, bool noRaise = false)
        {
            Properties[propertyName] = value;
            if (!noRaise)
                RaisePropertyChanged(propertyName);
            return value;
        }

        #region ViewModel 프로퍼티
        public static MainViewModel Current { get; private set; }

        public Game1ViewModel Game1 { get; private set; } = new Game1ViewModel();
        public Game2ViewModel Game2 { get; private set; } = new Game2ViewModel();
        #endregion

        #region UI 입력 텍스트 프로퍼티
        public string TextParam1
        {
            get => Get<string>("TextParam1", "").Copy();
            set => Set<string>("TextParam1", value);
        }
        public string TextParam2
        {
            get => Get<string>("TextParam2", "").Copy();
            set => Set<string>("TextParam2", value);
        }
        public string IntParam1
        {
            get => Get<string>("IntParam1", "").Copy();
            set => Set<string>("IntParam1", value);
        }
        public string IntParam2
        {
            get => Get<string>("IntParam2", "").Copy();
            set => Set<string>("IntParam2", value);
        }
        public string DateParam1
        {
            get => Get<string>("DateParam1", "").Copy();
            set => Set<string>("DateParam1", value);
        }
        public string DateParam2
        {
            get => Get<string>("DateParam2", "").Copy();
            set => Set<string>("DateParam2", value);
        }
        #endregion

        public MainViewModel()
        {
            RedirectConsoleWrite();
            Current = this;
            
            DBServers = Game1.DBServers;
            CurrentDBServer = DBServers[0];

            Commands[TAB_CHANGED_COMMAND] = new CustomCommand(TabChanged, isBackground: false);
            Commands[LOG_CHANGED_COMMAND] = new CustomCommand(LogChanged, isBackground: false);
            Commands[DB_SERVER_CHANGED_COMMAND] = new CustomCommand(DBSererChanged, isBackground: false);
            Commands[UPDATE_COMMAND] = new CustomCommand(UpdateService.Update);
            Commands[OPEN_EXECUTING_FORDER_COMMAND] = new CustomCommand(FileService.OpenExecutingFolder);

            Console.Write(File.ReadAllText("./LICENSE.md"));
            Toast("실행 위치", System.AppDomain.CurrentDomain.BaseDirectory);
        }

        #region INotifyPropertyChanged 구현 관련
        public event PropertyChangedEventHandler PropertyChanged;
        public void RaisePropertyChanged(string propertyName) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        #endregion

        #region 파일 드롭 관련
        public void FileDroped(string[] filePaths)
        {
            Console.Write(string.Join("\n", filePaths));

            if (filePaths == null || filePaths.Count() == 0) return;

            if (IsGame2Tab)
            {
                Task.Factory.StartNew(() => Game2.FileDroped(filePaths));
                return;
            }

            Task.Factory.StartNew(() =>
            {
                var imgPaths = filePaths.Where(x => FileService.imageExts.Find(ext => x.EndsWith(ext, StringComparison.OrdinalIgnoreCase)) != null);
                if (imgPaths.Count() < 1) return;

                imgPaths.ForEach(filePath => FileService.Download(filePath));
                Toast("완료", "이미지 처리");
            });
            Task.Factory.StartNew(() => FileService.ProcessCsvFiles(filePaths));
            Task.Factory.StartNew(() => FileService.ProcessAcbFiles(filePaths));
        }
        #endregion

        #region 메인 탭 관련
        public static int LastTabIndex { get; private set; } = -1;
        public static bool IsGame1Tab { get; private set; } = true;
        public static bool IsGame2Tab { get; private set; }
        private void TabChanged(object tabControl)
        {
            if (LastTabIndex == (tabControl as TabControl).SelectedIndex)
                return;

            Console.Write($"탭 변경 {LastTabIndex} → {(tabControl as TabControl).SelectedIndex}");
            LastTabIndex = (tabControl as TabControl).SelectedIndex;
            IsGame1Tab = LastTabIndex == 0;
            IsGame2Tab = LastTabIndex == 1;

            if (IsGame1Tab)
                DBServers = Game1.DBServers;
            else if (IsGame2Tab)
                DBServers = Game2.DBServers;
            CurrentDBServer = DBServers[0];
        }
        #endregion

        #region DB 서버 목록 관련
        public DBServer[] DBServers
        {
            get => Get<DBServer[]>("DBServers", new DBServer[] { });
            set => Set<DBServer[]>("DBServers", value);
        }
        public DBServer CurrentDBServer
        {
            get => Get<DBServer>("CurrentDBServer", DBServer.GAME1_NOW);
            set => Set<DBServer>("CurrentDBServer", value);
        }

        private void DBSererChanged() => Console.Write("DB 변경 : → " + CurrentDBServer);
        #endregion

        #region 로그 관련
        public string LogText { get; set; }
        public TaskbarIcon TaskbarIcon { get; } = new TaskbarIcon();

        private static void RedirectConsoleWrite() => Utility.ConsoleRedirectWriter.WRITER.OnWrite += (message) => Log(message);
        private static void Log(string log)
        {
            Current.LogText += $"{DateTime.Now.ToLongTimeString()} │ {log}\n";
            Current.RaisePropertyChanged("LogText");
        }
        private void LogChanged(object o) => (o as System.Windows.Controls.TextBox).ScrollToEnd();

        public static async void Toast(string title, string msg)
        {
            Console.Write($"Toast │ {title} │ {msg}");
            
            await ActionUtility.UI(() =>
            {
                if (IsGame1Tab)
                    Current.TaskbarIcon.Icon = new System.Drawing.Icon(System.Windows.Application.GetResourceStream(new Uri((System.Windows.Application.Current.MainWindow.FindResource("yellowIcon") as dynamic).Source.Decoder.ToString())).Stream);
                else if (IsGame2Tab)
                    Current.TaskbarIcon.Icon = new System.Drawing.Icon(System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/통합 이벤트 생성기;component/Resources/blue.ico")).Stream);

                Current.TaskbarIcon.ShowBalloonTip(title, msg, Current.TaskbarIcon.Icon);
            });
        }
        #endregion
    }
}
