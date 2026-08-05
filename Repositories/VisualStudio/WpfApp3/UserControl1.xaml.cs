using System.Windows.Controls;
using System.Windows.Input;

namespace WpfApp3
{
    public partial class UserControl1 : UserControl
    {
        public UserControl1()
        {
            InitializeComponent();
        }
        private void Label_MouseUp(object sender, MouseButtonEventArgs e)
        {
            if (sender is Label label)
                label.Content = "Oops!";
        }
    }
}
