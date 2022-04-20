public class Program
{
    public static void Main(string[] args)
    {
        var timer = new SomeTimer(1);
        timer.FireEvent += Timer_FireEvent;
        Task.Delay(10000).Wait();
    }

    private static void Timer_FireEvent(object? sender, int e)
    {
        Console.WriteLine(e);
    }

}