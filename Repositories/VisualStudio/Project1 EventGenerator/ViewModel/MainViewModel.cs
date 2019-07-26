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
        public static MainViewModel Current { get; private set; }

        public TaskbarIcon TaskbarIcon { get; } = new TaskbarIcon();
        public Dictionary<string, ICommand> Commands { get; private set; } = new Dictionary<string, ICommand>();
        public MainWindow MainWindow { get; private set; }
        public Game1ViewModel Game1 { get; private set; }
        public Game2ViewModel Game2 { get; private set; }

        #region 입력 요소
        private string _textParam1 = "";
        public string TextParam1
        {
            get => new string(_textParam1.ToCharArray());
            set
            {
                _textParam1 = value;
                RaisePropertyChanged("TextParam1");
            }
        }
        private string _textParam2 = "";
        public string TextParam2
        {
            get => new string(_textParam2.ToCharArray());
            set
            {
                _textParam2 = value;
                RaisePropertyChanged("TextParam2");
            }
        }
        private string _intParam1 = "";
        public string IntParam1
        {
            get => new string(_intParam1.ToCharArray());
            set
            {
                _intParam1 = value;
                RaisePropertyChanged("IntParam1");
            }
        }
        private string _intParam2 = "";
        public string IntParam2
        {
            get => new string(_intParam2.ToCharArray());
            set
            {
                _intParam2 = value;
                RaisePropertyChanged("IntParam2");
            }
        }
        private string _dateParam1 = "";
        public string DateParam1
        {
            get => new string(_dateParam1.ToCharArray());
            set
            {
                _dateParam1 = value;
                RaisePropertyChanged("DateParam1");
            }
        }
        private string _dateParam2 = "";
        public string DateParam2
        {
            get => new string(_dateParam2.ToCharArray());
            set
            {
                _dateParam2 = value;
                RaisePropertyChanged("DateParam2");
            }
        }
        #endregion

        public MainViewModel()
        {
            Current = this;
            Game1 = new Game1ViewModel();
            Game2 = new Game2ViewModel();

            DBServers = Game1.DBServers;
            CurrentDBServer = DBServers[0];

            Commands[TAB_CHANGED_COMMAND] = new CustomCommand(TabChanged, isBackground: false);
            Commands[LOG_CHANGED_COMMAND] = new CustomCommand(LogChanged, isBackground: false);
            Commands[DB_SERVER_CHANGED_COMMAND] = new CustomCommand(DBSererChanged, isBackground: false);
            Commands[UPDATE_COMMAND] = new CustomCommand(UpdateService.Update);
            Commands[OPEN_EXECUTING_FORDER_COMMAND] = new CustomCommand(FileService.OpenExecutingFolder);

            RedirectConsoleWrite();
            Console.Write(File.ReadAllText("LICENSE.md"));
            Console.Write($"실행 위치 : {System.AppDomain.CurrentDomain.BaseDirectory}");
        }

        #region INotifyPropertyChanged 구현 관련
        public event PropertyChangedEventHandler PropertyChanged;
        public void RaisePropertyChanged(string propertyName) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        #endregion

        #region 파일 드롭 관련
        public void FileDroped(string[] filePaths)
        {
            Log(string.Join("\n", filePaths));

            if (filePaths == null || filePaths.Count() == 0) return;

            if (IsGame2Tab)
            {
                Task.Factory.StartNew(() => Game2.FileDroped(filePaths));
                return;
            }

            #region 이미지 처리
            Task.Factory.StartNew(() =>
            {
                var imageExts = new List<string>(new string[] { ".jpg", ".png", ".gif", ".bmp", ".jpeg" });
                var imgPaths = filePaths.Where(x => imageExts.Find(ext => x.EndsWith(ext, StringComparison.OrdinalIgnoreCase)) != null);
                if (imgPaths.Count() < 1) return;

                imgPaths.ForEach(filePath => FileService.Download(filePath));
                Toast("완료", "이미지 처리");
            });
            #endregion

            #region CSV 처리
            Task.Factory.StartNew(() =>
            {
                if (FileService.ProcessCsvFiles(filePaths))
                    Toast("완료", "CSV 처리");
            });
            #endregion

            #region ACB 처리
            Task.Factory.StartNew(() =>
            {
                if (FileService.ProcessAcbFiles(filePaths))
                    Toast("완료", "ACB 처리");
            });
            #endregion
        }
        #endregion

        #region 메인 탭 관련
        public static int CurrentTabIndex { get; private set; } = 0;
        public static bool IsGame1Tab { get; private set; } = true;
        public static bool IsGame2Tab { get; private set; }
        private void TabChanged(object tabControl)
        {
            if (CurrentTabIndex == (tabControl as TabControl).SelectedIndex)
                return;
            Log("탭 변경 " + CurrentTabIndex + " → " + (tabControl as TabControl).SelectedIndex);

            CurrentTabIndex = (tabControl as TabControl).SelectedIndex;
            IsGame1Tab = CurrentTabIndex == 0;
            IsGame2Tab = CurrentTabIndex == 1;

            if (IsGame1Tab)
                DBServers = Game1.DBServers;
            else if (IsGame2Tab)
                DBServers = Game2.DBServers;
            RaisePropertyChanged("DBServers");
            CurrentDBServer = DBServers[0];
            RaisePropertyChanged("CurrentDBServer");
        }
        #endregion

        #region DB 서버 목록 관련
        public Model.DBServer[] DBServers { get; set; }
        public Model.DBServer CurrentDBServer { get; set; }
        private void DBSererChanged() => Log("DB 변경 : → " + CurrentDBServer);
        #endregion

        #region 로그 관련
        public string LogText { get; set; }
        private static void RedirectConsoleWrite() => Utility.ConsoleRedirectWriter.WRITER.OnWrite += (message) => Log(message);
        private static void Log(string log)
        {
            Current.LogText += $"{DateTime.Now.ToLongTimeString()} │ {log}\n";
            Current.RaisePropertyChanged("LogText");
        }
        private void LogChanged(object o) => (o as System.Windows.Controls.TextBox).ScrollToEnd();

        public static async void Toast(string title, string msg)
        {
            Log($"Toast │ {title} │ {msg}");
            
            if (IsGame1Tab)
                Current.TaskbarIcon.Icon = new System.Drawing.Icon(System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/Resources/yellow.ico")).Stream);
            else if (IsGame2Tab)
                Current.TaskbarIcon.Icon = new System.Drawing.Icon(System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/Resources/blue.ico")).Stream);
            await ActionUtility.UI(() => Current.TaskbarIcon.ShowBalloonTip(title, msg, Current.TaskbarIcon.Icon));
        }
        #endregion
    }
}
