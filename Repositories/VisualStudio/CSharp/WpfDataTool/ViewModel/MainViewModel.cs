using System;
using System.Linq;
using MoreLinq;

namespace WpfDataTool.ViewModel
{
    class MainViewModel : BaseViewModel
    {
        #region 프로퍼티
        public string LogText
        {
            get => Get<string>(nameof(LogText), "파일들을 드롭해보세요\n");
            set => Set<string>(nameof(LogText), value);
        }
        #endregion

        public MainViewModel()
        {
        }

        #region 파일 드롭
        public void FileDroped(string[] filePaths)
        {
            if (filePaths == null || !filePaths.Any()) return;
            filePaths.ForEach(x => Console.WriteLine(x));
        }
        #endregion

        #region 로그 관련
        public void Log(string message) =>
            LogText += string.IsNullOrWhiteSpace(message) ? $"{message}" : $"{DateTime.Now.ToLongTimeString()} │ {message}";
        public void WriteEventHandler(object _, string message) => Log(message);
        #endregion
    }
}
