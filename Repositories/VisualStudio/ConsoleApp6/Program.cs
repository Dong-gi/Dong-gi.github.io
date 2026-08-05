public class Program
{
    public static void Main(string[] args)
    {
        var timer = new SomeTimer(1);
        timer.FireEvent += Timer_FireEvent;
        timer.FireEvent += Timer_FireEvent;
        Task.Delay(5000).Wait();
        timer.FireEvent -= Timer_FireEvent;
        Task.Delay(1500).Wait();
    }

    private static void Timer_FireEvent(object? sender, int e)
    {
        Console.WriteLine(e);
    }
}