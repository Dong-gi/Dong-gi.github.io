using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;

namespace NaiveHttpServer
{
    public class Server : IDisposable
    {
        private TcpListener Listener { get; set; }
        public string BaseUrl { get; private set; }
        public string DocRoot { get; set; } = "./www";
        public List<Handler> Handlers { get; private set; } = new List<Handler>();

        public Server(ushort port = 55555)
        {
            var random = new Random(System.DateTime.Now.Millisecond);
            while (Listener == null)
            {
                try
                {
                    Listener = new TcpListener(IPAddress.Loopback, port);
                    Listener.Start();
                }
                catch
                {
                    Listener = null;
                    port = (ushort)random.Next(10000, 60000);
                    continue;
                }
                BaseUrl = $"http://localhost:{port}/";
                System.Diagnostics.Process.Start(BaseUrl);
                Console.WriteLine($"Simple HTTP Server Listening... : {BaseUrl}");
            }
            Listen();
        }

        private async void Listen()
        {
            TcpClient client;
            try
            {
                client = await Listener.AcceptTcpClientAsync();
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
            var request = new StringBuilder();
            while (stream.DataAvailable)
            {
                var size = stream.Read(buf, 0, buf.Length);
                request.Append(Encoding.UTF8.GetString(buf, 0, size));
            }
            Console.WriteLine(request);

            var body = Encoding.UTF8.GetBytes(
@"<html>
<head>
<script src=""https://code.jquery.com/jquery-3.4.1.min.js""></script>
</head>
<body>
안녕 세상
</body>
</html>");
            var response = Encoding.UTF8.GetBytes($@"HTTP/1.1 200 OK
Server: C# TcpListener
Content-Type: text/html; charset=utf-8
Content-Length: {body.Length}

");
            stream.Write(response, 0, response.Length);
            stream.Flush();
            stream.Write(body, 0, body.Length);
            stream.Flush();
            stream.Close();
            client.Close();
        }

        public void Close() => Dispose();
        public void Dispose()
        {
            Listener.Stop();
        }
    }
}
