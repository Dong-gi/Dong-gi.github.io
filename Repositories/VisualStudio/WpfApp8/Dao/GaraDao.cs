using System.Collections.Generic;
using WpfDataTool.Model;
using WpfDataTool.Model.Enum;

namespace WpfDataTool.Dao
{
    public class GaraDao : Dao
    {
        public GaraDao(DB db) : base(db) { }

        public override IEnumerable<T> Query<T>(DBServer server = null) => new List<T>();
    }
}
