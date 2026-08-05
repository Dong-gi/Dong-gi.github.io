using System.Windows;

namespace WpfApp2
{
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
        }
        private void Button_Click(object sender, RoutedEventArgs e)
        {
            new Window()
            {
                Title = "Modal",
                Owner = this,
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                ShowInTaskbar = true,
                Height = 60,
                MaxWidth = 300
            }.ShowDialog();
        }
        private void Button_Click_1(object sender, RoutedEventArgs e)
        {
            new Window()
            {
                Title = "Modeless",
                Height = 60,
                MaxWidth = 300
            }.Show();
        }
    }
}
