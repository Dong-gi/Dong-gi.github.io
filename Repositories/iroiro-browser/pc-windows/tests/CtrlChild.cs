// 시험이 띄우는 자식 — tun2proxy(ctrlc2)처럼 SetConsoleCtrlHandler 로 Ctrl+C 를 듣는다.
//   ctrlc-child.exe wait <표지 파일>   Ctrl+C 를 받으면 0.3초 '정리' 하고 표지 파일에 clean 을 쓰고 끝난다
//   ctrlc-child.exe ignore            Ctrl+C 를 받고도 끝나지 않는다(강제 종료 길)
//   ctrlc-child.exe args ...          받은 인자를 [..] 로 한 줄씩 찍는다(명령줄 싸기 확인)
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

static class CtrlChild
{
    delegate bool HandlerRoutine(uint type);
    [DllImport("kernel32.dll")] static extern bool SetConsoleCtrlHandler(HandlerRoutine h, bool add);

    static HandlerRoutine keep;
    static readonly ManualResetEvent got = new ManualResetEvent(false);
    static uint gotType = 99;

    static int Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "args")
        {
            for (int i = 1; i < args.Length; i++) Console.WriteLine("[" + args[i] + "]");
            return 0;
        }
        bool ignore = args.Length > 0 && args[0] == "ignore";
        string marker = args.Length > 1 ? args[1] : null;
        keep = t => { gotType = t; got.Set(); return true; };
        SetConsoleCtrlHandler(keep, true);
        Console.WriteLine("ready");
        Console.Out.Flush();
        if (!got.WaitOne(30000)) { Console.WriteLine("timeout"); return 3; }
        Console.WriteLine("ctrl event " + gotType);
        Console.Out.Flush();
        if (ignore) { Thread.Sleep(60000); return 4; }
        Thread.Sleep(300);
        if (marker != null) File.WriteAllText(marker, "clean " + gotType);
        Console.WriteLine("cleaned up");
        return 0;
    }
}
