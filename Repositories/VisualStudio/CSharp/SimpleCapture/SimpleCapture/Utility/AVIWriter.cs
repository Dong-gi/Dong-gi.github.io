using System;
using System.Collections.Generic;
using SharpAvi.Output;
using SharpAvi.Codecs;

namespace SimpleCapture.Utility
{
    public class AVIWriter
    {
        private static List<AviWriter> writerList = new List<AviWriter>();
        private static List<IAviVideoStream> videoStreamList = new List<IAviVideoStream>();

        /// <summary>
        /// list에 AviWriter를 추가하고, 인덱스를 반환합니다.
        /// </summary>
        /// <param name="filePath">Video's save path</param>
        /// <param name="width">Video's width</param>
        /// <param name="height">Video's height</param>
        /// <param name="fps">Video's frames per second</param>
        /// <returns>생성한 AviWriter의 인덱스</returns>
        public static int GetWriter(string filePath, int width, int height, int fps = 30)
        {
            var writer = new AviWriter( filePath + string.Format("{0}{1}{2}_{3}{4}{5}_{6}.avi",
                DateTime.Now.Year, DateTime.Now.Month, DateTime.Now.Day,
                DateTime.Now.Hour, DateTime.Now.Minute, DateTime.Now.Second, DateTime.Now.Millisecond))
            {
                FramesPerSecond = fps,
                EmitIndex1 = true
            };
            writerList.Add(writer);
            var encoder = new MotionJpegVideoEncoderWpf(width, height, 70);
            var stream = writer.AddEncodingVideoStream(encoder, width: width, height: height);
            videoStreamList.Add(stream);
            
            return writerList.Count - 1;
        }

        /// <summary>
        /// idx에 해당하는 스트림에 프레임을 추가합니다.
        /// </summary>
        /// <param name="idx">추가된 AviWriter의 순번</param>
        /// <param name="data">프레임의 바이트 배열 데이터</param>
        public static void WriteFrame(int idx, byte[] data)
        {
            videoStreamList[idx].WriteFrame(true, data, 0, data.Length);
        }

        public static void Close(int idx)
        {
            writerList[idx].Close();
        }
    }
}
