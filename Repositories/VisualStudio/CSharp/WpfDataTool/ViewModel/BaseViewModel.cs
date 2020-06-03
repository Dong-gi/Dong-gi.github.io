using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Windows.Input;

namespace WpfDataTool.ViewModel
{
    class BaseViewModel : INotifyPropertyChanged
    {
        public Dictionary<string, ICommand> Commands { get; private set; } = new Dictionary<string, ICommand>();
        protected readonly Dictionary<string, object> Properties = new Dictionary<string, object>();
        public T Get<T>(string propertyName) => (T)Properties[propertyName];
        public T Get<T>(string propertyName, Func<T> func)
        {
            try
            {
                return Get<T>(propertyName);
            }
            catch
            {
                Set<T>(propertyName, func.Invoke());
                return Get<T>(propertyName);
            }
        }
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
        protected T Set<T>(string propertyName, T value, bool raiseChanged = true)
        {
            Properties[propertyName] = value;
            if (raiseChanged)
                RaisePropertyChanged(propertyName);
            return value;
        }


        #region INotifyPropertyChanged 구현
        public event PropertyChangedEventHandler PropertyChanged;
        public virtual void RaisePropertyChanged(string propertyName)
            => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        public void RaisePropertyChanged(object viewModel, string propertyName)
            => PropertyChanged?.Invoke(viewModel, new PropertyChangedEventArgs(propertyName));
        #endregion
    }
}
