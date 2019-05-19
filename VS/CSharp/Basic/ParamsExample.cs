using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    public class ParamsExample
    {
        public static int Sum(params int[] args) => args.Sum();

        public static void Example()
        {
            Console.WriteLine(Sum(1, 2, 3, 4));
            Console.WriteLine(Sum(new int[] { 1, 2, 3, 4 }));
        }
    }
}
