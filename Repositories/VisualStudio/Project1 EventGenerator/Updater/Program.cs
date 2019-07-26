using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Updater
{
    public class Program
    {
        public static string[] downloadPaths =
        {
            @"\\경로1\이벤트 생성기.zip",
            @"\\경로2\이벤트 생성기.zip",
        };

        public static void Main(string[] args)
        {
            foreach (var p in Process.GetProcessesByName("통합 이벤트 생성기"))
                p.Kill();

            foreach (var downloadPath in downloadPaths)
                if (Update(downloadPath)) break;
        }

        private static bool Update(string downloadPath)
        {
            try
            {
                using (var archive = ZipFile.OpenRead(downloadPath))
                {
                    foreach (var entiry in archive.Entries)
                    {
                        var name = "./" + entiry.FullName;
                        var path = name.Substring(0, name.LastIndexOf('/'));

                        Console.WriteLine("\nDeleting... >> " + name);
                        try { File.Delete(name); }
                        catch (Exception e) { Console.WriteLine(e.Message); }

                        Console.WriteLine("Writing... >> " + name);
                        try { Directory.CreateDirectory(path); }
                        catch (Exception e) { Console.WriteLine(e.Message); }
                        try { entiry.ExtractToFile(name); }
                        catch (Exception e) { Console.WriteLine(e.Message); }

                        if (name.EndsWith(".dll") || name.EndsWith(".xml"))
                            try
                            {
                                Console.WriteLine("Hiding... >> " + name);
                                File.SetAttributes(name, File.GetAttributes(name) | FileAttributes.Hidden);
                            }
                            catch (Exception e) { Console.WriteLine(e.Message); }
                    }
                }
            } catch { return false; }

            var process = new Process();
            process.StartInfo.FileName = "통합 이벤트 생성기.exe";
            process.StartInfo.RedirectStandardOutput = false;
            process.StartInfo.UseShellExecute = false;
            process.Start();
            return true;
        }
    }
}
