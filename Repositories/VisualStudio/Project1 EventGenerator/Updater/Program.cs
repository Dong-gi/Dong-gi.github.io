using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Updater
{
    public class Program
    {
        public static void Main(string[] args)
        {
            foreach (var p in Process.GetProcessesByName("통합 이벤트 생성기"))
                p.Kill();

            InstallerUpdater.Update();
        }
    }
}
