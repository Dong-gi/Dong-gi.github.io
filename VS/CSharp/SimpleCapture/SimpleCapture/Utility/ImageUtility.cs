using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media.Imaging;

namespace SimpleCapture.Utility
{
    public class ImageUtility
    {
        // Bitmap needs "System.Drawing.dll"

        #region Common Functions
        /// <summary>
        /// 전체 화면을 캡처하여 반환합니다.
        /// </summary>
        /// <returns>전체 화면 정보를 갖는 BitmapSource</returns>
        public static BitmapSource CopyScreen()
        {
            using (var screenBmp = new Bitmap(
                (int)SystemParameters.PrimaryScreenWidth,
                (int)SystemParameters.PrimaryScreenHeight,
                System.Drawing.Imaging.PixelFormat.Format32bppArgb))
            {
                using (var bmpGraphics = Graphics.FromImage(screenBmp))
                {
                    bmpGraphics.CopyFromScreen(0, 0, 0, 0, screenBmp.Size);
                    var result = BitmapSourceFromBitmap(screenBmp);
                    result.Freeze();
                    return result;
                }
            }
        }

        /// <summary>
        /// source 이미지의 rect 영역을 복사한 새로운 BitmapSource를 반환합니다.
        /// rect 영역은 source 내부의 상대적인 위치를 기준으로 합니다.
        /// </summary>
        /// <param name="source">자를 이미지 원본</param>
        /// <param name="rect">자를 위치와 크기</param>
        /// <returns></returns>
        public static BitmapSource CutBitmap(BitmapSource source, Int32Rect rect)
        {
            var cutted = new WriteableBitmap(rect.Width, rect.Height, source.DpiX, source.DpiY, source.Format, source.Palette);

            int bytesPerPixel = ((source.Format.BitsPerPixel + 7) / 8);
            int originalStride = source.PixelWidth * bytesPerPixel;
            int cuttedStride = cutted.PixelWidth * bytesPerPixel;
            int originalLength = originalStride * source.PixelHeight;
            int cuttedLength = cuttedStride * cutted.PixelHeight;

            byte[] originalPixels = new byte[originalLength];
            source.CopyPixels(originalPixels, originalStride, 0);

            byte[] cuttedPixels = new byte[cuttedLength];
            for (int i = 0; i < cutted.PixelHeight; ++i)
            {
                int cutR = i * cuttedStride;
                int oriR = (i + rect.Y) * originalStride;
                int oriC = rect.X * bytesPerPixel;
                int length = cutted.PixelWidth * bytesPerPixel;
                for (int j = 0; j < length; ++j)
                {
                    cuttedPixels[cutR + j] = originalPixels[oriR + oriC + j];
                }
            }
            cutted.WritePixels(new Int32Rect(0, 0, rect.Width, rect.Height), cuttedPixels, cuttedStride, 0);
            cutted.Freeze();
            return cutted;
        }

        [System.Runtime.InteropServices.DllImport("gdi32.dll")]
        public static extern bool DeleteObject(IntPtr hObject);

        public static BitmapSource BitmapSourceFromBitmap(Bitmap source)
        {
            var result = Imaging.CreateBitmapSourceFromHBitmap(
                          source.GetHbitmap(),
                          IntPtr.Zero,
                          Int32Rect.Empty,
                          BitmapSizeOptions.FromEmptyOptions());
            DeleteObject(source.GetHbitmap());
            return result;
        }

        public static Bitmap BitmapFromBitmapSource(BitmapSource source)
        {
            source.Freeze();
            Bitmap bitmap;
            using (var outStream = new MemoryStream())
            {
                BitmapEncoder enc = new BmpBitmapEncoder();
                enc.Frames.Add(BitmapFrame.Create(source));
                enc.Save(outStream);
                bitmap = new Bitmap(outStream);
                outStream.Close();
            }
            return bitmap;
        }

        public static byte[] ByteArrayFromBitmapSource(BitmapSource source)
        {
            source.Freeze();
            int bytesPerPixel = ((source.Format.BitsPerPixel + 7) / 8);
            int stride = source.PixelWidth * bytesPerPixel;
            int length = stride * source.PixelHeight;

            byte[] pixels = new byte[length];
            source.CopyPixels(pixels, stride, 0);
            return pixels;
        }

        public static void Save(BitmapSource source, string fileName, ImageFormat format)
        {
            using (var fileStream = new FileStream(fileName, FileMode.Create))
            {
                BitmapEncoder encoder;
                if (format.Equals(ImageFormat.Gif))
                    encoder = new GifBitmapEncoder();
                else if (format.Equals(ImageFormat.Png))
                    encoder = new PngBitmapEncoder();
                else if (format.Equals(ImageFormat.Jpeg))
                    encoder = new JpegBitmapEncoder();
                else
                    encoder = new BmpBitmapEncoder();
                encoder.Frames.Add(BitmapFrame.Create(source));
                encoder.Save(fileStream);
                fileStream.Close();
            }
        }

        public static void Save(BitmapSource source, string path, string format)
        {
            var fileName = path + string.Format("{0}{1}{2}_{3}{4}{5}_{6}.{7}",
                DateTime.Now.Year, DateTime.Now.Month, DateTime.Now.Day,
                DateTime.Now.Hour, DateTime.Now.Minute, DateTime.Now.Second, DateTime.Now.Millisecond, format);
            switch (format.ToLower())
            {
                case "gif": Save(source, fileName, ImageFormat.Gif); break;
                case "png": Save(source, fileName, ImageFormat.Png); break;
                case "jpg": case "jpeg": Save(source, fileName, ImageFormat.Jpeg); break;
                case "bmp": Save(source, fileName, ImageFormat.Bmp); break;
            }
        }

        public static void SetClipboardImage(BitmapSource source)
        {
            Clipboard.SetImage(source);
        }
        #endregion

        #region Uncommon Functions : just for this app
        /// <summary>
        /// 지정된 영역을 캡처합니다.
        /// </summary>
        /// <param name="rect">source를 자를 사각형 위치와 크기</param>
        /// <param name="source">rect로 자를 영역. 기본으로 전체 스크린이 들어갑니다.</param>
        public static void CaptureSelectedFrame(Int32Rect rect, BitmapSource source = null)
        {
            if (source == null) source = ImageUtility.CopyScreen();
            var image = ImageUtility.CutBitmap(source, rect);
            foreach (var format in AppSettings.setting.SaveFormats)
            {
                ImageUtility.Save(image, AppSettings.setting.SavePath, format);
            }
            SetClipboardImage(image);
        }

        /// <summary>
        /// 화면 전체 영역을 캡처합니다.
        /// </summary>
        public static BitmapSource CaptureFullScreen(Window minimizeWindow = null)
        {
            if (minimizeWindow != null)
            {
                minimizeWindow.WindowState = WindowState.Minimized;
                System.Threading.Thread.Sleep(200);
            }
            var image = ImageUtility.CopyScreen();
            foreach (var format in AppSettings.setting.SaveFormats)
            {
                ImageUtility.Save(image, AppSettings.setting.SavePath, format);
            }
            if (minimizeWindow != null) minimizeWindow.WindowState = WindowState.Normal;
            SetClipboardImage(image);
            return image;
        }

        /// <summary>
        /// 저장된 모든 설정으로 캡처합니다.
        /// </summary>
        public static void CaptureAllOptions(Window minimizeWindow = null)
        {
            var image = CaptureFullScreen(minimizeWindow);
            foreach (var rect in AppSettings.setting.Rects)
            {
                CaptureSelectedFrame(rect, image);
            }
        }

        /// <summary>
        /// 자유 사각형 영역을 캡처합니다.
        /// </summary>
        public static void CaptureFreeArea(Window hideWindow = null)
        {
            var w = new SelectRectangleCaptureAreaWindow();
            w.RectangleSelected += (Int32Rect rect) =>
            {
                CaptureSelectedFrame(rect);
            };
            if (hideWindow != null)
            {
                hideWindow.Visibility = Visibility.Hidden;
                w.Finished += () => hideWindow.Visibility = Visibility.Visible;
            }
            w.Show();
        }
        #endregion
    }
}
