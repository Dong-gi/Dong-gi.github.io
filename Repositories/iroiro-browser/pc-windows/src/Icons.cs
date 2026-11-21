// 트레이·창 아이콘 — 코드로 그린다. 색만이 아니라 모양도 다르다(꺼짐: 빈 고리, 연결 중: 반 고리, 연결됨: 찬 원, 오류: 느낌표).
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;

namespace WebTether
{
    public static class Icons
    {
        [DllImport("user32.dll")] static extern bool DestroyIcon(IntPtr h);

        static readonly Dictionary<string, Icon> cache = new Dictionary<string, Icon>();

        public static Color ColorOf(TetherState s)
        {
            switch (s)
            {
                case TetherState.Connected: return Color.FromArgb(0x1E, 0x8E, 0x3E);
                case TetherState.Connecting:
                case TetherState.Waiting: return Color.FromArgb(0xE3, 0x8B, 0x00);
                case TetherState.Error: return Color.FromArgb(0xC6, 0x28, 0x28);
                default: return Color.FromArgb(0x75, 0x75, 0x75);
            }
        }

        public static Bitmap Draw(TetherState s, int size)
        {
            var bmp = new Bitmap(size, size);
            using (var g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.Clear(Color.Transparent);
                var c = ColorOf(s);
                float w = Math.Max(1.5f, size / 8f);
                var r = new RectangleF(w / 2 + 0.5f, w / 2 + 0.5f, size - w - 1, size - w - 1);
                using (var pen = new Pen(c, w))
                using (var brush = new SolidBrush(c))
                {
                    switch (s)
                    {
                        case TetherState.Connected:
                            g.FillEllipse(brush, r);
                            using (var white = new Pen(Color.White, w))
                            {
                                // 체크 표시
                                g.DrawLines(white, new[] {
                                    new PointF(size * 0.28f, size * 0.52f), new PointF(size * 0.44f, size * 0.68f), new PointF(size * 0.72f, size * 0.36f) });
                            }
                            break;
                        case TetherState.Connecting:
                        case TetherState.Waiting:
                            using (var faint = new Pen(Color.FromArgb(90, c), w)) g.DrawEllipse(faint, r);
                            g.DrawArc(pen, r, -90, 180);
                            g.FillEllipse(brush, size * 0.4f, size * 0.4f, size * 0.2f, size * 0.2f);
                            break;
                        case TetherState.Error:
                            g.FillEllipse(brush, r);
                            using (var white = new SolidBrush(Color.White))
                            {
                                g.FillRectangle(white, size * 0.44f, size * 0.22f, size * 0.12f, size * 0.36f);
                                g.FillRectangle(white, size * 0.44f, size * 0.66f, size * 0.12f, size * 0.12f);
                            }
                            break;
                        default:
                            g.DrawEllipse(pen, r);
                            break;
                    }
                }
            }
            return bmp;
        }

        public static Icon For(TetherState s, int size)
        {
            var key = s + "/" + size;
            Icon ic;
            if (cache.TryGetValue(key, out ic)) return ic;
            using (var bmp = Draw(s, size))
            {
                var h = bmp.GetHicon();
                ic = (Icon)Icon.FromHandle(h).Clone();
                DestroyIcon(h);
            }
            cache[key] = ic;
            return ic;
        }
    }
}
