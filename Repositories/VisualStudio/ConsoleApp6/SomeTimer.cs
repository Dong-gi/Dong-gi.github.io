using System.ComponentModel;

public class SomeTimer
{
    private static readonly object s_fireEventKey = new object();

    private EventHandlerList _eventHandlerList = new EventHandlerList();
    private readonly int _periodSeconds;
    private int _fireCount = 0;

    public event EventHandler<int> FireEvent
    {
        add
        {
            _eventHandlerList.AddHandler(s_fireEventKey, value);
            Console.WriteLine($"add : {value}");
        }
        remove
        {
            _eventHandlerList.RemoveHandler(s_fireEventKey, value);
            Console.WriteLine($"remove : {value}");
        }
    }


    public SomeTimer(int periodSeconds)
    {
        if (periodSeconds <= 0 || periodSeconds > 1000)
            throw new ArgumentOutOfRangeException(nameof(periodSeconds));

        _periodSeconds = periodSeconds;
        Fire();
    }

    private async void Fire()
    {
        await Task.Delay(_periodSeconds * 1000);
        if (_eventHandlerList[s_fireEventKey] is EventHandler<int> handler)
            handler(this, ++_fireCount);
        Fire();
    }
}