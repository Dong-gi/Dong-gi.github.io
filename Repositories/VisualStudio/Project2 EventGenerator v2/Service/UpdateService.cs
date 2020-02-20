using System.Diagnostics;

namespace EventGenerator.Service
{
    class UpdateService
    {
        public static void Update()
        {
            var process = new Process();
            process.StartInfo.FileName = System.AppDomain.CurrentDomain.BaseDirectory + @"\Updater.exe";
            process.StartInfo.RedirectStandardOutput = false;
            process.StartInfo.UseShellExecute = false;
            process.Start();
        }
        /*
                        using (var process = new System.Diagnostics.Process())
                        {
                            process.StartInfo.WorkingDirectory = fileInfo.DirectoryName;
                            process.StartInfo.FileName = process.StartInfo.WorkingDirectory + @"\.exe";
                            process.StartInfo.Arguments = $"{fileName}.csv";
                            process.StartInfo.RedirectStandardOutput = true;
                            process.StartInfo.RedirectStandardError = true;
                            process.StartInfo.UseShellExecute = false;
                            process.StartInfo.CreateNoWindow = true;
                            process.Start();
                            while (!process.HasExited)
                            {
                                var line = process.StandardOutput.ReadLine();
                                if (line.Length > 0)
                                    Console.Write(line);
                                Thread.Sleep(100);
                            }
                            process.WaitForExit();
                            Console.Write(process.StandardOutput.ReadToEnd());
                            Console.Write(process.StandardError.ReadToEnd());
                        }
         */
    }
}
