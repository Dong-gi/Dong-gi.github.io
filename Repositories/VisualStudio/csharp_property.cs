class MainViewModel : INotifyPropertyChanged
{
    private readonly Dictionary<string, object> properties = new Dictionary<string, object>();
    public T Get<T>(string propertyName) => (T)properties[propertyName];
    public T Get<T>(string propertyName, T defaultValue)
    {
        try
        {
            return Get<T>(propertyName);
        }
        catch
        {
            Set<T>(propertyName, defaultValue);
            return defaultValue;
        }
    }
    private T Set<T>(string propertyName, T value, bool noRaise = false)
    {
        properties[propertyName] = value;
        if (!noRaise)
            RaisePropertyChanged(propertyName);
        return value;
    }

    #region UI 입력 텍스트 프로퍼티
    public string TextParam1
    {
        get => Get<string>(nameof(TextParam1), "").Copy();
        set => Set<string>(nameof(TextParam1), value);
    }
    public DateTime Date1
    {
        get => new DateTime(Get<DateTime>(nameof(Date1), DateTime.Now).Ticks);
        set => Set<DateTime>(nameof(Date1), value);
    }
    public string DateText1 => Date1.ToString("yyyy-MM-dd"); // get만 이용하는 경우 간략하게 할수 있다.
    #endregion

    #region INotifyPropertyChanged 구현 관련
    public event PropertyChangedEventHandler PropertyChanged;
    public void RaisePropertyChanged(string propertyName) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    #endregion
}