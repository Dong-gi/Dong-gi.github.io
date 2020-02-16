using EventGenerator.Model;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Dao
{
    public class GaraDao : Dao
    {
        public GaraDao(DB db) : base(db) { }

        public override IEnumerable<T> Query<T>(DBServer insteadDBServer = null) => new List<T>();
    }
}
