using System;
using System.Collections.Generic;
using Microsoft.Office.Interop.Excel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using EventGenerator.ViewModel;
using System.IO;
using System.Drawing;
using EventGenerator.Service;

namespace EventGenerator.Utility
{
    public class MSExcel
    {
        public static string SaveDir { get; set; } = System.AppDomain.CurrentDomain.BaseDirectory;
        public static readonly Application App;
        public static readonly Workbooks Workbooks;
        public readonly _Workbook Workbook;
        public readonly Sheets Sheets;
        public _Worksheet CurrentSheet { get; set; }

        static MSExcel()
        {
            App = new Application
            {
                DisplayAlerts = false
            };
            Workbooks = App.Workbooks;
        }

        public MSExcel(string templateFilePath = null)
        {
            if (templateFilePath == null || !File.Exists(templateFilePath))
            {
                Workbook = Workbooks.Add();
                Sheets = Workbook.Worksheets;
                for (var i = Sheets.Count; i > 1; --i)
                    Sheets[i].Delete();
            }
            else
            {
                Workbook = Workbooks.Open(templateFilePath);
                Sheets = Workbook.Worksheets;
            }
        }

        /// <param name="allowDuplicateSheet">true : 이미 동일한 이름의 시트가 존재하는 경우, 뒤에 숫자를 붙여 시트 추가</param>
        public MSExcel AddSheet(IEnumerable<IEnumerable<string>> data, string sheetName, bool allowDuplicateSheet = false)
        {
            if (data == null || !data.Any())
                return this;

            CurrentSheet = null;
            sheetName = DetermineSheetName(sheetName, allowDuplicateSheet);
            // CurrentSheet가 설정된 경우엔 기존 시트에 덮어쓰기
            if (CurrentSheet == null)
                CurrentSheet = Sheets.Add(After: Sheets[Sheets.Count]);
            CurrentSheet.Name = sheetName;
            AfterSheetSelected(ref sheetName);

            string[,] sheetData = CollectionUtility.ToMatrix(data);
            AfterDataTransformed(ref sheetName, ref data, ref sheetData);

            Range range = CurrentSheet.Cells[1, 1];
            range = range.get_Resize(sheetData.GetLength(0), sheetData.GetLength(1));
            range.set_Value(System.Reflection.Missing.Value, sheetData);
            AfterSheetWorte(ref sheetName, ref data, ref sheetData);

            return this;
        }

        private string DetermineSheetName(string sheetName, bool allowDuplication, int dupNum = -1)
        {
            var tmpName = sheetName.Copy();
            var maxLength = 30 - (dupNum < 0 ? 0 : dupNum.ToString().Length);
            if (tmpName.Length > maxLength)
                tmpName = tmpName.Substring(0, maxLength);
            if (dupNum >= 0)
                tmpName = $"{tmpName}{dupNum}";
            try
            {
                CurrentSheet = Sheets[tmpName];
                if (allowDuplication)
                    return DetermineSheetName(sheetName, allowDuplication, dupNum + 1);
                else
                    return tmpName;
            }
            catch
            {
                // 동일 이름의 시트가 없으면 이 이름을 쓰도록
                return tmpName;
            }
        }

        protected virtual void AfterSheetSelected(ref string sheetName) { }
        protected virtual void AfterDataTransformed(ref string sheetName, ref IEnumerable<IEnumerable<string>> data, ref string[,] sheetData) { }
        protected virtual void AfterSheetWorte(ref string sheetName, ref IEnumerable<IEnumerable<string>> data, ref string[,] sheetData) { }

        public string Save(string fileName, object format = null, int retry = 5)
        {
            var actualFileName = FileUtility.AlternateFileName(SaveDir, fileName);
            try
            {
                Workbook.Close(true, SaveDir + actualFileName, format ?? XlFileFormat.xlWorkbookNormal);
                SingleIcon.Toast("저장 완료", actualFileName);
            }
            catch (Exception e)
            {
                if (retry > 0)
                    Save($"{FileUtility.FileNameWithoutExt(fileName)}_{new DateTime().Millisecond}.{FileUtility.FileExt(fileName)}", format, retry - 1);
                else
                {
                    SingleIcon.Toast("저장 실패. 재시도 포기", fileName);
                    Console.Write(e);
                    Workbook.Close(false);
                }
            }
            return actualFileName;
        }
    }

    public class CustomExcel : MSExcel
    {
        protected override void AfterSheetWorte(ref string sheetName, ref IEnumerable<IEnumerable<string>> data, ref string[,] sheetData)
        {
            Range range = CurrentSheet.Cells;
            range.NumberFormat = "@";                           // 텍스트 서식
            range.HorizontalAlignment = XlHAlign.xlHAlignLeft;  // 가로 정렬
            range.VerticalAlignment = XlVAlign.xlVAlignTop;     // 세로 정렬
            range.Font.Name = "Arial";                          // Font-family
            range.Font.Size = 10;                               // Font-size
            range.RowHeight = 13;                               // 행 높이
            CurrentSheet.Rows[1].RowHeight = 26;
            CurrentSheet.Columns.AutoFit();                     // 자동 열 너비. 느려질 수 있음
                                                                // ↓ 셀 색상
            CurrentSheet.Cells[1, 1].Interior.Color = ColorTranslator.ToOle(Color.FromArgb(0, 204, 255));
            CurrentSheet.Cells[1, 2].Interior.Color = ColorTranslator.ToOle(Color.Red);
            CurrentSheet.Cells[1, 3].Interior.Color = ColorTranslator.ToOle(Color.FromArgb(255, 204, 153));
            CurrentSheet.Cells[1, 4].Interior.Color = ColorTranslator.ToOle(Color.Yellow);

            for (var i = 1; i <= sheetData.GetLength(1); ++i)
                CurrentSheet.Columns[i].ColumnWidth = CurrentSheet.Columns[i].ColumnWidth > 18 ? 18 : CurrentSheet.Columns[i].ColumnWidth;
        }
    }
}
