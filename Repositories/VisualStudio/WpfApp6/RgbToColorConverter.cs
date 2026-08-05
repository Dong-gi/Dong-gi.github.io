using System;
using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;

namespace WpfApp6
{
    public class RgbToColorConverter : IMultiValueConverter
    {
        public object? Convert(object[] value, Type typeTarget, object param, CultureInfo culture)
        {
            var color = Color.FromRgb((byte)(double)value[0], (byte)(double)value[1], (byte)(double)value[2]);
            if (typeof(Color).Equals(typeTarget))
                return color;
            return typeof(Brush).Equals(typeTarget) ? new SolidColorBrush(color) : null;
        }

        public object[]? ConvertBack(object value, Type[] typeTarget, object param, CultureInfo culture)
        {
            if (value is Color color)
                return new object[] { color.R, color.G, color.B };

            if (value is SolidColorBrush solidColorBrush)
                return new object[] { solidColorBrush.Color.R, solidColorBrush.Color.G, solidColorBrush.Color.B };

            return null;
        }
    }
}
