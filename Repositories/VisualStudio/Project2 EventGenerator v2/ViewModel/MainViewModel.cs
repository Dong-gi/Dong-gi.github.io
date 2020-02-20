using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Windows.Controls;
using System.Windows.Input;
using EventGenerator.Utility;
using MoreLinq;
using System.IO;
using EventGenerator.Model;
using static EventGenerator.Model.Constants;
using System.Threading.Tasks;
using EventGenerator.Service;
using System.Windows;

namespace EventGenerator.ViewModel
{
    class MainViewModel : BaseViewModel
    {
        #region 프로퍼티
        public string TextParam1
        {
            get => Get<string>(nameof(TextParam1), "").Copy();
            set => Set<string>(nameof(TextParam1), value);
        }
        public string TextParam2
        {
            get => Get<string>(nameof(TextParam2), "").Copy();
            set => Set<string>(nameof(TextParam2), value);
        }
        public string IntParam1
        {
            get => Get<string>(nameof(IntParam1), "").Copy();
            set => Set<string>(nameof(IntParam1), value);
        }
        public string IntParam2
        {
            get => Get<string>(nameof(IntParam2), "").Copy();
            set => Set<string>(nameof(IntParam2), value);
        }
        public string DateParam1
        {
            get => Get<string>(nameof(DateParam1), "").Copy();
            set => Set<string>(nameof(DateParam1), value);
        }
        public string DateParam2
        {
            get => Get<string>(nameof(DateParam2), "").Copy();
            set => Set<string>(nameof(DateParam2), value);
        }
        public string LogText
        {
            get => Get<string>(nameof(LogText), "");
            set => Set<string>(nameof(LogText), value);
        }
        public bool IsAddCurrent
        {
            get => Get<bool>(nameof(IsAddCurrent), true);
            set => Set<bool>(nameof(IsAddCurrent), value);
        }

        public int LastTabIndex
        {
            get => Get<int>(nameof(LastTabIndex), -1);
            private set => Set<int>(nameof(LastTabIndex), value);
        }
        public bool IsGame1Tab
        {
            get => Get<bool>(nameof(IsGame1Tab), true);
            private set => Set<bool>(nameof(IsGame1Tab), value);
        }
        public bool IsGame2Tab
        {
            get => Get<bool>(nameof(IsGame2Tab), false);
            private set => Set<bool>(nameof(IsGame2Tab), value);
        }
        public DBServer[] DBServers
        {
            get => Get<DBServer[]>(nameof(DBServers), new DBServer[] { });
            private set => Set<DBServer[]>(nameof(DBServers), value);
        }
        public DBServer CurrentDBServer
        {
            get => Get<DBServer>(nameof(CurrentDBServer), DBServer.Game1Now);
            set => Set<DBServer>(nameof(CurrentDBServer), value);
        }

        public Game1ViewModel Game1
        {
            get => Get<Game1ViewModel>(nameof(Game1), new Game1ViewModel { Main = this });
        }
        public Game2ViewModel Game2
        {
            get => Get<Game2ViewModel>(nameof(Game2), new Game2ViewModel { Main = this });
        }
        #endregion

        public MainViewModel()
        {
            Commands[TAB_CHANGED_COMMAND] = new CustomCommand(TabChanged, false);
            Commands[DB_SERVER_CHANGED_COMMAND] = new CustomCommand(DBServerChanged);
            Commands[UPDATE_COMMAND] = new CustomCommand(UpdateService.Update, false);
            Commands[OPEN_EXECUTING_FORDER_COMMAND] = new CustomCommand(FileUtility.OpenExecutingFolder);

            TabChanged(0);
            InitialLog();
        }

        #region 파일 드롭
        public void FileDroped(string[] filePaths)
        {
            if (filePaths == null || !filePaths.Any()) return;

            if (IsGame2Tab)
            {
                //Task.Factory.StartNew(() => Game2.FileDroped(filePaths));
                return;
            }

            Task.Factory.StartNew(() =>
            {
                var imgPaths = filePaths.Where(x => FileUtility.ImageExts.Find(ext => x.EndsWith(ext, StringComparison.OrdinalIgnoreCase)) != null);
                if (imgPaths.Count() < 1) return;

                imgPaths.ForEach(filePath => FileUtility.Download(filePath));
                SingleIcon.Toast("완료", "이미지 처리");
            });
            Task.Factory.StartNew(() => FileUtility.ProcessCsvFiles(filePaths));
            Task.Factory.StartNew(() => FileUtility.ProcessAcbFiles(filePaths));
        }
        #endregion

        #region 메인 탭 관련
        private void TabChanged(object tabControl)
        {
            if (LastTabIndex == (tabControl as TabControl).SelectedIndex)
                return;
            TabChanged((tabControl as TabControl).SelectedIndex);
        }
        private void TabChanged(int index)
        {
            LastTabIndex = index;
            IsGame1Tab = LastTabIndex == 0;
            IsGame2Tab = LastTabIndex == 1;

            if (IsGame1Tab)
            {
                DBServers = DBServer.Game1DBServers;
                SingleIcon.GetIcon().Icon = new System.Drawing.Icon(System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/통합 이벤트 생성기;component/Resources/yellow.ico")).Stream);
            }
            else if (IsGame2Tab)
            {
                DBServers = DBServer.Game2DBServers;
                SingleIcon.GetIcon().Icon = new System.Drawing.Icon(System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/통합 이벤트 생성기;component/Resources/blue.ico")).Stream);
            }
            else
            {
                DBServers = CollectionUtility.Concat(DBServer.Game1DBServers, DBServer.Game2DBServers).ToArray();
            }
            CurrentDBServer = DBServers[0];
        }
        #endregion

        #region 로그 관련
        public void Log(string message) => LogText += $"{DateTime.Now.ToLongTimeString()} │ {message}\n";
        public void WriteEventHandler(object _, string message) => Log(message);
        private void DBServerChanged()
        {
            if (CurrentDBServer != null)
                Log($"DB 변경 : → {CurrentDBServer}");
        }
        private void InitialLog()
        {
            try
            {
                Log(File.ReadAllText($"{System.AppDomain.CurrentDomain.BaseDirectory}/LICENSE.md"));
            }
            catch { }
        }
        #endregion
    }
}
