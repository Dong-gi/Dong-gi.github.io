using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using static WpfDataTool.Model.Constants;

namespace WpfDataTool.Utility
{
    public class FileUtility
    {
        public static void OpenFolder(string path) => Process.Start("explorer.exe", path);
        public static void OpenExecutingFolder() => OpenFolder(".\\");
        public static string FileName(string filePath) => filePath.Trim().Split(new char[] { '/', '\\' }).Last();
        public static string FileExt(string filePath) => filePath.Trim().Split('.').Last();
        public static string FileNameWithoutExt(string filePath) => Regex.Replace(FileName(filePath), $"\\.{FileExt(filePath)}$", "");

        public static bool Download(string remotePath, string fileName = null, string saveFileName = null, string savePath = null, bool print = false)
        {
            using var client = new System.Net.WebClient();
            try
            {
                if (savePath != null)
                    Directory.CreateDirectory(savePath);
                client.DownloadFile($"{remotePath}{fileName ?? ""}", $"{savePath ?? IMG_SAVE_PATH}{saveFileName ?? fileName ?? FileName(remotePath)}");
                Console.Write($"다운로드 성공 : {fileName ?? ""}@{remotePath}");
                return true;
            }
            catch (Exception e)
            {
                if (print)
                    Console.Write(e);
            }
            return false;
        }
    }
}
