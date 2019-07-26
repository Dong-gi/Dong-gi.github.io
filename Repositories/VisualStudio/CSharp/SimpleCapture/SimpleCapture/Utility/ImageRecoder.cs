using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace SimpleCapture.Utility
{
    /// <summary>
    /// ImageUtility를 이용하여 주기적(30fps)으로 화면 프레임을 생성합니다.
    /// AVIWriter를 이용하여 생성한 프레임을 동영상에 추가합니다.
    /// </summary>
    public class ImageRecoder
    {
        #region static field
        private delegate void ScreenCaptured(BitmapSource source);
        private static event ScreenCaptured FrameCreated = delegate { };
        private static Dictionary<int, ImageRecoder> workers = new Dictionary<int, ImageRecoder>();
        
        private static readonly object LOCK = new object();
        private static bool isWorking = false;
        #endregion

        private ScreenCaptured ScreenCaptureHandler { get; set; }
        public int Index { get; private set; }
        public Int32Rect Rect { get; private set; }
        public States State { get; set; }
        public enum States { RECODING, PAUSED }

        public static ImageRecoder Recode(Int32Rect rect, string filePath = "")
        {
            lock (LOCK)
            {
                int index = AVIWriter.GetWriter(filePath, rect.Width, rect.Height);
                workers[index] = new ImageRecoder()
                {
                    ScreenCaptureHandler = (BitmapSource source) =>
                    {
                        AVIWriter.WriteFrame(index, ImageUtility.ByteArrayFromBitmapSource(ImageUtility.CutBitmap(source, rect)));
                    },
                    Index = index,
                    Rect = rect,
                    State = States.RECODING
                };
                FrameCreated += workers[index].ScreenCaptureHandler;
                
                if (!isWorking)
                {
                    isWorking = true;
                    var timer = new DispatcherTimer();
                    timer.Interval = TimeSpan.FromMilliseconds(1000 / 30);
                    timer.Tick += (object sender, EventArgs e) =>
                    {
                        var frame = ImageUtility.CopyScreen();
                        FrameCreated.Invoke(frame);
                    };
                    timer.Start();
                }
                return workers[index];
            }
        }

        public static ImageRecoder GetEmptyRecoder()
        {
            return new ImageRecoder()
            {
                Index = 1,
                Rect = new Int32Rect(1, 2, 3, 4),
                State = States.PAUSED
            };
        }

        public static List<int> Indexes()
        {
            return workers.Keys.ToList<int>();
        }

        public static void Pause(int index)
        {
            lock (LOCK)
            {
                FrameCreated -= workers[index].ScreenCaptureHandler;
                workers[index].State = States.PAUSED;
            }
        }

        public static void ReStart(int index)
        {
            lock (LOCK)
            {
                FrameCreated += workers[index].ScreenCaptureHandler;
                workers[index].State = States.RECODING;
            }
        }

        public static void Stop(int index)
        {
            lock (LOCK)
            {
                Pause(index);
                AVIWriter.Close(index);
                workers.Remove(index);
            }
        }
    }
}
