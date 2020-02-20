using System;
using System.Threading.Tasks;
using System.Windows.Input;

namespace EventGenerator.Utility
{
    public class CustomCommand : ICommand
    {
        public Action<object> Command { get; set; }
        public Predicate<object> CanExecuteAction { get; set; } = (x => true);
        public bool IsBackground { get; set; } = true;


        public CustomCommand() { }
        public CustomCommand(Action action, bool isBackground = true)
        {
            Command = (_) => action();
            IsBackground = isBackground;
        }
        public CustomCommand(Action<object> action, bool isBackground = true)
        {
            Command = action;
            IsBackground = isBackground;
        }


        public bool CanExecute(object parameter) => CanExecuteAction(parameter);
        public event EventHandler CanExecuteChanged
        {
            add
            {
                CommandManager.RequerySuggested += value;
            }
            remove
            {
                CommandManager.RequerySuggested -= value;
            }
        }
        public void Execute(object param = null)
        {
            if (Command == null) return;
            if (IsBackground)
                Task.Factory.StartNew(Command, param);
            else
            {
                try
                {
                    Command(param);
                }
                catch (Exception e)
                {
                    Console.WriteLine(e);
                }
            }
        }
    }
}
