var authority = Authority.READ | Authority.WRITE;
Console.WriteLine(authority.HasFlag(Authority.WRITE));

[Flags]
enum Authority
{
    EXECUTE = 1,
    WRITE = 2,
    READ = 4,
    ALL = READ | WRITE | EXECUTE
}
