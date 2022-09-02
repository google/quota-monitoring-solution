# Quota Monitoring Solution Module

This module deploys the core of the Quota Monitoring Solution, including the
Cloud Function, Pub/Sub topics, BigQuery table and base logging and alerting.

## Usage

```hcl
module "qms" {
  source = "git::https://github.com/google/quota-monitoring-solution/terraform/modules//qms"

  project_id                    = "your-project-id"
  region                        = "us-west2"
  service_account_email         = "serviceAccount:sa-your-project-id@your-project-id.iam.gserviceaccount.com"
  folders                       = "[678901]"
  organizations                 = "[123456]"
  alert_log_bucket_name         = "your-alert-log-bucket"
  notification_email_address    = "alert@example.com"
  threshold                     = "80"
}
```

<!-- BEGIN_TF_DOCS -->
## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_Alert_data_scanning_frequency"></a> [Alert\_data\_scanning\_frequency](#input\_Alert\_data\_scanning\_frequency) | Value of Big Query scheduled query frequency to fetch the alerts | `string` | `"every 12 hours"` | no |
| <a name="input_alert_log_bucket_name"></a> [alert\_log\_bucket\_name](#input\_alert\_log\_bucket\_name) | Bucket Name for alert Log Sink (must be globally unique) | `string` | n/a | yes |
| <a name="input_big_query_alert_dataset_desc"></a> [big\_query\_alert\_dataset\_desc](#input\_big\_query\_alert\_dataset\_desc) | Value of the Big Query Alert Dataset description | `string` | `"Dataset to store quota monitoring alert data"` | no |
| <a name="input_big_query_alert_dataset_id"></a> [big\_query\_alert\_dataset\_id](#input\_big\_query\_alert\_dataset\_id) | Value of the Big Query Dataset Id to store alerts | `string` | `"quota_monitoring_notification_dataset"` | no |
| <a name="input_big_query_alert_table_id"></a> [big\_query\_alert\_table\_id](#input\_big\_query\_alert\_table\_id) | Value of the Big Query Table Id to store alerts | `string` | `"quota_monitoring_notification_table"` | no |
| <a name="input_big_query_dataset_default_partition_expiration_ms"></a> [big\_query\_dataset\_default\_partition\_expiration\_ms](#input\_big\_query\_dataset\_default\_partition\_expiration\_ms) | Value of the Big Query Dataset default partition expiration | `number` | `86400000` | no |
| <a name="input_big_query_dataset_desc"></a> [big\_query\_dataset\_desc](#input\_big\_query\_dataset\_desc) | Value of the Big Query Dataset description | `string` | `"Dataset to store quota monitoring data"` | no |
| <a name="input_big_query_dataset_id"></a> [big\_query\_dataset\_id](#input\_big\_query\_dataset\_id) | Value of the Big Query Dataset Id | `string` | `"quota_monitoring_dataset"` | no |
| <a name="input_big_query_dataset_location"></a> [big\_query\_dataset\_location](#input\_big\_query\_dataset\_location) | Value of the Big Query Dataset location | `string` | `"US"` | no |
| <a name="input_big_query_table_id"></a> [big\_query\_table\_id](#input\_big\_query\_table\_id) | Value of the Big Query Table Id | `string` | `"quota_monitoring_table"` | no |
| <a name="input_big_query_table_partition"></a> [big\_query\_table\_partition](#input\_big\_query\_table\_partition) | Value of the Big Query Table time partitioning | `string` | `"DAY"` | no |
| <a name="input_bigquery_data_transfer_query_name"></a> [bigquery\_data\_transfer\_query\_name](#input\_bigquery\_data\_transfer\_query\_name) | Value of the Name Big Query scheduled query to fetch rows where metric usage is >= threshold | `string` | `"extract-quota-usage-alerts"` | no |
| <a name="input_cloud_function_list_project"></a> [cloud\_function\_list\_project](#input\_cloud\_function\_list\_project) | Value of the name for the Cloud Function to list Project Ids to be scanned | `string` | `"quotaMonitoringListProjects"` | no |
| <a name="input_cloud_function_list_project_desc"></a> [cloud\_function\_list\_project\_desc](#input\_cloud\_function\_list\_project\_desc) | Value of the description for the Cloud Function to list Project Ids to be scanned | `string` | `"List Project Ids for the parent node"` | no |
| <a name="input_cloud_function_list_project_memory"></a> [cloud\_function\_list\_project\_memory](#input\_cloud\_function\_list\_project\_memory) | Value of the memory for the Cloud Function to list Project Ids to be scanned | `number` | `512` | no |
| <a name="input_cloud_function_list_project_timeout"></a> [cloud\_function\_list\_project\_timeout](#input\_cloud\_function\_list\_project\_timeout) | Value of the timeout for the Cloud Function to list Project Ids to be scanned | `number` | `540` | no |
| <a name="input_cloud_function_notification_project"></a> [cloud\_function\_notification\_project](#input\_cloud\_function\_notification\_project) | Value of the Name for the Cloud Function to send quota alerts | `string` | `"quotaMonitoringNotification"` | no |
| <a name="input_cloud_function_notification_project_desc"></a> [cloud\_function\_notification\_project\_desc](#input\_cloud\_function\_notification\_project\_desc) | Value of the description for the Cloud Function to send notification | `string` | `"Send notification"` | no |
| <a name="input_cloud_function_notification_project_memory"></a> [cloud\_function\_notification\_project\_memory](#input\_cloud\_function\_notification\_project\_memory) | Value of the memory for the Cloud Function to send notification | `number` | `512` | no |
| <a name="input_cloud_function_notification_project_timeout"></a> [cloud\_function\_notification\_project\_timeout](#input\_cloud\_function\_notification\_project\_timeout) | Value of the timeout for the Cloud Function to send notification | `number` | `540` | no |
| <a name="input_cloud_function_scan_project"></a> [cloud\_function\_scan\_project](#input\_cloud\_function\_scan\_project) | Value of the Name for the Cloud Function to scan Project quotas and load in Big Query | `string` | `"quotaMonitoringScanProjects"` | no |
| <a name="input_cloud_function_scan_project_desc"></a> [cloud\_function\_scan\_project\_desc](#input\_cloud\_function\_scan\_project\_desc) | Value of the description for the Cloud Function to scan Project quotas | `string` | `"Scan Project Quotas for the project Ids received"` | no |
| <a name="input_cloud_function_scan_project_memory"></a> [cloud\_function\_scan\_project\_memory](#input\_cloud\_function\_scan\_project\_memory) | Value of the memory for the Cloud Function to scan Project quotas | `number` | `512` | no |
| <a name="input_cloud_function_scan_project_timeout"></a> [cloud\_function\_scan\_project\_timeout](#input\_cloud\_function\_scan\_project\_timeout) | Value of the timeout for the Cloud Function to scan Project quotas | `number` | `540` | no |
| <a name="input_folders"></a> [folders](#input\_folders) | Value of the list of folders to be scanned for quota | `string` | n/a | yes |
| <a name="input_log_sink_name"></a> [log\_sink\_name](#input\_log\_sink\_name) | Name for Log Sink | `string` | `"quota-monitoring-sink"` | no |
| <a name="input_notification_email_address"></a> [notification\_email\_address](#input\_notification\_email\_address) | Email Address to receive email notifications | `string` | n/a | yes |
| <a name="input_organizations"></a> [organizations](#input\_organizations) | Value of the list of organization Ids to scanned for quota | `string` | n/a | yes |
| <a name="input_project_id"></a> [project\_id](#input\_project\_id) | Value of the Project Id to deploy the solution | `string` | n/a | yes |
| <a name="input_region"></a> [region](#input\_region) | Value of the region to deploy the solution. Use the same region as used for App Engine | `string` | n/a | yes |
| <a name="input_retention_days"></a> [retention\_days](#input\_retention\_days) | Log Sink Bucket's retention period in days to detele alert logs | `number` | `30` | no |
| <a name="input_scheduler_cron_job_deadline"></a> [scheduler\_cron\_job\_deadline](#input\_scheduler\_cron\_job\_deadline) | Value of the The deadline for job attempts of cron job scheduler | `string` | `"540s"` | no |
| <a name="input_scheduler_cron_job_description"></a> [scheduler\_cron\_job\_description](#input\_scheduler\_cron\_job\_description) | Value of description of cron job scheduler | `string` | `"trigger quota monitoring scanning"` | no |
| <a name="input_scheduler_cron_job_frequency"></a> [scheduler\_cron\_job\_frequency](#input\_scheduler\_cron\_job\_frequency) | Value of the cron job frequency to trigger the solution | `string` | `"0 0 * * *"` | no |
| <a name="input_scheduler_cron_job_name"></a> [scheduler\_cron\_job\_name](#input\_scheduler\_cron\_job\_name) | Value of name of cron job scheduler | `string` | `"quota-monitoring-cron-job"` | no |
| <a name="input_scheduler_cron_job_timezone"></a> [scheduler\_cron\_job\_timezone](#input\_scheduler\_cron\_job\_timezone) | Value of the timezone of cron job scheduler | `string` | `"America/Chicago"` | no |
| <a name="input_service_account_email"></a> [service\_account\_email](#input\_service\_account\_email) | Value of the Service Account | `string` | n/a | yes |
| <a name="input_source_code_bucket_name"></a> [source\_code\_bucket\_name](#input\_source\_code\_bucket\_name) | Value of cloud storage bucket to download source code for Cloud Function | `string` | `"quota-monitoring-solution-source"` | no |
| <a name="input_source_code_notification_repo_url"></a> [source\_code\_notification\_repo\_url](#input\_source\_code\_notification\_repo\_url) | Value of Notification Quota Alerts source code git url | `string` | `"https://github.com/GoogleCloudPlatform/professional-services/tree/main/tools/quota-monitoring-alerting/quota-notification"` | no |
| <a name="input_source_code_notification_zip"></a> [source\_code\_notification\_zip](#input\_source\_code\_notification\_zip) | Value of Notification Quota Alerts source code zip file | `string` | `"quota-monitoring-notification.zip"` | no |
| <a name="input_source_code_repo_url"></a> [source\_code\_repo\_url](#input\_source\_code\_repo\_url) | Value of List and Scan Project Quotas source code git url | `string` | `"https://github.com/GoogleCloudPlatform/professional-services/tree/main/tools/quota-monitoring-alerting/quota-scan"` | no |
| <a name="input_source_code_zip"></a> [source\_code\_zip](#input\_source\_code\_zip) | Value of List and Scan Project Quotas source code zip file | `string` | `"quota-monitoring-solution.zip"` | no |
| <a name="input_threshold"></a> [threshold](#input\_threshold) | Value of threshold for all metrics. If any metric usage >= the threshold, notification will be created | `string` | n/a | yes |
| <a name="input_topic_alert_notification"></a> [topic\_alert\_notification](#input\_topic\_alert\_notification) | Value of the Pub/Sub topic Id to publish quota alerts from Big Query | `string` | `"quota-monitoring-topic-alert"` | no |
| <a name="input_topic_alert_project_id"></a> [topic\_alert\_project\_id](#input\_topic\_alert\_project\_id) | Value of the Pub/Sub topic Id to publish Project Ids from Cloud Function | `string` | `"quota-monitoring-topic-project-id"` | no |
| <a name="input_topic_alert_project_quota"></a> [topic\_alert\_project\_quota](#input\_topic\_alert\_project\_quota) | Value of the Pub/Sub topic Id to publish Project's quota from Cloud Function | `string` | `"quota-monitoring-topic-project"` | no |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_email0_id"></a> [email0\_id](#output\_email0\_id) | Full resource identifier for the notification channel. |

<!-- END_TF_DOCS -->