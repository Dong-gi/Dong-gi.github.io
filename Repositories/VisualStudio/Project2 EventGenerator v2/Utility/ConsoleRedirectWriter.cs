using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public class ConsoleRedirectWriter : StringWriter
    {
        public static readonly ConsoleRedirectWriter WRITER = new ConsoleRedirectWriter();
        public Action<string> OnWrite = delegate { };


        private ConsoleRedirectWriter() => Console.SetOut(this);

        public void Write<T>(T value) => OnWrite(value?.ToString() ?? "");
        public void WriteLine<T>(T value)
        {
            Write(value);
            WriteLine();
        }

        public override void Write(bool value) => Write(value);
        public override void Write(char value) => Write(value);
        public override void Write(char[] buffer) => OnWrite(new String(buffer));
        public override void Write(char[] buffer, int index, int count) => OnWrite(new String(buffer, index, count));
        public override void Write(decimal value) => Write(value);
        public override void Write(double value) => Write(value);
        public override void Write(int value) => Write(value);
        public override void Write(long value) => Write(value);
        public override void Write(uint value) => Write(value);
        public override void Write(ulong value) => Write(value);
        public override void Write(object value) => Write(value);
        public override void Write(float value) => Write(value);
        public override void Write(string value) => OnWrite(value);
        public override void Write(string format, object arg0) => OnWrite(String.Format(format, arg0));
        public override void Write(string format, object arg0, object arg1) => OnWrite(String.Format(format, arg0, arg1));
        public override void Write(string format, object arg0, object arg1, object arg2) => OnWrite(String.Format(format, arg0, arg1, arg2));
        public override void Write(string format, object[] arg) => OnWrite(String.Format(format, arg));

        public override void WriteLine() => Write(new String(CoreNewLine));
        public override void WriteLine(bool value) => WriteLine(value);
        public override void WriteLine(char value) => WriteLine(value);
        public override void WriteLine(char[] buffer) => WriteLine(new String(buffer));
        public override void WriteLine(char[] buffer, int index, int count) => WriteLine(new String(buffer, index, count));
        public override void WriteLine(decimal value) => WriteLine(value);
        public override void WriteLine(double value) => WriteLine(value);
        public override void WriteLine(int value) => WriteLine(value);
        public override void WriteLine(long value) => WriteLine(value);
        public override void WriteLine(uint value) => WriteLine(value);
        public override void WriteLine(ulong value) => WriteLine(value);
        public override void WriteLine(object value) => WriteLine(value);
        public override void WriteLine(float value) => WriteLine(value);
        public override void WriteLine(string value) => WriteLine(value);
        public override void WriteLine(string format, object arg0) => WriteLine(String.Format(format, arg0));
        public override void WriteLine(string format, object arg0, object arg1) => WriteLine(String.Format(format, arg0, arg1));
        public override void WriteLine(string format, object arg0, object arg1, object arg2) => WriteLine(String.Format(format, arg0, arg1, arg2));
        public override void WriteLine(string format, object[] arg) => WriteLine(String.Format(format, arg));

        public override Task WriteLineAsync() => Task.Factory.StartNew(() => WriteLine());
        public override Task WriteLineAsync(char value) => Task.Factory.StartNew(() => WriteLine(value));
        //public override Task WriteLineAsync(char[] buffer) => Task.Factory.StartNew(() => WriteLine(buffer));
        public override Task WriteLineAsync(char[] buffer, int index, int count) => Task.Factory.StartNew(() => WriteLine(buffer, index, count));
        public override Task WriteLineAsync(string value) => Task.Factory.StartNew(() => WriteLine(value));
    }
}
