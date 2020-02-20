using EventGenerator.Model;
using System.Collections.Generic;

namespace EventGenerator.Dao
{
    public class GaraDao : Dao
    {
        public GaraDao(DB db) : base(db) { }

        public override IEnumerable<T> Query<T>(DBServer server = null, bool print = false) => new List<T>();
    }
}
