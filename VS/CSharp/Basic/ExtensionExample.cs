using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    public static class ExtensionExample
    {
        public static int Double(this int i) => i * 2;

        public static void Example()
        {
            Console.WriteLine(1.Double());
        }
    }
}
