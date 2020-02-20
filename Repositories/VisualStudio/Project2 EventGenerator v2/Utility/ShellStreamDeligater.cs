using Renci.SshNet;
using System;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;

namespace EventGenerator.Utility
{
    public class ShellStreamDeligater : IDisposable
    {
        private ShellStream Stream { get; set; }
        private StringBuilder CopyText { get; set; } = new StringBuilder();
        public string LastCopiedTest { get; private set; }
        public bool IsWriteConsole { get; set; } = true;
        public bool IsReceived { get; private set; } = false;

        public ShellStreamDeligater(ShellStream stream)
        {
            if ((Stream = stream) == null)
                throw new ArgumentNullException(nameof(stream));
            stream.DataReceived += (sender, args) =>
            {
                var text = Encoding.Default.GetString(args.Data);
                CopyText.Append(text);
                if (IsWriteConsole)
                    Console.Write(text);
                IsReceived = true;
            };
        }

        public ShellStreamDeligater CopyStart()
        {
            CopyText.Clear();
            return this;
        }
        public ShellStreamDeligater CopyEnd()
        {
            LastCopiedTest = CopyText.Length > 0 ? CopyText.ToString() : "EMPTY";
            if (IsWriteConsole)
                Console.WriteLine($"복사 완료 : {LastCopiedTest}");
            ActionUtility.UI(() => System.Windows.Clipboard.SetText(LastCopiedTest)).Wait();
            return this;
        }
        public ShellStreamDeligater Read()
        {
            while (!IsReceived) Thread.Sleep(100);
            IsReceived = false;
            do
            {
                Thread.Sleep(100);
            } while (IsReceived);
            IsReceived = false;
            return this;
        }
        public ShellStreamDeligater WriteLine(string text)
        {
            Stream.WriteLine(text);
            return this;
        }
        public ShellStreamDeligater Flush()
        {
            Stream.Flush();
            return this;
        }
        public ShellStreamDeligater Expect(Regex regex)
        {
            Stream.Expect(regex);
            while (!regex.IsMatch(CopyText.ToString()))
                Thread.Sleep(50);
            return this;
        }
        public ShellStreamDeligater Expect(Regex regex, TimeSpan timeout)
        {
            if (Stream.Expect(regex, timeout) == null)
                throw new TimeoutException();
            while (!regex.IsMatch(CopyText.ToString()))
                Thread.Sleep(50);
            return this;
        }

        private bool Disposed { get; set; } = false;
        protected virtual void Dispose(bool disposing)
        {
            if (Disposed) return;
            Stream.Close();
            Stream.Dispose();
            Disposed = true;
        }

        public void Dispose()
        {
            Dispose(true);
            GC.SuppressFinalize(this);
        }
    }
}
