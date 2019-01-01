using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    class IndexerExample
    {
        public static void Example()
        {
            var arr = new IndexerExample();
            Console.WriteLine(arr["World"]);
        }

        public string this[string text] => "Hello " + text + "!";
    }
}
