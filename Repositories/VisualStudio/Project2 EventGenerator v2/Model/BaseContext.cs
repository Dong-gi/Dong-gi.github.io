using EventGenerator.Utility;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace EventGenerator.Model
{
    abstract class BaseContext
    {
        public CustomExcel Excel { get; private set; } = CustomNewExcel.NewInstance();


        public void Save(string fileName) => Task.Factory.StartNew(() => Excel.Save(fileName));

        public void AddSheet<T>(IEnumerable<T> items, DBServer server, bool addSheetWhenListEmpty = false, bool allowDuplicateSheets = false) =>
            Excel.AddSheet(CustomExcelDataUtility.ConvertToExcelData(AddItems(items), server, returnAnyway: addSheetWhenListEmpty), allowDuplicateSheets);

        public List<T> AddItems<T>(IEnumerable<T> items)
        {
            var baseList = GetList<T>();
            if (items == null || items.Count() < 1)
                return baseList;

            baseList.AddRange(items);
            var key = typeof(T).GetMethod("Key");
            if (key != null)
                baseList = baseList.DistinctBy(obj => key.Invoke(null, new Object[] {obj})).ToList();
            return baseList;
        }

        public List<T> GetList<T>()
        {
            var context = this.GetType();
            var property = context.GetProperty(typeof(T).Name + "List", typeof(List<T>));
            return context.GetProperty(typeof(T).Name + "List", typeof(List<T>)).GetValue(this) as List<T>;
        }
    }
}
