using EventGenerator.Model.CustomAttribute;
using EventGenerator.Utility;
using MoreLinq;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace EventGenerator.Model.Dto
{
    static class Game1
    {
        [CustomDB(DB.DB1)]
        public class Table1
        {
            public string column1 { get; set; }
            public string column2 { get; set; }
        }
    }

    static class Game1Extension1
    {
        public class Table1 : Game1.Table1
        {
            public string extension_column1 { get; set; }
        }
    }
}
