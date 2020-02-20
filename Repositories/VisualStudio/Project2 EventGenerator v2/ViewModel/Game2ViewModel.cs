using EventGenerator.Service;
using EventGenerator.Utility;
using MoreLinq;
using System;
using System.Diagnostics;
using System.Linq;

using static EventGenerator.Model.Constants;

namespace EventGenerator.ViewModel
{
    class Game2ViewModel : BaseViewModel
    {
        #region 프로퍼티
        public MainViewModel Main
        {
            get => Get<MainViewModel>(nameof(Main), null);
            set
            {
                if (Main == null)
                    Set<MainViewModel>(nameof(Main), value);
            }
        }
        #endregion

        public Game2ViewModel()
        {
            Commands[QUIT_EXCEL_COMMAND] = new CustomCommand(QuitExcelProcesses);
            Commands[SSH_EXAMPLE_COMMAND] = new CustomCommand(new SSHService().SSHExample);
            Commands[AWS_CLOUD_FRONT_INVALIDATION_COPY_COMMAND] = new CustomCommand(new AWSService().AWSCloudFrontInvalidationCopy);
        }


        #region INotifyPropertyChanged 구현
        public override void RaisePropertyChanged(string propertyName) => base.RaisePropertyChanged(Main, $"{nameof(Main.Game2)}");
        #endregion

        public bool ImageOptimize { get; set; } = true;
        public bool XlsxToXls { get; set; }
        public bool MergeSheets { get; set; }
        public bool CsvToXlsx { get; set; }
        public void FileDroped(string[] filePaths)
        {
            if (ImageOptimize)
                new ImageOptimizer(filePaths).Optimize();
            else if (XlsxToXls)
                FileUtility.XlsxToXls(filePaths.Where(filePath => filePath.EndsWith("xlsx")));
            else if (MergeSheets)
                FileUtility.MergeExcels(filePaths.ToList());
            else if (CsvToXlsx)
                FileUtility.CsvToXlsx(filePaths);
            SingleIcon.Toast("완료", "파일 드롭 이벤트 처리 완료");
        }

        private void QuitExcelProcesses() =>
            Process.GetProcesses().Where(p => p.ProcessName.Equals("excel", StringComparison.OrdinalIgnoreCase)).ForEach(p => p.Kill());

    }
}
