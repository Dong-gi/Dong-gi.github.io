using SimpleCapture.Utility;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;

namespace SimpleCapture.Command
{
    public class ActivateWindowCommand : CustomCommand
    {
        public override void Execute(object o)
        {
            var w = o as Window;
            w.Visibility = Visibility.Visible;
            w.Activate();
        }


        public override bool CanExecute(object o)
        {
            return ((o as Window) != null);
        }
    }
}
