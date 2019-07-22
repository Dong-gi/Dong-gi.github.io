using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Service
{
    class UpdateService
    {
        public static void Update()
        {
            var process = new Process();
            process.StartInfo.FileName = "Updater.exe";
            process.StartInfo.RedirectStandardOutput = false;
            process.StartInfo.UseShellExecute = false;
            process.Start();
        }
    }
}
