using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Input;

namespace EventGenerator.Utility
{
    public class CustomCommand : ICommand
    {
        private readonly Action noArgAction;
        private readonly Action<object> argumentAction;
        private readonly Predicate<object> canExecute = (x => true);
        private readonly bool isBackground;


        public CustomCommand(Action<object> action, Predicate<object> canExecute = null, bool isBackground = true)
        {
            this.argumentAction = action;
            this.canExecute = canExecute ?? this.canExecute;
            this.isBackground = isBackground;
        }

        public CustomCommand(Action action, bool isBackground = true)
        {
            this.noArgAction = action;
            this.isBackground = isBackground;
        }

        public bool CanExecute(object parameter) => canExecute(parameter);

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
            if (isBackground)
            {
                if (argumentAction != null)
                    Task.Factory.StartNew(argumentAction, param);
                else
                    Task.Factory.StartNew(noArgAction);
            }
            else
            {
                if (argumentAction != null)
                    argumentAction(param);
                else
                    noArgAction();
            }
        }
    }
}
