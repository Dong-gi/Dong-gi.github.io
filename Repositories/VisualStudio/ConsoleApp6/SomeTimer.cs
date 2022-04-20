using System.ComponentModel;

public class SomeTimer
{
    private EventHandlerList _eventHandlerList = new EventHandlerList();

    private static readonly object _fireEventKey = new object();

    public event EventHandler<int> FireEvent
    {
        add
        {
            _eventHandlerList.AddHandler(_fireEventKey, value);
            Console.WriteLine($"add : {value}");
        }
        remove
        {
            _eventHandlerList.RemoveHandler(_fireEventKey, value);
            Console.WriteLine($"remove : {value}");
        }
    }

    private readonly int _periodSeconds;
    private int _count = 0;

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
        if (_eventHandlerList[_fireEventKey] != null)
            ((EventHandler<int>)_eventHandlerList[_fireEventKey])(this, ++_count);
        Fire();
    }
}