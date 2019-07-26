using EventGenerator.Service;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    class ImageOptimizer
    {
        private int Counter { get; set; } = 0;
        private List<System.Action> Workers { get; } = new List<Action>();

        public ImageOptimizer(IEnumerable<string> fileOrDirPaths) =>
            InitWorkers(fileOrDirPaths);
        
        public static List<System.Action> GetWorkers(string fileOrDirPath, string additionalSavePath = "")
        {
            Directory.CreateDirectory(@"ImageOptimizer\img\" + additionalSavePath);
            var result = new List<System.Action>();
            var directoryInfo = new DirectoryInfo(fileOrDirPath);
            if (directoryInfo.Exists)
            {
                directoryInfo.GetDirectories().ForEach(subDirectoryInfo => result.AddRange(GetWorkers(subDirectoryInfo.FullName, additionalSavePath + directoryInfo.Name + '\\')));
                directoryInfo.GetFiles("*.*").ForEach(fileInfo => result.AddRange(GetWorkers(fileInfo.FullName, additionalSavePath + directoryInfo.Name + '\\')));
            }
            else
            {
                result.Add(() =>
                {
                    using (var process = new System.Diagnostics.Process())
                    {
                        Console.Write($"Current file : {fileOrDirPath}");
                        process.StartInfo.RedirectStandardOutput = true;
                        process.StartInfo.UseShellExecute = false;
                        process.StartInfo.CreateNoWindow = true;

                        var fileInfo = new FileInfo(fileOrDirPath);
                        if (fileOrDirPath.EndsWith(".png", StringComparison.OrdinalIgnoreCase))
                        {
                            FileService.Download(fileOrDirPath, savePath: @"ImageOptimizer\img\" + additionalSavePath);
                            process.StartInfo.FileName = @"ImageOptimizer\pngquant.exe";
                            process.StartInfo.Arguments = $"--ext .png --force ImageOptimizer\\img\\{additionalSavePath}{fileInfo.Name}";
                            process.Start();
                        }
                        else if (fileOrDirPath.EndsWith(".jpg", StringComparison.OrdinalIgnoreCase) || fileOrDirPath.EndsWith(".jpeg", StringComparison.OrdinalIgnoreCase))
                        {
                            FileService.Download(fileOrDirPath, savePath: @"ImageOptimizer\");
                            process.StartInfo.FileName = @"ImageOptimizer\cjpeg.exe";
                            process.StartInfo.Arguments = $"-outfile \"ImageOptimizer\\img\\{additionalSavePath}{fileInfo.Name}\" \"ImageOptimizer\\{fileInfo.Name}\"";
                            process.Start();
                            process.WaitForExit();
                            Directory.EnumerateFiles(@"ImageOptimizer\").Where(x => x.Contains(fileInfo.Name)).ForEach(File.Delete);
                        }
                    }
                });
            }
            return result;
        }

        private void InitWorkers(IEnumerable<string> fileOrDirPaths) =>
            fileOrDirPaths.ForEach(fileOrDirPath => Workers.AddRange(GetWorkers(fileOrDirPath)));

        public void Optimize()
        {
            Task.WaitAll(Workers.Select(worker => Task.Factory.StartNew(worker)).ToArray());
            System.Diagnostics.Process.Start(@"ImageOptimizer\img");
        }
    }
}
