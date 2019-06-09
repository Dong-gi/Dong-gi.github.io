using SimpleCapture.Utility;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;

namespace SimpleCapture.Command
{
    public class CloseWindowCommand : CustomCommand
    {
        public override void Execute(object o)
        {
            (o as Window).Close();
        }


        public override bool CanExecute(object o)
        {
            return ((o as Window) != null);
        }
    }
}
