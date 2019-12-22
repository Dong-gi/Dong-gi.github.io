using System;
using System.Runtime.InteropServices;
using System.Collections.Generic;
using System.Windows.Threading;
using System.Windows;

namespace SimpleCapture.Utility
{
    sealed class KeyboardManager
    {
        #region Private Members

        private delegate IntPtr HookHandlerDelegate(int nCode, IntPtr wParam, ref KBHookStruct lParam);
        private static HookHandlerDelegate callbackPtr;
        private static IntPtr hookPtr = IntPtr.Zero;
        private const int LowLevelKeyboardHook = 13;
        private const int WM_KEYDOWN = 0x0100;
        private const int WM_KEYUP = 0x0101;

        private static List<int[]> keyList = new List<int[]>();
        /// <summary>
        /// 다른 프로그램의 윈도우가 받을 광역 키보드 이벤트를 받아온 경우,
        /// 빠르게 처리한 뒤 해당 윈도우가 처리하도록 넘겨줘야 합니다.
        /// 따라서, 여기에서는 받아온 키들을 keyList에 저장하고,
        /// timer를 따라서 사용자가 등록한 이벤트를 처리하도록 했습니다.
        /// </summary>
        private static DispatcherTimer timer = new DispatcherTimer();

        [DllImport("user32.dll", SetLastError = true)]
        private static extern IntPtr SetWindowsHookEx(int idHook, HookHandlerDelegate callbackPtr, IntPtr hInstance, uint dwThreadId);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool UnhookWindowsHookEx(IntPtr hhk);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern IntPtr CallNextHookEx(IntPtr hhk, int nCode, IntPtr wParam, ref KBHookStruct lParam);

        #endregion

        /// <summary>
        /// 유저는 이 이벤트핸들러를 이용하여 광역 키보드 이벤트를 처리합니다.
        /// </summary>
        /// <param name="keyCode">키값</param>
        public delegate void RawKeyEventHandler(int keyCode);
        // 이벤트에 빈 델리게이트 리스트를 할당하면 null을 검사하지 않아도 됨.
        public static event RawKeyEventHandler KeyDown = delegate { };
        public static event RawKeyEventHandler KeyUp = delegate { };

        public static bool DisableSystemKeys { get; set; }
        public static bool LeftControlPressed { get; private set; }
        public static bool LeftShiftPressed { get; private set; }

        public static void StartKeyHook()
        {
            if (callbackPtr == null)
            {
                callbackPtr = new HookHandlerDelegate(KeyboardHookHandler);
            }

            if (hookPtr == IntPtr.Zero)
            {
                // Note: This does not work in the VS host environment.  To run in debug mode:
                // Project -> Properties -> Debug -> Uncheck "Enable the Visual Studio hosting process"
                IntPtr hInstance = Marshal.GetHINSTANCE(Application.Current.GetType().Module);
                hookPtr = SetWindowsHookEx(LowLevelKeyboardHook, callbackPtr, hInstance, 0);
                DisableSystemKeys = false;

                timer.Interval = TimeSpan.FromMilliseconds(100);
                timer.Tick += (object sender, EventArgs e) =>
                {
                    while (keyList.Count > 0)
                    {
                        var type = keyList[0][0];
                        var keyCode = keyList[0][1];
                        if (type == WM_KEYDOWN)
                        {
                            switch (keyCode)
                            {
                                case 162: LeftControlPressed = true; break;
                                case 160: LeftShiftPressed = true; break;
                            }
                            KeyDown.Invoke(keyCode);
                        }
                        else if (type == WM_KEYUP)
                        {
                            switch (keyCode)
                            {
                                case 162: LeftControlPressed = false; break;
                                case 160: LeftShiftPressed = false; break;
                            }
                            KeyUp.Invoke(keyCode);
                        }
                        keyList.RemoveAt(0);
                    }
                };
                timer.Start();
            }

        }

        public static void StopKeyHook()
        {
            if (hookPtr != IntPtr.Zero)
            {
                UnhookWindowsHookEx(hookPtr);
                hookPtr = IntPtr.Zero;
                timer.Stop();
            }
        }

        private static IntPtr KeyboardHookHandler(int nCode, IntPtr wParam, ref KBHookStruct lParam)
        {
            if (nCode == 0)
            {
                if (DisableSystemKeys)
                {
                    if (((lParam.vkCode == 0x09) && (lParam.flags == 0x20)) ||  // Alt+Tab
                    ((lParam.vkCode == 0x1B) && (lParam.flags == 0x20)) ||      // Alt+Esc
                    ((lParam.vkCode == 0x1B) && (lParam.flags == 0x00)) ||      // Ctrl+Esc
                    ((lParam.vkCode == 0x5B) && (lParam.flags == 0x01)) ||      // Left Windows Key
                    ((lParam.vkCode == 0x5C) && (lParam.flags == 0x01)) ||      // Right Windows Key
                    ((lParam.vkCode == 0x73) && (lParam.flags == 0x20)) ||      // Alt+F4
                    ((lParam.vkCode == 0x20) && (lParam.flags == 0x20)))        // Alt+Space
                    {
                        return new IntPtr(1);
                    }
                }
                keyList.Add(new int[] { wParam.ToInt32(), lParam.vkCode });
            }

            return CallNextHookEx(hookPtr, nCode, wParam, ref lParam);
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct KBHookStruct
        {
            public int vkCode;
            public int scanCode;
            public int flags;
            public int time;
            public int dwExtraInfo;
        }
    }

}
