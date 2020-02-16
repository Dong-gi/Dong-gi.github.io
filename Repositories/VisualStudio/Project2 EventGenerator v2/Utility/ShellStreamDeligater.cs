using EventGenerator.ViewModel;
using Renci.SshNet;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public class ShellStreamDeligater : IDisposable
    {
        private ShellStream Stream { get; set; }
        private bool IsCopy { get; set; } = false;
        public string CopyText { get; private set; }
        public bool IsWriteConsole { get; set; } = true;
        public bool IsReceived { get; private set; } = false;

        public ShellStreamDeligater(ShellStream stream)
        {
            if ((Stream = stream) == null)
                throw new ArgumentNullException(nameof(stream));
            stream.DataReceived += (sender, args) =>
            {
                var text = Encoding.Default.GetString(args.Data);
                if (IsCopy)
                    CopyText += text;
                if (IsWriteConsole)
                    Console.Write(text);
                IsReceived = true;
            };
        }

        public ShellStreamDeligater CopyStart()
        {
            CopyText = "";
            IsCopy = true;
            return this;
        }
        public ShellStreamDeligater CopyEnd()
        {
            IsCopy = false;
            ActionUtility.UI(() => System.Windows.Clipboard.SetText(CopyText)).Wait();
            //MainViewModel.Current.Toast("알림", "클립보드로 복사 완료");
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
            return this;
        }
        public ShellStreamDeligater Expect(Regex regex, TimeSpan timeout)
        {
            if (Stream.Expect(regex, timeout) == null)
                throw new TimeoutException();
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
