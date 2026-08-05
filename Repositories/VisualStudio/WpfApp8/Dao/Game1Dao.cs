using System.Collections.Generic;
using System.Linq;
using WpfDataTool.Model;
using static WpfDataTool.Model.Dto.Game1;
using static WpfDataTool.Model.Constants;
using WpfDataTool.Model.Enum;

namespace WpfDataTool.Dao
{
    class Game1Dao : Dao
    {
        public static Game1Dao DB1Dao() => new Game1Dao(DB.DB1);
        public static Game1Dao DB2Dao() => new Game1Dao(DB.DB2);


        public Game1Dao(DB db) : base(db) { }


        #region DB1
        public static IEnumerable<Table1> SelectTable1(DBServer server, IEnumerable<string> ids)
            => DB1Dao().SELECT_ALL_FROM_table_WHERE_column_IN_ids(table1, column1, ids).OrderBy(column1).Query<Table1>(server);

        public static IEnumerable<Table1> SelectTable1(DBServer server, string start, string end)
            => DB1Dao().SelectAll().Where($"{column1} between '{start}' and '{end}'").OrderBy(column1).Query<Table1>(server);
        #endregion
    }
}
