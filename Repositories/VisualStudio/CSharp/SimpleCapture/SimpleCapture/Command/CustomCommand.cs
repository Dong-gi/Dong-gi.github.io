using System;
using System.Windows.Input;

namespace SimpleCapture.Utility
{
    public class CustomCommand : ICommand
    {
        // Command가 실행할 함수
        private Action<object> execute;
        // Command가 실행할 조건이 되었는지 판별하는 함수
        private Predicate<object> canExecute;

        public CustomCommand() { }

        public CustomCommand(Action<object> execute, Predicate<object> canExecute)
        {
            this.execute = execute;
            this.canExecute = canExecute;
        }

        // 기본으로 정의한 canExecute
        public virtual bool CanExecute(object parameter)
        {
            bool b = canExecute == null? true : canExecute(parameter);
            return b;
        }

        public event EventHandler CanExecuteChanged
        {
            add { CommandManager.RequerySuggested += value; }
            remove { CommandManager.RequerySuggested -= value; }
        }

        public virtual void Execute(object parameter)
        {
            execute(parameter);
        }
    }
}
