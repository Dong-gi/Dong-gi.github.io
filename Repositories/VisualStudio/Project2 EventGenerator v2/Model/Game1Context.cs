using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

using static EventGenerator.Model.Dto.Game1;

namespace EventGenerator.Model
{
    class Game1Context : BaseContext
    {
        public List<Table1> Table1List { get; private set; } = new List<Table1>();
    }
}
