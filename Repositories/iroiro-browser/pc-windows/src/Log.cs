// 로그 — 메모리의 고리 버퍼(로그 창)와 파일(1 MB 넘으면 .1 로 돌려쓴다).
using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

namespace WebTether
{
    public sealed class Log
    {
        public const int Capacity = 3000;
        public const long MaxFileBytes = 1024 * 1024;

        readonly object gate = new object();
        readonly Queue<string> ring = new Queue<string>();
        readonly string file;
        readonly long maxBytes;
        readonly string guardRoot;
        long fileBytes = -1;

        public event Action<string> Line;

        public Log(string file) : this(file, MaxFileBytes, null) { }

        public Log(string file, long maxBytes) : this(file, maxBytes, null) { }

        /// <summary>guardRoot: 상태 폴더 — 쓰고 돌려쓰기 전에 그 아래에 재분석 지점이 없는지 본다(있으면 파일에 쓰지 않는다).</summary>
        public Log(string file, long maxBytes, string guardRoot)
        {
            this.file = file;
            this.maxBytes = maxBytes;
            this.guardRoot = guardRoot;
        }

        static readonly Regex Ansi = new Regex("\x1b\\[[0-9;]*[A-Za-z]");

        /// <summary>자식의 출력에서 색 부호와 제어 글자를 걷는다.</summary>
        public static string Clean(string s)
        {
            if (s == null) return "";
            s = Ansi.Replace(s, "");
            var sb = new StringBuilder(s.Length);
            foreach (var c in s) sb.Append(c == '\t' ? ' ' : (char.IsControl(c) ? '?' : c));
            var r = sb.ToString();
            return r.Length > 2000 ? r.Substring(0, 2000) + "…" : r;
        }

        public void Write(string source, string text)
        {
            var line = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " [" + source + "] " + Clean(text);
            Action<string> h;
            lock (gate)
            {
                ring.Enqueue(line);
                while (ring.Count > Capacity) ring.Dequeue();
                AppendFile(line);
                h = Line;
            }
            if (h != null) h(line);
        }

        /// <summary>이미 꼴을 갖춘 줄(서비스의 로그를 트레이가 받아 보일 때) — 파일에 쓰지 않는다.</summary>
        public void Append(string line)
        {
            line = Clean(line);
            Action<string> h;
            lock (gate)
            {
                ring.Enqueue(line);
                while (ring.Count > Capacity) ring.Dequeue();
                h = Line;
            }
            if (h != null) h(line);
        }

        public string[] Snapshot()
        {
            lock (gate) return ring.ToArray();
        }

        void AppendFile(string line)
        {
            if (file == null) return;
            try
            {
                var dir = Path.GetDirectoryName(file);
                SecureDir.CheckNoReparse(guardRoot, file);
                if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                if (fileBytes < 0) fileBytes = File.Exists(file) ? new FileInfo(file).Length : 0;
                var bytes = Encoding.UTF8.GetBytes(line + "\r\n");
                if (fileBytes + bytes.Length > maxBytes && fileBytes > 0)
                {
                    var old = file + ".1";
                    SecureDir.CheckNoReparse(guardRoot, old);
                    if (File.Exists(old)) File.Delete(old);
                    File.Move(file, old);
                    fileBytes = 0;
                }
                using (var fs = new FileStream(file, FileMode.Append, FileAccess.Write, FileShare.Read))
                    fs.Write(bytes, 0, bytes.Length);
                fileBytes += bytes.Length;
            }
            catch (IOException) { fileBytes = -1; }
            catch (UnauthorizedAccessException) { fileBytes = -1; }
        }
    }
}
