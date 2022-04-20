public class SomeTimer
{
    public event EventHandler<int> FireEvent = delegate { };

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
        FireEvent.Invoke(this, ++_count);
        Fire();
    }
}