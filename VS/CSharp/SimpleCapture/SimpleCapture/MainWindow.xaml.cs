using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using Newtonsoft.Json;
using System.IO;
using SimpleCapture.Utility;

namespace SimpleCapture
{
    /// <summary>
    /// MainWindow.xaml에 대한 상호 작용 논리
    /// </summary>
    public partial class MainWindow : Window
    {
        private static readonly string INI_FILE_PATH = "settings.ini";
        
        public MainWindow()
        {
            InitializeComponent();
            if (!File.Exists(INI_FILE_PATH)) { AppSettings.setting = AppSettings.Default; }
            else { AppSettings.setting = JsonConvert.DeserializeObject<AppSettings>(File.ReadAllText(INI_FILE_PATH)); }
            KeyboardManager.KeyDown += new KeyboardManager.RawKeyEventHandler(KeyboardListener_KeyDown);
            KeyboardManager.StartKeyHook();
            this.DataContext = AppSettings.setting;
        }

        private void KeyboardListener_KeyDown(int keyCode)
        {
            if (KeyboardManager.LeftControlPressed && KeyboardManager.LeftShiftPressed)
            {
                var key = KeyInterop.KeyFromVirtualKey(keyCode).ToString();
                switch (key)
                {
                    case "C": ImageUtility.CaptureFreeArea(this); break;
                    case "A": ImageUtility.CaptureAllOptions(this); break;
                    case "S": ImageUtility.CaptureFullScreen(this); break;
                    case "F": ControlSavedRectangles(); break;
                    case "R": ControlRecords(); break;
                }
            }
        }

        /// <summary>
        /// 저장된 사각형 영역들을 관리합니다.
        /// </summary>
        private void ControlSavedRectangles()
        {
            new ControlSavedRectangleAreaWindow().Show();
            this.WindowState = WindowState.Minimized;
        }

        /// <summary>
        /// 녹화 상황들을 관리합니다.
        /// </summary>
        private void ControlRecords()
        {
            ControlRecordWindow.Single.Visibility = Visibility.Visible;
            ControlRecordWindow.Single.Show();
            this.WindowState = WindowState.Minimized;
        }

        private void Window_Closed(object sender, EventArgs e)
        {
            ControlRecordWindow.Single.IsClosing = true;
            ControlRecordWindow.Single.Close();
            KeyboardManager.StopKeyHook();
            StreamWriter writer = new StreamWriter(INI_FILE_PATH);
            writer.Write(JsonConvert.SerializeObject(AppSettings.setting));
            writer.Close();
        }

        private void Window_ContentRendered(object sender, EventArgs e)
        {
            //Test.Test.Test_AVIWriter();
        }

        private void Border_MouseEnter(object sender, MouseEventArgs e)
        {
            Border border = sender as Border;
            border.BorderBrush = System.Windows.Media.Brushes.Black;
            // I tried to bind Label_Detail.Content with setting.Detail.
            // But after setting.Detail first assigned, Label_Detail doesn't changed. :(
            if (border.Equals(Border_FreeArea)) Label_Detail.Content = "Capture a selected rectangle area of the screen (Ctrl + Shift + C)";
            else if (border.Equals(Border_FixedArea)) Label_Detail.Content = "Capture multiple rectangle area of the screen (Ctrl + Shift + F)";
            else if (border.Equals(Border_FullSize)) Label_Detail.Content = "Capture a full screen (Ctrl + Shift + S)";
            else if (border.Equals(Border_Record)) Label_Detail.Content = "Record rectangle areas of the screen (Ctrl + Shift + R)";
        }

        private void Border_MouseLeave(object sender, MouseEventArgs e)
        {
            Border border = sender as Border;
            border.BorderBrush = System.Windows.Media.Brushes.Transparent;
            Label_Detail.Content = "If you want auto captures, press (Ctrl + Shift + A)";
        }

        private void Border_Click(object sender, MouseButtonEventArgs e)
        {
            Border border = sender as Border;
            if (border.Equals(Border_FreeArea)) ImageUtility.CaptureFreeArea(this);
            else if (border.Equals(Border_FixedArea)) ControlSavedRectangles();
            else if (border.Equals(Border_FullSize)) ImageUtility.CaptureFullScreen(this);
            else if (border.Equals(Border_Record)) ControlRecords();
        }

        private void Button_Click(object sender, RoutedEventArgs e)
        {
            using (var dialog = new System.Windows.Forms.FolderBrowserDialog())
            {
                Label_Detail.Content = "Current Save Path : " + AppSettings.setting.SavePath;
                dialog.SelectedPath = AppSettings.setting.SavePath;
                System.Windows.Forms.DialogResult result = dialog.ShowDialog();
                AppSettings.setting.SavePath = dialog.SelectedPath;
                Label_Detail.Content = "Current Save Path : " + AppSettings.setting.SavePath;
            }
        }
    }
    
}
