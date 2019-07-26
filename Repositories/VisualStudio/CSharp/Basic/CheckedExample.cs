using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    public class CheckedExample
    {
        public static void Example()
        {
            checked
            {
                var i = 2_100_000_000;
                try
                {
                    i *= 2;
                    Console.WriteLine("연산 완료 1");
                }
                catch (Exception e)
                {
                    Console.WriteLine(e);
                }
            }

            unchecked
            {
                var i = 2_100_000_000;
                try
                {
                    i *= 2;
                    Console.WriteLine("연산 완료 2");
                }
                catch (Exception e)
                {
                    Console.WriteLine(e);
                }
            }
        }
    }
}
