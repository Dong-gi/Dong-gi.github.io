using SimpleCapture.Utility;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Input;

namespace SimpleCapture.ViewModel
{
    public class ImageRecoderViewModel
    {
        public class RecoderWithIndex
        {
            public ImageRecoder Recoder { get; set; }
            public string Size
            {
                get
                {
                    return "(" + Recoder.Rect.Width + " x " + Recoder.Rect.Height + ")";
                }
            }
            public string State
            {
                get
                {
                    switch (Recoder.State)
                    {
                        case ImageRecoder.States.RECODING: return "RECODING";
                        default: return "PAUSED";
                    }
                }
            }
        }
        public ObservableCollection<RecoderWithIndex> Items { get; set; }
        public ICommand NewCommand { get; set; }
        public ICommand PauseOrRestartCommand { get; set; }
        public ICommand StopCommand { get; set; }
        public ICommand CloseCommand { get; set; }

        public ImageRecoderViewModel()
        {
            Items = new ObservableCollection<RecoderWithIndex>();
            NewCommand = new CustomCommand(New, (object o) => true);
            PauseOrRestartCommand = new CustomCommand(PauseOrRestart, (object o) => true);
            StopCommand = new CustomCommand(Stop, (object o) => true);
            CloseCommand = new CustomCommand(Close, (object o) => true);
        }

        private void New(object o)
        {
            var w = new SelectRectangleCaptureAreaWindow();
            w.RectangleSelected += (Int32Rect rect) =>
            {
                Items.Add(new RecoderWithIndex()
                {
                    Recoder = ImageRecoder.Recode(rect, AppSettings.setting.SavePath)
                });
            };

            var window = o as Window;
            window.Visibility = Visibility.Hidden;
            w.Finished += () => window.Visibility = Visibility.Visible;

            w.Show();
        }

        private void PauseOrRestart(object o)
        {
            var window = o as ControlRecordWindow;
            var listView = window.List_Records;
            if (listView.SelectedItems.Count > 0)
            {
                foreach (var item in listView.SelectedItems)
                {
                    var recoder = (item as RecoderWithIndex).Recoder;
                    if (recoder.State == ImageRecoder.States.PAUSED)
                        ImageRecoder.ReStart(recoder.Index);
                    else
                        ImageRecoder.Pause(recoder.Index);
                }
            }
        }

        private void Stop(object o)
        {
            var window = o as ControlRecordWindow;
            var listView = window.List_Records;
            if (listView.SelectedItems.Count > 0)
            {
                var arr = new RecoderWithIndex[listView.SelectedItems.Count];
                listView.SelectedItems.CopyTo(arr, 0);
                foreach (var recoder in arr)
                {
                    ImageRecoder.Stop(recoder.Recoder.Index);
                    Items.Remove(recoder);
                }
            }
        }

        private void Close(object o)
        {
            var window = o as ControlRecordWindow;
            var listView = window.List_Records;
            if (listView.Items.Count > 0)
            {
                var arr = new RecoderWithIndex[listView.SelectedItems.Count];
                listView.Items.CopyTo(arr, 0);
                foreach (var recoder in arr)
                {
                    ImageRecoder.Stop(recoder.Recoder.Index);
                    Items.Remove(recoder);
                }
            }
        }
    }
}
