public class Program
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

    public static void Main(string[] args)
    {
        var field = typeof(Program).GetField("field");
        if (field == null)
            throw new NullReferenceException(nameof(field));

        foreach (var attr in field.GetCustomAttributes(false))
            if (attr is MyAttribute myAttr)
                Console.WriteLine("{0}, {1}", myAttr.Arg, myAttr.NamedArg);
    }
}