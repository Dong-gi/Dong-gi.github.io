﻿using Amazon.CloudFront.Model;
using EventGenerator.Model;
using EventGenerator.Service;
using EventGenerator.Utility;
using Microsoft.Office.Interop.Excel;
using MoreLinq;
using Newtonsoft.Json;
using Renci.SshNet;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Input;

using static EventGenerator.Model.Constants;

namespace EventGenerator.ViewModel
{
    class Game2ViewModel
    {
        public Dictionary<string, ICommand> Commands { get; private set; } = new Dictionary<string, ICommand>();
        public DBServer[] DBServers { get; private set; } = { DBServer.GAME2_NOW, DBServer.GAME2_DUMP };

        public Game2ViewModel()
        {
            Commands[QUIT_EXCEL_COMMAND] = new CustomCommand(QuitExcelProcesses);
            Commands[SSH_EXAMPLE_COMMAND] = new CustomCommand(new SSHService().SSHExample);
            Commands[AWS_CLOUD_FRONT_INVALIDATION_COPY_COMMAND] = new CustomCommand(new AWSService().AWSCloudFrontInvalidationCopy);

            Console.Write("Game2 초기화 완료");
        }

        public bool ImageOptimize { get; set; } = true;
        public bool XlsxToXls { get; set; }
        public bool MergeSheets { get; set; }
        public bool CsvToXlsx { get; set; }
        public void FileDroped(string[] filePaths)
        {
            if (ImageOptimize)
                new ImageOptimizer(filePaths).Optimize();
            else if (XlsxToXls)
                FileService.XlsxToXls(filePaths.Where(filePath => filePath.EndsWith("xlsx")));
            else if (MergeSheets)
                FileService.MergeExcels(filePaths.ToList());
            else if (CsvToXlsx)
                FileService.CsvToXlsx(filePaths);
            MainViewModel.Toast("완료", "파일 드롭 이벤트 처리 완료");
        }

        private void QuitExcelProcesses() =>
            Process.GetProcesses().Where(p => p.ProcessName.Equals("excel", StringComparison.OrdinalIgnoreCase)).ForEach(p => p.Kill());

    }
}