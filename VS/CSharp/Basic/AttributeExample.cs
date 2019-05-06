using System;
using System.Collections.Generic;
using System.Linq;
using MoreLinq;
using System.Text;
using System.Threading.Tasks;

namespace Basic
{
    public class AttributeExample
    {
        [System.AttributeUsage(AttributeTargets.All, Inherited = false, AllowMultiple = true)]
        public sealed class MyAttribute : Attribute
        {
            public MyAttribute(string arg = "") => (Arg, NamedArg) = (arg, "");

            public string Arg { get; }
            public string NamedArg { get; set; }
        }

        [My("arg1")]
        [My("arg2", NamedArg = "namedArg2")]
        [field: My("arg3", NamedArg = "namedArg3")]
        public int field = 0;

        public static void Example()
        {
            typeof(AttributeExample)
                .GetField("field")
                .GetCustomAttributes(false)
                .ForEach(x => Console.WriteLine("{0}, {1}", (x as MyAttribute).Arg, (x as MyAttribute).NamedArg));
        }
    }
}
