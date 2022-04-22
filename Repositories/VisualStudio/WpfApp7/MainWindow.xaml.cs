using Hardcodet.Wpf.TaskbarNotification;
using System.Windows;

namespace WpfApp7
{
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
        }

        private void Button_Click(object sender, RoutedEventArgs e)
        {
            new TaskbarIcon().ShowBalloonTip("Hello", "World", BalloonIcon.Info);
        }
    }
}
