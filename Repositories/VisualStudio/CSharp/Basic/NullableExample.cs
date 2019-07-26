using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    class NullableExample
    {
        public static void Example()
        {
            int? num = null;
            Console.WriteLine(num.HasValue);
            try
            {
                Console.WriteLine(num.Value);
            }
            catch (Exception e)
            {
                Console.WriteLine(e.Message);
            }

            num = 1;
            Console.WriteLine(num.HasValue);
            Console.WriteLine(num.Value);
            // string? name; 8.0 부터
            // name!.Length https://docs.microsoft.com/ko-kr/dotnet/csharp/nullable-references
        }

        // #nullable enable
        // #nullable disable
    }
}
