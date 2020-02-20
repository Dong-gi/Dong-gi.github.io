using EventGenerator.Dao;
using EventGenerator.Model;
using System.Collections.Generic;
using System.Linq;
using static EventGenerator.Model.Dto.Game1;

namespace EventGenerator.Service
{
    class Game1Service
    {
        private BaseContext Context { get; } = new BaseContext();

        #region 대리자
        private List<T> AddItems<T>(IEnumerable<T> items) => Context.AddItems(items);

        private void AddSheet<T>(DBServer server, IEnumerable<T> items, bool addSheetWhenListEmpty = false, bool allowDuplicateSheets = false)
            => Context.AddSheet(server, items, addSheetWhenListEmpty, allowDuplicateSheets);

        public List<T> Get<T>() => Context.GetList<T>();

        public void Save(string fileName) => Context.Save(fileName);
        #endregion

        #region 서비스
        public List<Table1> SearchTable1ByColumn1(DBServer server, string start, string end) => Game1Dao.SelectTable1(server, start, end).ToList();

        public Game1Service MakeTable1(DBServer server, IEnumerable<Table1> items)
        {
            AddSheet(server, items);
            return this;
        }
        #endregion
    }
}
