using System;
using System.Collections.Generic;
using Microsoft.Office.Interop.Excel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using EventGenerator.ViewModel;
using System.IO;
using System.Drawing;

namespace EventGenerator.Utility
{
    public abstract class CustomExcel
    {
        protected static string saveDirPath = System.AppDomain.CurrentDomain.BaseDirectory;
        private static List<CustomExcel> list = new List<CustomExcel>();

        protected Application excel;
        protected Workbooks books;
        protected _Workbook currentBook;
        protected Sheets sheets;
        protected _Worksheet currentSheet;
        protected int sheetCount = 0;


        protected CustomExcel()
        {
            list.Add(this);
            excel = new Application
            {
                DisplayAlerts = false
            };
            books = excel.Workbooks;
        }


        public static void CloseAll() => list.ForEach(x => x.CloseWithoutSave());


        public abstract void AddSheet(List<string[]> list, bool allowDuplicateSheet, string sheetName = null);

        public string Save(string fileName)
        {
            var actualFileName = StringUtility.AlternateFileName(saveDirPath, fileName);
            try
            {
                // XlFileFormat.xlWorkbookNormal은 xls이며, 필요한 경우 변경
                currentBook.SaveAs(saveDirPath + actualFileName, XlFileFormat.xlWorkbookNormal);
                MainViewModel.Toast("저장 완료", actualFileName);
            }
            catch (Exception e)
            {
                Console.Write(e);
            }
            finally
            {
                CloseWithoutSave();
            }
            return actualFileName;
        }

        public void CloseWithoutSave()
        {
            try
            {
                currentBook.Close(false);
            }
            catch (Exception) { }
            try
            {
                books.Close();
            }
            catch (Exception) { }
            try
            {
                excel.Quit();
            }
            catch (Exception) { }
        }
    }

    public class CustomNewExcel : CustomExcel
    {
        public static CustomExcel NewInstance() => new CustomNewExcel();

        private CustomNewExcel()
        {
            currentBook = books.Add();
            sheets = currentBook.Worksheets;

            for (int i = sheets.Count; i > 1; i--)
                sheets[i].Delete();
        }

        public override void AddSheet(List<string[]> list, bool allowDuplicateSheet = false, string sheetName = null)
        {
            if (list == null || list.Count < 1) return;
            try
            {
                if (sheetName == null)
                    sheetName = new Random().Next().ToString();
                if (allowDuplicateSheet)
                    sheetName = (new Random().Next().ToString() + sheetName).Substring(0, 30);

                var writeNew = false;
                try
                {
                    currentSheet = sheets[sheetName];
                }
                catch (Exception)
                {
                    writeNew = true;
                }

                if (writeNew || currentSheet == null)
                {
                    int nextSheetPos = sheetCount + 1;
                    if (sheets.Count < nextSheetPos)
                        currentSheet = sheets.Add(After: sheets[sheets.Count]);
                    else
                        currentSheet = sheets[nextSheetPos];
                    sheetCount = nextSheetPos;
                }

                currentSheet.Name = sheetName;
                Range range = currentSheet.Cells[1, 1];
                string[,] data = CollectionUtility.ToMatrix(list.ToArray());
                range = range.get_Resize(data.GetLength(0), data.GetLength(1));
                range.set_Value(System.Reflection.Missing.Value, data);

                Range cells = currentSheet.Cells;
                cells.NumberFormat = "@"; // 텍스트 서식
                cells.HorizontalAlignment = XlHAlign.xlHAlignLeft; // 가로 정렬
                cells.VerticalAlignment = XlVAlign.xlVAlignTop; // 세로 정렬
                cells.Font.Name = "Arial";
                cells.Font.Size = 10;
                cells.RowHeight = 13;
                currentSheet.Rows[1].RowHeight = 26;
                currentSheet.Columns.AutoFit(); // 자동 열 너비. 느려질 수 있음

                currentSheet.Cells[1, 1].Interior.Color = ColorTranslator.ToOle(Color.FromArgb(0, 204, 255));
                currentSheet.Cells[1, 2].Interior.Color = ColorTranslator.ToOle(Color.Red);
                currentSheet.Cells[1, 3].Interior.Color = ColorTranslator.ToOle(Color.FromArgb(255, 204, 153));
                currentSheet.Cells[1, 4].Interior.Color = ColorTranslator.ToOle(Color.Yellow);

                for (var i = 1; i <= list[0].Length; ++i)
                    currentSheet.Columns[i].ColumnWidth = currentSheet.Columns[i].ColumnWidth > 18 ? 18 : currentSheet.Columns[i].ColumnWidth;
            }
            catch (Exception e)
            {
                Console.Write(e);
            }
        }
    }

    public class CustomCopyExcel : CustomExcel
    {
        public static CustomExcel NeewInstance(string filePath) => new CustomCopyExcel(filePath);

        private CustomCopyExcel(string filePath)
        {
            currentBook = books.Open(filePath);
            sheets = currentBook.Worksheets;
        }

        public override void AddSheet(List<string[]> list, bool allowDuplicateSheet = false, string sheetName = null)
        {
            try
            {
                if (sheetName == null)
                    sheetName = new Random().Next().ToString();
                if (allowDuplicateSheet)
                    sheetName = (new Random().Next().ToString() + sheetName).Substring(0, 30);

                var writeNew = false;
                try
                {
                    currentSheet = sheets[sheetName];
                }
                catch (Exception)
                {
                    writeNew = true;
                }

                if (writeNew || currentSheet == null)
                {
                    int nextSheetPos = sheetCount + 1;
                    if (sheets.Count < nextSheetPos)
                        currentSheet = sheets.Add(After: sheets[sheets.Count]);
                    else
                        currentSheet = sheets[nextSheetPos];
                    sheetCount = nextSheetPos;
                }

                Range cells = currentSheet.Cells;
                cells.NumberFormat = "@";

                // 템플릿 형식에 맞춰 특정 행, 열만 데이터를 쓴다.
                int row = 4;
                foreach (string[] strings in list)
                {
                    int col = 1;
                    foreach (string val in strings)
                        currentSheet.Cells[row, col++] = string.IsNullOrEmpty(val)? "" : val;
                    ++row;
                }
            }
            catch (Exception e)
            {
                Console.Write(e);
            }
        }
    }
}
