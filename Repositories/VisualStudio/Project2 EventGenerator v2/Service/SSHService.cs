using Renci.SshNet;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

using static EventGenerator.Model.Constants;

namespace EventGenerator.Service
{
    class SSHService
    {
        public async void SSHExample()
        {
            var connectionInfo = new ConnectionInfo(
                                        host: ConfigurationManager.AppSettings[SSH_EXAMPLE_SERVER_IP],
                                        port: int.Parse(ConfigurationManager.AppSettings[SSH_EXAMPLE_SERVER_PORT]),
                                        username: ConfigurationManager.AppSettings[SSH_EXAMPLE_SERVER_ID],
                                        authenticationMethods: new PasswordAuthenticationMethod(
                                            username: ConfigurationManager.AppSettings[SSH_EXAMPLE_SERVER_ID],
                                            password: ConfigurationManager.AppSettings[SSH_EXAMPLE_SERVER_PW])
                                        );
            using (var client = new SshClient(connectionInfo))
            {
                try
                {
                    client.Connect();
                }
                catch(Exception e)
                {
                    Console.Write(e);
                }
                if (!client.IsConnected)
                {
                    SingleIcon.Toast("주의", "SSH 연결 실패...");
                    return;
                }
                var result = await Task.Factory.StartNew(() => client.CreateCommand("bin/command.sh").Execute());
                SingleIcon.Toast("성공", result);
                client.Disconnect();
            }
        }
    }
}
