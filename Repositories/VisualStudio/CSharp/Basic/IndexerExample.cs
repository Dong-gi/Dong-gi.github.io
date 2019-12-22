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
            var obj = new IndexerExample();
            obj[1] = 0x1234;
            Console.WriteLine(obj["World"]);
            Console.WriteLine(obj["msg", "Hello World"]);
        }

        public int this[int idx]
        {
            get => idx;
            set => Console.WriteLine($"{idx} : 0x{value:X}");
        }
        public string this[string name] => $"Hello {name}!";
        public string this[string key, string value] => $"{{\"{key}\":\"{value}\"}}";
    }
}
