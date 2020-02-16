using EventGenerator.Model;
using System.Collections.Generic;
using static EventGenerator.Model.Dto.Game1;
using static EventGenerator.Model.Constants;

namespace EventGenerator.Dao
{
    class Game1Dao : Dao
    {
        public static Game1Dao DB1Dao() => new Game1Dao(DB.DB1);
        public static Game1Dao DB2Dao() => new Game1Dao(DB.DB2);


        public Game1Dao(DB db) : base(db) { }


        #region DB1
        public static IEnumerable<Table1> SelectTable1(IEnumerable<string> ids)
            => DB1Dao().SelectAllWhereIdIn(table1, column1, ids).OrderBy(column1).Query<Table1>();

        public static IEnumerable<Table1> SelectTable1(string start, string end)
            => DB1Dao().SelectAll().Where($"{column1} between '{start}' and '{end}'").OrderBy(column1).Query<Table1>();
        #endregion
    }
}
