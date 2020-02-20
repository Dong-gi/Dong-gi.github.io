using EventGenerator.Utility;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace EventGenerator.Model
{
    public class BaseContext
    {
        public MSExcel Excel { get; private set; } = new MSExcel();
        protected readonly Dictionary<Type, object> TypeListPairs = new Dictionary<Type, object>();

        public List<T> GetList<T>()
        {
            try
            {
                return TypeListPairs[typeof(T)] as List<T>;
            }
            catch
            {
                return SetList(new List<T>());
            }
        }
        public List<T> SetList<T>(List<T> list)
        {
            TypeListPairs[typeof(T)] = list;
            return list;
        }

        public List<T> AddItems<T>(IEnumerable<T> items)
        {
            var baseList = GetList<T>();
            if (items == null || items.Count() < 1)
                return baseList;

            baseList.AddRange(items);
            var key = typeof(T).GetMethod("Key");
            if (key != null)
                return SetList(baseList.DistinctBy(obj => key.Invoke(null, new Object[] {obj})).ToList());
            return baseList;
        }

        public void AddSheet<T>(DBServer server, IEnumerable<T> items, bool addSheetWhenListEmpty = false, bool allowDuplicateSheets = false)
            => Excel.AddSheet(ExcelDataUtility.MakeGame1SheetData(server, AddItems(items), returnAnyway: addSheetWhenListEmpty), typeof(T).Name, allowDuplicateSheets);

        public void Save(string fileName) => Task.Factory.StartNew(() => Excel.Save(fileName));
    }
}
