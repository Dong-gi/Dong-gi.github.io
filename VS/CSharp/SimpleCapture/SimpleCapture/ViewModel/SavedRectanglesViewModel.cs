using SimpleCapture.Utility;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Input;

namespace SimpleCapture.ViewModel
{
    public class SavedRectanglesViewModel
    {
        public class RectWithIndex
        {
            public int Index { get; set; }
            public Int32Rect Rect { get; set; }
            public string LeftTop
            {
                get
                {
                    return "(" + Rect.X + ", " + Rect.Y + ")";
                }
            }
            public string WidthHeight
            {
                get
                {
                    return "(" + Rect.Width + " x " + Rect.Height + ")";
                }
            }
        }
        public ObservableCollection<RectWithIndex> Items { get; set; }
        private int count = 0;
        public ICommand AddCommand { get; set; }
        public ICommand CaptureCommand { get; set; }
        public ICommand RemoveCommand { get; set; }
        public ICommand CloseCommand { get; set; }

        public SavedRectanglesViewModel()
        {
            Items = new ObservableCollection<RectWithIndex>();
            foreach (var rect in AppSettings.setting.Rects)
            {
                Items.Add(new RectWithIndex()
                {
                    Index = ++count,
                    Rect = rect
                });
            }
            AddCommand = new CustomCommand(Add, (object o) => true);
            CaptureCommand = new CustomCommand(Capture, (object o) => true);
            RemoveCommand = new CustomCommand(Remove, (object o) => true);
            CloseCommand = new CustomCommand(Close, (object o) => true);
        }

        private void Add(object o)
        {
            var w = new SelectRectangleCaptureAreaWindow();
            w.RectangleSelected += (Int32Rect rect) =>
            {
                Items.Add(new RectWithIndex()
                {
                    Index = ++count,
                    Rect = rect
                });
            };

            var window = o as Window;
            window.Visibility = Visibility.Hidden;
            w.Finished += () => window.Visibility = Visibility.Visible;

            w.Show();
        }

        private void Capture(object o)
        {
            var window = o as ControlSavedRectangleAreaWindow;
            var listView = window.List_Rects;
            if (listView.SelectedItems.Count > 0)
            {
                var screen = ImageUtility.CaptureFullScreen(window);
                foreach (var item in listView.SelectedItems)
                {
                    var rect = ((RectWithIndex)item).Rect;
                    ImageUtility.CaptureSelectedFrame(rect, screen);
                }
            }
        }

        private void Remove(object o)
        {
            var window = o as ControlSavedRectangleAreaWindow;
            var listView = window.List_Rects;
            if (listView.SelectedItems.Count > 0)
            {
                var arr = new RectWithIndex[listView.SelectedItems.Count];
                listView.SelectedItems.CopyTo(arr, 0);
                foreach (var item in arr)
                {
                    Items.Remove(item);
                }
            }
        }

        private void Close(object o)
        {
            var window = o as ControlSavedRectangleAreaWindow;
            var listView = window.List_Rects;
            var rects = new List<Int32Rect>();
            if (listView.Items.Count > 0)
            {
                foreach (var item in listView.Items)
                {
                    rects.Add(((RectWithIndex)item).Rect);
                }
            }
            AppSettings.setting.Rects = rects;
        }

    }
}
