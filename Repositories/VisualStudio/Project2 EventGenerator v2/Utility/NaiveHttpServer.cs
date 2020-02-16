using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace EventGenerator.Utility
{
    public class NaiveHttpServer
    {
        public static class Status
        {
            public const string OK = "200 OK";
            public const string NotFound = "404 Not Found";
            public const string InternalServerError  = "500 Internal Server Error";
            public const string NotImplemented = "501 Not Implemented";
        }
        public static class MimeType
        {
            public const string Text = "text/plain";
            public const string Html = "text/html";
            public const string Css = "text/css";
            public const string Javascript = "text/javascript";

            public static string Get(string path)
            {
                try
                {
                    switch (path.Split('.').Last().ToLower())
                    {
                        case "html": return Html;
                        case "css": return Css;
                        case "js":
                        case "json": return Javascript;
                        default: return Text;
                    }
                }
                catch
                {
                    return Text;
                }
            }
        }
        public enum Method { GET, POST }
        public class Request
        {
            public Method Method { get; private set; }
            public string RequestPath { get; private set; }
            public Dictionary<string, string> Header { get; private set; }
            public Dictionary<string, string> Param { get; private set; }
            
            public Request(Method method, string path, string header = null, string param = null)
            {
                Method = method;
                RequestPath = path;
                Header = new Dictionary<string, string>();
                if (header != null)
                {
                    var regex = new Regex(@"([^:]+):(.+)");
                    foreach (var line in header.Split('\n'))
                    {
                        var match = regex.Match(line);
                        if (match.Success)
                            Header[match.Groups[1].Value.Trim()] = match.Groups[2].Value.Trim();
                    }
                }
                try
                {
                    Param = Newtonsoft.Json.JsonConvert.DeserializeObject<Dictionary<string, string>>(param);
                }
                catch
                {
                    Param = new Dictionary<string, string>();
                }
            }
            public override string ToString() => Newtonsoft.Json.JsonConvert.SerializeObject(this);
        }
        public class Handler
        {
            public delegate bool Filter(Request request);
            public delegate void Handle(Request request, NetworkStream stream);
            public Filter RequestFilter { get; set; }
            public Handle RequestHandler { get; set; }
        }


        private static readonly List<NaiveHttpServer> Servers = new List<NaiveHttpServer>();
        public TcpListener Server { get; private set; }
        public List<Handler> Handlers { get; set; } = new List<Handler>();
        public string BaseUrl { get; private set; }
        public string DocRoot { get; set; } = "./WWW";


        public NaiveHttpServer(ushort port = 55555)
        {
            while (Server == null)
            {
                try
                {
                    Server = new TcpListener(IPAddress.Loopback, port);
                    Server.Start();
                    BaseUrl = $"http://localhost:{port}/";
                    Console.WriteLine($"Simple HTTP Server Listening... : {BaseUrl}");
                }
                catch
                {
                    Server = null;
                    port++;
                    continue;
                }
            }
            Listen();
            Servers.Add(this);
        }

        public static void CloseAll() => Servers.ForEach((x) => x.Close());
        public void Close() => Server.Stop();
        public void OpenBrowser() => System.Diagnostics.Process.Start(BaseUrl);

        private async void Listen()
        {
            TcpClient client;
            try
            {
                client = await Server.AcceptTcpClientAsync();
            }
            catch
            {
                return;
            }
            Listen();
            Work(client);
        }

        public void Work(TcpClient client)
        {
            var stream = client.GetStream();
            var buf = new byte[1024];
            var requestBuilder = new StringBuilder();
            while (stream.DataAvailable)
            {
                var size = stream.Read(buf, 0, buf.Length);
                requestBuilder.Append(Encoding.UTF8.GetString(buf, 0, size));
            }

            var requestText = requestBuilder.ToString().Replace("\r", "");
            Console.WriteLine(requestText);
            Handle(requestText, stream);
            
            stream.Close();
            client.Close();
        }

        public void Handle(string requestText, NetworkStream stream)
        {
            Request request = null;
            if (requestText.StartsWith("GET"))
            {
                var regex = new Regex(@"GET\s*([^ ?]+)[?]?([\S]*).*\n([\s\S]+)");
                var match = regex.Match(requestText);
                if (match.Success)
                    request = new Request(Method.GET, match.Groups[1].Value.Trim(), match.Groups[3].Value.Trim(), Uri.UnescapeDataString(match.Groups[2].Value.Trim()));
            }
            else if (requestText.StartsWith("POST"))
            {
                var regex = new Regex(@"POST\s*([^ ?]+)[?]?([\S]*).*\n([\s\S]+?)\n\n([\s\S]*)");
                var match = regex.Match(requestText);
                if (match.Success)
                    request = new Request(Method.GET, match.Groups[1].Value.Trim(), match.Groups[3].Value.Trim(), match.Groups[4].Value.Trim());
            }
            if (request != null && Handlers.Any(x => x.RequestFilter(request)))
            {
                Console.WriteLine(request);
                try
                {
                    Handlers.Find(x => x.RequestFilter(request)).RequestHandler(request, stream);
                }
                catch (Exception e)
                {
                    Write(stream, e.StackTrace, Status.InternalServerError);
                }
            }
            else
            {
                Write(stream, "Handler not implemented for your request", Status.NotImplemented);
                return;
            }
        }

        public static void Write(NetworkStream stream, string body, string status = Status.OK, string mime = MimeType.Html)
        {
            var data = Encoding.UTF8.GetBytes(body);
            var header = Encoding.UTF8.GetBytes($@"HTTP/1.1 {status}
Server: C# TcpListener
Content-Type: {mime}; charset=utf-8
Content-Length: {data.Length}

");
            var all = new byte[header.Length + data.Length];
            header.CopyTo(all, 0);
            data.CopyTo(all, header.Length);
            Write(stream, all);
        }
        public static void WriteTextFile(string path, NetworkStream stream)
        {
            try
            {
                if (File.Exists(path))
                    Write(stream, File.ReadAllText(path), mime: MimeType.Get(path));
                else
                    Write(stream, $"The file '{path}' not exists", Status.NotFound);
            }
            catch
            {
                Write(stream, $"Failed to read '{path}'", Status.NotFound);
            }
        }
        public static void WriteBinaryFile(string path, NetworkStream stream)
        {
            try
            {
                if (File.Exists(path))
                    WriteBlob(stream, File.ReadAllBytes(path));
                else
                    Write(stream, $"The file '{path}' not exists", Status.NotFound);
            }
            catch
            {
                Write(stream, $"Failed to read '{path}'", Status.NotFound);
            }
        }
        public static void WriteBlob(NetworkStream stream, byte[] body, string status = Status.OK)
        {
            var header = Encoding.UTF8.GetBytes($@"HTTP/1.1 {status}
Server: C# TcpListener
Content-Type: application/octet-stream;
Content-Length: {body.Length}

");
            var all = new byte[header.Length + body.Length];
            header.CopyTo(all, 0);
            body.CopyTo(all, header.Length);
            Write(stream, all);
        }
        public static void Write(NetworkStream stream, byte[] msg)
        {
            for (var count = 5; count > 0; --count)
            {
                if (!stream.CanWrite)
                {
                    System.Threading.Thread.Sleep(100);
                    continue;
                }
                stream.Write(msg, 0, msg.Length);
                stream.Flush();
                System.Threading.Thread.Sleep(20);
                return;
            }
            Console.WriteLine("Failed");
        }
    }
}