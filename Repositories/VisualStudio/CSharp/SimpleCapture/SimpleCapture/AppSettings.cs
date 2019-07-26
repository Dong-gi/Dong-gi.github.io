using System.Collections.Generic;
using System.Windows;

namespace SimpleCapture
{
    public class AppSettings
    {
        public static AppSettings setting;

        //public bool HideWindow { get; set; }
        //public bool HideNoties { get; set; }
        public List<Int32Rect> Rects { get; set; }
        private string _SavePath;
        public string SavePath
        {
            get { return _SavePath; }
            set
            {
                if (value.EndsWith("\\") || value.Length == 0) _SavePath = value;
                else _SavePath = value + "\\";
            }
        }
        public List<string> SaveFormats { get; set; }
        private bool _JPG;
        public bool SaveJPG
        {
            get { return _JPG; }
            set
            {
                SaveFormats.Remove("jpg");
                if (_JPG = value) { SaveFormats.Add("jpg"); }
            }
        }
        private bool _BMP;
        public bool SaveBMP
        {
            get { return _BMP; }
            set
            {
                SaveFormats.Remove("bmp");
                if (_BMP = value) { SaveFormats.Add("bmp"); }
            }
        }
        private bool _PNG;
        public bool SavePNG
        {
            get { return _PNG; }
            set
            {
                SaveFormats.Remove("png");
                if (_PNG = value) { SaveFormats.Add("png"); }
            }
        }
        private bool _GIF;
        public bool SaveGIF
        {
            get { return _GIF; }
            set
            {
                SaveFormats.Remove("gif");
                if (_GIF = value) { SaveFormats.Add("gif"); }
            }
        }

        public static AppSettings Default
        {
            get
            {
                var setting = new AppSettings()
                {
                    //HideWindow = false,
                    //HideNoties = false,
                    Rects = new List<Int32Rect>(),
                    SavePath = "",
                    SaveFormats = new List<string>(),
                    SaveJPG = false,
                    SaveBMP = false,
                    SaveGIF = false,
                    SavePNG = true
                };
                return setting;
            }
        }
    }
}
