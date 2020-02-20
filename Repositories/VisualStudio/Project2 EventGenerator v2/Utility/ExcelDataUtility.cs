using EventGenerator.Model;
using EventGenerator.Model.CustomAttribute;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;

namespace EventGenerator.Utility
{
    public static class ExcelDataUtility
    {
        /// <param name="returnAnyway">데이터가 없어도 시트에 쓰려는 경우</param>
        public static List<List<string>> MakeGame1SheetData<T>(DBServer server, IEnumerable<T> items, bool returnAnyway = false)
        {
            if (!returnAnyway && (items?.Count() ?? 0) < 1)
                return new List<List<string>>();
            
            var result = new List<List<string>>();
            var type = items.Any() ? items.First().GetType() : typeof(T);

            var fieldNameLine = new List<string>() { "columns" };
            var activeProperties = type.GetRuntimeProperties().Where(propertyInfo =>
                                        // 버전 정보가 명시되지 않은 경우, 유효한 걸로 간주
                                        !(System.Attribute.GetCustomAttribute(propertyInfo, typeof(CustomVersionAttribute)) is CustomVersionAttribute version)
                                        || version.Active(server.Version)
                                   ).ToList();
            // 확장 프로퍼티는 맨 마지막에 쓰기 위해
            var extensionProperties = activeProperties.Where(x => x.DeclaringType.FullName.Contains("Extension")).ToList();
            activeProperties.RemoveAll(x => extensionProperties.Contains(x));
            activeProperties.AddRange(extensionProperties);
            fieldNameLine.AddRange(activeProperties.Select(propertyInfo => StringUtility.ReplaceUnderbarToUpper(propertyInfo.Name)));
            result.Add(fieldNameLine);

            foreach (var obj in items)
            {
                if (obj == null) continue;

                var dataLine = new List<string>();
                foreach (PropertyInfo propertyInfo in activeProperties)
                {
                    var fieldVal = propertyInfo.GetValue(obj);
                    if (fieldVal == null)
                    {
                        dataLine.Add(null);
                        continue;
                    }

                    // SQLite에서 Timestamp를 가져와도 스레드에 설정된 문화권의 포맷을 이용하지 않아 별도 처리
                    if (server.ServerKind == ServerKind.SQLITE
                        && propertyInfo.Name.Contains("date")
                        && DateTime.TryParse(fieldVal as string, out DateTime dateTime))
                            fieldVal = dateTime.ToString("yyyy-MM-dd HH:mm:ss");

                    dataLine.Add(fieldVal?.ToString());
                }
                result.Add(dataLine);
            }
            return result;
        }

        public static List<List<string>> MakeGame1SheetData(IEnumerable<Model.Dto.Gara> rows, IEnumerable<string> columns)
        {
            if (!rows.Any())
                return new List<List<string>>();
            var result = new List<List<string>> { columns.ToList(), new List<string>(), columns.ToList() };
            rows.ForEach(row =>
            {
                var dataLine = new List<string>();
                for (var i = 0; i < columns.Count(); ++i)
                    dataLine[i] = row.Get(i);
                result.Add(dataLine);
            });
            return result;
        }

        public static List<List<string>> MakeGame1SheetDataByDapperRow(IEnumerable<dynamic> rows)
        {
            if (!rows.Any())
                return new List<List<string>>();
            var result = new List<List<string>>() { new List<string>(), new List<string>() };
            var fieldNameLine = new List<string>();
            IEnumerator<KeyValuePair<string, object>> row = (rows.First() as IDictionary<string, object>).GetEnumerator();
            while (row.MoveNext())
                fieldNameLine.Add(row.Current.Key);
            result.Add(fieldNameLine);

            rows.ForEach(dapperRow =>
            {
                var dataLine = new List<string>();
                row = (dapperRow as IDictionary<string, object>).GetEnumerator();
                while (row.MoveNext())
                    dataLine.Add((row.Current.Value ?? "").ToString());
                result.Add(dataLine);
            });
            return result;
        }
    }
}