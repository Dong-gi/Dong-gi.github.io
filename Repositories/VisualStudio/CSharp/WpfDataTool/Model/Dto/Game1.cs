using WpfDataTool.Model.Attr;
using WpfDataTool.Model.Enum;

namespace WpfDataTool.Model.Dto
{
    static class Game1
    {
        [DB(DB.DB1)]
        public class Table1
        {
            public string column1 { get; set; }
            public string column2 { get; set; }
        }
        [DB(DB.DB2)]
        public class Table2
        {
            public string old_column { get; set; }
            [Ver(removed: 2.0)]
            public string rm_column { get; set; }
            [Ver(3.0)]
            public string new_column { get; set; }
        }
    }

    static class Game1Extension
    {
        public class Table1 : Game1.Table1
        {
            public string ext_column1 { get; set; }
        }
    }
}
