using EventGenerator.Dao;
using EventGenerator.Model;
using EventGenerator.Model.Dto;
using EventGenerator.Utility;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using static EventGenerator.Model.Dto.Game1;

namespace EventGenerator.Service
{
    class Game1Service
    {
        private BaseContext Context { get; } = new Game1Context();

        #region 대리자
        private List<T> AddItems<T>(IEnumerable<T> items) => Context.AddItems(items);

        private void AddSheet<T>(IEnumerable<T> items, DBServer server, bool addSheetWhenListEmpty = false, bool allowDuplicateSheets = false)
            => Context.AddSheet(items, server, addSheetWhenListEmpty, allowDuplicateSheets);

        public List<T> Get<T>() => Context.GetList<T>();

        public void Save(string fileName) => Context.Save(fileName);
        #endregion

        #region 서비스
        public List<Table1> SearchTable1ByColumn1(string start, string end) => Game1Dao.SelectTable1(start, end).ToList();

        public Game1Service MakeTable1(IEnumerable<Table1> items, DBServer server)
        {
            AddSheet(items, server);
            return this;
        }
        #endregion
    }
}
