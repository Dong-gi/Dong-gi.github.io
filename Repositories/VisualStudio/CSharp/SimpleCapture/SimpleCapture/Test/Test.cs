using SimpleCapture.Utility;
using System;
using System.Collections.Generic;
using System.Drawing.Imaging;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Threading;

namespace SimpleCapture.Test
{
    public class Test
    {
        #region Test ImageUtility
        public static void Test_CopyScreen()
        {
            TestWindow window = new TestWindow()
            {
                Title = "Test_CopyScreen"
            };
            window.grid.Children.Add(new System.Windows.Controls.Image()
            {
                Source = ImageUtility.CopyScreen()
            });
            window.Show();
        }
        public static void Test_CutBitmap()
        {
            TestWindow window = new TestWindow()
            {
                Title = "Test_CutBitmap"
            };
            window.grid.Children.Add(new System.Windows.Controls.Image()
            {
                Source = ImageUtility.CutBitmap(ImageUtility.CopyScreen(), new Int32Rect(100, 200, 100, 200))
            });
            window.Show();
        }
        public static void Test_BitmapFromBitmapSource()
        {
            TestWindow window = new TestWindow()
            {
                Title = "Test_BitmapFromBitmapSource"
            };
            window.grid.Children.Add(new System.Windows.Controls.Image()
            {
                Source = ImageUtility.BitmapSourceFromBitmap(
                    ImageUtility.BitmapFromBitmapSource(ImageUtility.CopyScreen()))
            });
            window.Show();
        }
        public static void Test_SetClipboardImage()
        {
            ImageUtility.SetClipboardImage(ImageUtility.CopyScreen());
        }
        public static void Test_Save()
        {
            var image = ImageUtility.CopyScreen();
            ImageUtility.Save(image, "test.bmp", ImageFormat.Bmp);
            ImageUtility.Save(image, "test.gif", ImageFormat.Gif);
            ImageUtility.Save(image, "test.jpg", ImageFormat.Jpeg);
            ImageUtility.Save(image, "test.png", ImageFormat.Png);
        }
        #endregion
        #region Test AVIWriter
        public static void Test_AVIWriter()
        {
            var screen = ImageUtility.CopyScreen();
            var idx = AVIWriter.GetWriter("", screen.PixelWidth, screen.PixelHeight);
            int count = 0;
            var timer = new DispatcherTimer();
            timer.Interval = TimeSpan.FromMilliseconds(1000/30);
            timer.Tick += (object sender, EventArgs e) => {
                AVIWriter.WriteFrame(idx, ImageUtility.ByteArrayFromBitmapSource(ImageUtility.CopyScreen()));
                if(count++ > 300)
                {
                    timer.Stop();
                    AVIWriter.Close(idx);
                }
            };
            timer.Start();
        }
        #endregion
    }
}
