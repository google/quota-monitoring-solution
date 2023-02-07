# Configure Project Alerting
> In addition to central dashboard and alerting at org or folder level, 
> configure dashboard and alerting at the project level.

**In this guide, we will refer project level alerting as app alerting.**

## Configure Dashboard
Create Looker Studio dashboard to monitor a collection of projects for an application. 
### 1. Create View in BigQuery
Create a view in BigQuery for the application projects
```sh
CREATE VIEW [YOUR_PROJECT_ID].quota_monitoring_dataset.[YOUR_APP_NAME] AS (
  SELECT * FROM `[YOUR_PROJECT_ID].quota_monitoring_dataset.quota_monitoring_table`
WHERE
    project_id IN ("[YOUR_PROJECT_ID_1]","[YOUR_PROJECT_ID_2]")
);
```
- Replace [YOUR_PROJECT_ID] with the project id where QMS is deployed. 
- Replace [YOUR_APP_NAME] with the application name of your preference. 
- Replace [YOUR_PROJECT_ID_1], [YOUR_PROJECT_ID_2] etc. with the project ids of the application project.
<br/><br>
This will create a new view in BigQuery with all the quota usage for the selected projects.

### 2. Looker Studio Dashboard Setup
Follow all the steps mentioned in the [main deployment guide](README.md#310-data-studio-dashboard-setup) to set up the dashboard and replace the 'table name' by 'view name':
```sql
SELECT
project_id,
added_at,
region,
quota_metric,
CASE
WHEN CAST(quota_limit AS STRING) ='9223372036854775807' THEN 'unlimited'
ELSE
CAST(quota_limit AS STRING)
END AS str_quota_limit,
SUM(current_usage) AS current_usage,
ROUND((SAFE_DIVIDE(CAST(SUM(current_usage) AS BIGNUMERIC), CAST(quota_limit AS BIGNUMERIC))*100),2) AS current_consumption,
SUM(max_usage) AS max_usage,
ROUND((SAFE_DIVIDE(CAST(SUM(max_usage) AS BIGNUMERIC), CAST(quota_limit AS BIGNUMERIC))*100),2) AS max_consumption
FROM
(
SELECT
*,
RANK() OVER (PARTITION BY project_id, region, quota_metric ORDER BY added_at DESC) AS latest_row
FROM
`[YOUR_PROJECT_ID].quota_monitoring_dataset.[YOUR_VIEW_NAME]`
) t
WHERE
latest_row=1
AND current_usage IS NOT NULL
AND quota_limit IS NOT NULL
AND current_usage != 0
AND quota_limit != 0
GROUP BY
project_id,
region,
quota_metric,
added_at,
quota_limit
```
<br><br>
Repeat this process multiple application level dashboard setup.

## Configure Alerting

### 1. Fill data in CSV file 
Populate data in CSV file and provide following information for each project you would like to configure alerting

<ol>
<li>project_id - GCP project Id</li>
<li>email_id - email id of group or person to receive alert</li>
<li>app_code - unique number for each record. This will be used to create alerting resources</li>
<li>dashboard_url - url of the dashboard for this project</li>
</ol>

A sample csv file looks like this
![Sample CSV](../quota-monitoring-solution/img/aa_csv.png)

You can use the sample csv file available with the solution to fill the data:
```sh
vi sample_qms_app_Alerting.csv
```
and rename the file to 'qms_app_alerting.csv' using:
```sh
mv sample_qms_app_Alerting.csv qms_app_Alerting.csv
```
### 2. Upload CSV file on GCS
Rerun terraform to upload the csv file in the GCS bucket
```sh
terraform plan
```
```sh
terraform apply
```
This will configure app level alerting using the CloudFunction<br><br>
To verify that the program ran successfully, check the BigQuery Table. The
time to load data in BigQuery might take a few seconds. A sample BigQuery table will look
like this:
    ![test-bigquery-table](../quota-monitoring-solution/img/aa_app_bigquery_table.png)
In addition to loading data from the csv file, cloud function also creates alerting 
resources and loads ids for custom log metric, notification channel and alert policy