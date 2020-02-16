using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Updater
{
    public class InstallerUpdater
    {
        public static readonly string[] updatePaths =
        {
            @"\\경로1\setup.exe",
            @"\\경로2\setup.exe",
        };

        public static void Update()
        {
            foreach (var updatePath in updatePaths)
                if (Update(updatePath)) break;
        }

        private static bool Update(string updatePath)
        {
            var process = new Process();
            process.StartInfo.FileName = updatePath;
            process.StartInfo.RedirectStandardOutput = false;
            process.StartInfo.UseShellExecute = false;
            process.Start();
            return true;
        }
    }
}
