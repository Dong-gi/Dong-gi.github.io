using EventGenerator.Model.CustomAttribute;
using MoreLinq;
using System;
using System.Collections;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using static EventGenerator.Model.Constants;

namespace EventGenerator.Utility
{
    public class CustomExcelDataUtility
    {
        public static List<string[]> ConvertToExcelData<T>(IEnumerable<T> items, bool returnAnyway = false)
        {
            if (!returnAnyway && (items?.Count() ?? 0) < 1) return new List<string[]>();
            var result = new List<string[]>();
            var type = items.Count() < 1? typeof(T) : items.First().GetType();

            var fieldNameLine = new List<string>() { "columns" };
            var activeProperties = type.GetRuntimeProperties().Where(propertyInfo =>
                                        // 버전 정보가 명시되지 않은 경우, 유효한 걸로 간주
                                        !(System.Attribute.GetCustomAttribute(propertyInfo, typeof(CustomVersionAttribute)) is CustomVersionAttribute version)
                                        || version.Active(ViewModel.MainViewModel.Current.CurrentDBServer.Version)
                                   ).ToList();
            // 확장 프로퍼티는 맨 마지막에 쓰기 위해
            var extensionProperties = activeProperties.Where(x => x.DeclaringType.FullName.Contains("Extension")).ToList();
            activeProperties.RemoveAll(x => extensionProperties.Contains(x));
            activeProperties.AddRange(extensionProperties);
            fieldNameLine.AddRange(activeProperties.Select(propertyInfo => StringUtility.ReplaceUnderbarToUpper(propertyInfo.Name)));
            result.Add(fieldNameLine.ToArray());

            foreach (object obj in items)
            {
                if (obj == null) continue;

                var dataLine = new string[fieldNameLine.Count];
                var idx = 0;
                foreach (PropertyInfo propertyInfo in activeProperties)
                {
                    idx++;
                    object fieldVal = propertyInfo.GetValue(obj);
                    if(fieldVal == null) continue;

                    // SQLite에서 Timestamp를 가져와도 스레드에 설정된 문화권의 포맷을 이용하지 않아 별도 처리
                    if (ViewModel.MainViewModel.Current.CurrentDBServer.IsSQLite
                        && propertyInfo.Name.Contains("date")
                        && DateTime.TryParse(fieldVal as string, out DateTime dateTime))
                            fieldVal = dateTime.ToString("yyyy-MM-dd HH:mm:ss");

                    dataLine[idx] = fieldVal?.ToString();
                }
                result.Add(dataLine);
            }
            return result;
        }

        public static List<string[]> GaraRowsToExcelData(IEnumerable<Model.Dto.Gara> rows, string[] columns)
        {
            if (!rows.Any()) return null;
            var result = new List<string[]>() { columns, Array.Empty<string>(), columns };
            rows.ForEach(row =>
            {
                var dataLine = new string[columns.Length];
                for (var i = 0; i < columns.Length; ++i)
                    dataLine[i] = row.Get(i);
                result.Add(dataLine);
            });
            return result;
        }

        public static List<string[]> DapperRowToExcelData(IEnumerable<dynamic> rows)
        {
            if (!rows.Any()) return null;
            var result = new List<string[]>() { Array.Empty<string>(), Array.Empty<string>() };
            var fieldNameLine = new List<string>();
            IEnumerator<KeyValuePair<string, object>> row = (rows.First() as IDictionary<string, object>).GetEnumerator();
            while (row.MoveNext())
                fieldNameLine.Add(row.Current.Key);
            result.Add(fieldNameLine.ToArray());

            rows.ForEach(dapperRow =>
            {
                var dataLine = new string[fieldNameLine.Count];
                row = (dapperRow as IDictionary<string, object>).GetEnumerator();
                for (var i = 0; i < dataLine.Length; ++i)
                {
                    row.MoveNext();
                    dataLine[i] = (row.Current.Value ?? "").ToString();
                }
                result.Add(dataLine);
            });
            return result;
        }
    }
}