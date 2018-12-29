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
            public MyAttribute(string arg = "", String namedArg = "") => (Arg, NamedArg) = (arg, namedArg);

            public string Arg { get; }
            public string NamedArg { get; set; }
        }

        public static void Example()
        {
            var example = new AttributeExample();
            typeof(AttributeExample)
                .GetField("field")
                .GetCustomAttributes(false)
                .ForEach(x => Console.WriteLine("{0}, {1}", (x as MyAttribute).Arg, (x as MyAttribute).NamedArg));
        }

        [My("arg1", NamedArg = "namedArg1")]
        [My("arg2", NamedArg = "namedArg2")]
        [My("arg3", NamedArg = "namedArg3")]
        [My("arg4", NamedArg = "namedArg4")]
        public int field = 0;
    }
}
