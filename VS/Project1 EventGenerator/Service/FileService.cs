using EventGenerator.Dao;
using EventGenerator.Utility;
using EventGenerator.ViewModel;
using Microsoft.Office.Interop.Excel;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

using static EventGenerator.Model.Constants;

namespace EventGenerator.Service
{
    public class FileService
    {
        public static void OpenFolder(string path) => System.Diagnostics.Process.Start(path);
        public static void OpenExecutingFolder() => OpenFolder(".\\");
        public static string FileName(string filePath) => filePath.Trim().Split(new char[] { '/', '\\' }).Last();
        public static string FileExt(string filePath) => filePath.Trim().Split('.').Last();
        public static string FileNameWithoutExt(string filePath) => Regex.Replace(FileName(filePath), $"\\.{FileExt(filePath)}$", "");

        public static bool Download(string remotePath, string fileName = null, string saveFileName = null, string savePath = null)
        {
            using (var client = new System.Net.WebClient())
            {
                try
                {
                    if (savePath != null)
                        Directory.CreateDirectory(savePath);
                    client.DownloadFile(remotePath + (fileName ?? ""), (savePath ?? IMG_SAVE_PATH) + (saveFileName ?? fileName ?? FileName(remotePath)));
                    Console.Write($"다운로드 성공 : {fileName ?? ""}@{remotePath}");
                    return true;
                }
                catch (Exception e) { Console.Write(e); }
            }
            return false;
        }
        
        public static bool ProcessCsvFiles(IEnumerable<string> filePaths) {
            var regex1 = new Regex(@"(\s)(\d:\d\d:\d\d)", RegexOptions.Compiled | RegexOptions.Multiline);
            var regex2 = new Regex("빈문자열", RegexOptions.Compiled | RegexOptions.Multiline);
            var regex3 = new Regex(@"(.+?)(\.csv).+");
            var processed = false;

            filePaths.Where(x => x.EndsWith(".csv", StringComparison.OrdinalIgnoreCase)).ForEach(filePath =>
            {
                processed = true;
                var text = File.ReadAllText(filePath);
                using (var writer = new StreamWriter(regex3.Replace(filePath, "$0$1")))
                    writer.Write(regex2.Replace(regex1.Replace(text, " 0$2"), "\"\""));
                Console.Write("CSV 처리 완료 : " + filePath);
            });
            return processed;
        }

        public static bool ProcessAcbFiles(IEnumerable<string> filePaths)
        {
            var processed = false;
            filePaths.Where(x => x.EndsWith(".acb", StringComparison.OrdinalIgnoreCase)).ForEach(filePath =>
            {
                processed = true;
                var text = File.ReadAllText(filePath);
                using (var writer = new StreamWriter(new FileStream(filePath + " → text.acb", FileMode.OpenOrCreate), Encoding.ASCII))
                {
                    var newText = new char[text.Length > 16384 ? 16384 : text.Length];
                    for (var i = 0; i < newText.Length; ++i)
                        newText[i] = (Char.IsLetterOrDigit(text[i]) ? text[i] : ' ');
                    writer.Write(newText);
                }
                Console.Write("ACB 처리 완료 : " + filePath);
            });
            return processed;
        }

        public static void XlsxToXls(IEnumerable<string> xlsxFilePaths)
        {
            xlsxFilePaths.ForEach(filePath =>
            {
                var excel = new Application
                {
                    DisplayAlerts = false
                };
                var books = excel.Workbooks;
                var book = books.Open(filePath);

                Console.Write(filePath + " 열기 완료");
                book.SaveAs(filePath.Replace(".xlsx", ".xls"), XlFileFormat.xlWorkbookNormal);
                Console.Write("저장 완료");
                book.Close(false);
                books.Close();
                excel.Quit();
            });
        }

        public static void MergeExcels(List<string> filePaths)
        {
            var excel = new Application
            {
                DisplayAlerts = false
            };
            var books = excel.Workbooks;
            var book = books.Open(filePaths[0]);
            var sheets = book.Worksheets;

            for (var i = 1; i < filePaths.Count; ++i)
            {
                var book2 = books.Open(filePaths[i]);
                var sheets2 = book2.Worksheets;

                for (var j = sheets2.Count; j > 0; --j)
                {
                    Worksheet sheet = sheets.Add(After: sheets[sheets.Count]);
                    sheet.Cells.NumberFormat = "@";
                    Worksheet sheet2 = sheets2[j];
                    sheet2.UsedRange.Copy();
                    sheet.UsedRange.PasteSpecial(XlPasteType.xlPasteValues);
                    var name = new Random().Next() + " " + sheet2.Name;
                    sheet.Name = name.Length > 30 ? name.Substring(0, 30) : name;
                }
                book2.Close(false);
            }
            book.SaveAs(new FileInfo(filePaths[0]).DirectoryName
                + (new FileInfo(filePaths[0]).DirectoryName.EndsWith("\\") ? "" : "\\")
                + "merge.xls");
            book.Close(false);
            books.Close();
            excel.Quit();
            Console.Write("저장 완료");
        }

        public static void CsvToXlsx(IEnumerable<string> filePaths)
        {
            filePaths.ForEach(filePath =>
            {
                var text = File.ReadAllText(filePath);
                text = text.Replace("\r", "");

                var regex1 = new Regex(@"\\\""", RegexOptions.Compiled | RegexOptions.Multiline);
                var replacement = new string[] { "''''", "\\\"" };
                var regex2 = new Regex(replacement[0], RegexOptions.Compiled | RegexOptions.Multiline);
                text = regex1.Replace(text, replacement[0]);

                var data = new List<string[]>();
                var reader = new StringReader(text);
                var header = reader.ReadLine().Split(',').ToArray();
                data.Add(header);

                text = reader.ReadToEnd();
                var quoteOpen = false;
                var cells = new List<string>();
                var cell = "";
                foreach (var c in text.ToCharArray())
                {
                    switch (c)
                    {
                        case ',':
                            if (quoteOpen)
                                cell += c;
                            else
                            {
                                cells.Add(cell);
                                cell = "";
                            }
                            break;
                        case '\n':
                            if (quoteOpen)
                                cell += c;
                            else if (cell.Length > 0)
                            {
                                cells.Add(cell);
                                cell = "";
                            }
                            break;
                        case '"':
                            quoteOpen = !quoteOpen;
                            break;
                        default:
                            cell += c;
                            break;
                    }
                }
                if (cell.Length > 0)
                    cells.Add(cell);
                var line = new string[header.Length];
                for (var i = 0; i < cells.Count; ++i)
                {
                    line[i % header.Length] = regex2.Replace(cells[i], replacement[1]);
                    if ((i + 1) % header.Length == 0)
                    {
                        data.Add(line);
                        line = new string[header.Length];
                    }
                }
                for (var i = 0; i < data.Count; i += 10)
                {
                    data.Add(line);
                    line = new string[header.Length];
                }

                var excel = new Application();
                excel.DisplayAlerts = false;
                var books = excel.Workbooks;
                var book = books.Open(filePath);
                var sheets = book.Worksheets;
                var sheet = sheets[1];

                Range range = sheet.Cells[1, 1];
                string[,] matrix = CollectionUtility.ToMatrix(data);
                range = range.get_Resize(matrix.GetLength(0), matrix.GetLength(1));
                range.set_Value(System.Reflection.Missing.Value, matrix);

                sheet.Cells.NumberFormat = "@";
                sheet.Cells.RowHeight = 13;

                Console.Write("CSV to XLSX 변환 완료");
                var fileName = FileService.FileName(filePath);
                book.SaveAs(new FileInfo(filePath).DirectoryName
                        + (new FileInfo(filePath).DirectoryName.EndsWith("\\") ? "" : "\\")
                        + fileName.Replace(".csv", ".xlsx"), XlFileFormat.xlWorkbookDefault);
                book.Close(false);
                books.Close();
                excel.Quit();
            });
        }
    }
}
