using System.Collections.Generic;

using static EventGenerator.Model.Dto.Game1;

namespace EventGenerator.Model
{
    class Game1Context : BaseContext
    {
        public List<Table1> Table1List { get; private set; } = new List<Table1>();
    }
}
