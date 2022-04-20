[Flags]
enum Authority
{
    EXECUTE = 1,
    WRITE = 2,
    READ = 4,
    ALL = READ | WRITE | EXECUTE
}

public class Program
{
    public static void Main(string[] args)
    {
        var authority = Authority.READ | Authority.WRITE;
        Console.WriteLine(authority.HasFlag(Authority.WRITE));
    }
}