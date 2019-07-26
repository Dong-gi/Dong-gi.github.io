using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;

namespace SimpleCapture.Utility
{
    /// <summary>
    /// SelectRectangleCaptureAreaWindow.xaml에 대한 상호 작용 논리
    /// </summary>
    public partial class SelectRectangleCaptureAreaWindow : Window
    {
        private Point startPoint;
        private Rectangle Rect { get; set; }
        private bool Cancel { get; set; }

        public SelectRectangleCaptureAreaWindow()
        {
            InitializeComponent();
            Rect = null;
            KeyboardManager.DisableSystemKeys = true;
            KeyboardManager.KeyUp += KeyboardManager_KeyUp;
        }

        private void KeyboardManager_KeyUp(int keyCode)
        {
            // ESC
            if(keyCode == 27)
            {
                Cancel = true;
                this.Close();
            }
        }

        public delegate void RectangleSelectedHandler(Int32Rect rect);
        public event RectangleSelectedHandler RectangleSelected = delegate { };
        public delegate void SelectionFinishedHandler();
        public event SelectionFinishedHandler Finished = delegate { };

        private void Canvas_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.LeftButton == MouseButtonState.Pressed && Rect == null)
            {
                startPoint = e.GetPosition(canvas);

                Rect = new Rectangle
                {
                    Stroke = Brushes.Red,
                    StrokeThickness = 5,
                    Fill = Brushes.Black
                };
                Canvas.SetLeft(Rect, startPoint.X);
                Canvas.SetTop(Rect, startPoint.Y);
                canvas.Children.Add(Rect);
            }
        }

        private void Canvas_MouseMove(object sender, MouseEventArgs e)
        {
            if (e.LeftButton == MouseButtonState.Released || Rect == null)
                return;
            if (e.LeftButton == MouseButtonState.Pressed)
            {
                var pos = e.GetPosition(canvas);

                var x = Math.Min(pos.X, startPoint.X);
                var y = Math.Min(pos.Y, startPoint.Y);

                var w = Math.Max(pos.X, startPoint.X) - x;
                var h = Math.Max(pos.Y, startPoint.Y) - y;

                Rect.Width = w;
                Rect.Height = h;

                Canvas.SetLeft(Rect, x);
                Canvas.SetTop(Rect, y);
            }
        }

        private void Canvas_MouseUp(object sender, MouseButtonEventArgs e)
        {
            if (e.LeftButton == MouseButtonState.Released && Rect != null)
            {
                this.Close();
            }
        }

        private void Window_Closed(object sender, EventArgs e)
        {
            if (!Cancel)
            {
                RectangleSelected.Invoke(new Int32Rect(
                    (int)Canvas.GetLeft(Rect), (int)Canvas.GetTop(Rect), (int)Rect.Width, (int)Rect.Height));
            }
            Finished.Invoke();
            KeyboardManager.DisableSystemKeys = false;
        }
    }
}
