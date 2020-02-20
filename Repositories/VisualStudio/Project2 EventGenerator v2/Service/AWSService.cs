using Amazon.CloudFront.Model;
using EventGenerator.Utility;
using System;
using System.Configuration;
using System.Threading;
using System.Threading.Tasks;

using static EventGenerator.Model.Constants;

namespace EventGenerator.Service
{
    class AWSService
    {
        public async void AWSCloudFrontInvalidationCopy()
        {
            using var client = new Amazon.CloudFront.AmazonCloudFrontClient(
                                awsAccessKeyId: ConfigurationManager.AppSettings[AWS_ACCESS_KEY_ID],
                                awsSecretAccessKey: ConfigurationManager.AppSettings[AWS_SECRET_ACCESS_KEY],
                                region: Amazon.RegionEndpoint.APNortheast1);
            try
            {
                var distribution = (await client.GetDistributionAsync(new GetDistributionRequest(ConfigurationManager.AppSettings[AWS_DISTRIBUTION_ID]))).Distribution;
                Console.Write(distribution.DistributionConfig.Comment);

                var defaultInvalidation = (await client.GetInvalidationAsync(new GetInvalidationRequest(distribution.Id, ConfigurationManager.AppSettings[AWS_BASE_INVALIDATION_ID]))).Invalidation;
                Console.Write(string.Join("\n", defaultInvalidation.InvalidationBatch.Paths.Items));

                var nowTime = System.DateTime.Now.AddTicks(-System.DateTime.Parse("1970-01-01 09:00:00").Ticks).Ticks / 10000;
                var response = await client.CreateInvalidationAsync(new CreateInvalidationRequest(distribution.Id, new InvalidationBatch(defaultInvalidation.InvalidationBatch.Paths, nowTime.ToString())));
                var resultInvalidation = client.GetInvalidation(new GetInvalidationRequest(distribution.Id, response.Invalidation.Id)).Invalidation;
                await Task.Factory.StartNew(() =>
                {
                    while (!resultInvalidation.Status.Equals("Completed"))
                    {
                        Console.Write(resultInvalidation.Status);
                        Thread.Sleep(5000);
                        resultInvalidation = client.GetInvalidation(new GetInvalidationRequest(distribution.Id, response.Invalidation.Id)).Invalidation;
                    }
                });
                SingleIcon.Toast("완료", resultInvalidation.Status);
            }
            catch (Exception e)
            {
                ChatworkUtility.Get().SendAsync(e);
            }
        }
    }
}
