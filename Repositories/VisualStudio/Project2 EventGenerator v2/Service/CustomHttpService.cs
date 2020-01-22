using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;

namespace EventGenerator.Service
{
    public class CustomHttpService
    {
        public TcpListener Server { get; private set; }

        public CustomHttpService()
        {
            var random = new Random(System.DateTime.Now.Millisecond);
            var port = 55555;
            while (Server == null)
            {
                try
                {
                    Server = new TcpListener(IPAddress.Loopback, port);
                    Server.Start();
                }
                catch
                {
                    Server = null;
                    port = random.Next(10000, 60000);
                    continue;
                }
                var baseURL = $"http://localhost:{port}/";
                System.Diagnostics.Process.Start(baseURL);
                Console.WriteLine(baseURL);
            }
            Work();
        }

        private async void Work()
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
            Work();
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
            stream.Write(body, 0, body.Length);
            stream.Flush();
            stream.Close();
            client.Close();
        }
    }
}
