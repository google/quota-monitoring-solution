# Quota Monitoring and Alerting

> An easy-to-deploy Data Studio Dashboard with alerting capabilities, showing
usage and quota limits in an organization or folder.

Google Cloud enforces [quotas](https://cloud.google.com/docs/quota) on resource
usage for project owners, setting a limit on how much of a particular Google
Cloud resource your project can use. Each quota limit represents a specific
countable resource, such as the number of API requests made per day to the
number of load balancers used concurrently by your application.

Quotas are enforced for a variety of reasons:

*   To protect the community of Google Cloud users by preventing unforeseen
    spikes in usage.
*   To help you manage resources. For example, you can set your own limits on
    service usage while developing and testing your applications.

We are introducing a new custom quota monitoring and alerting solution for
Google Cloud customers.

## 1. Summary

Quota Monitoring Solution is a stand-alone application of an easy-to-deploy
Data Studio dashboard with alerting capabilities showing all usage and quota
limits in an organization or folder.

### 1.1 Four Initial Features

![key-features](img/quota_monitoring_key_features.png)

*The data refresh rate depends on the configured frequency to run the
application.

## 2. Architecture

![architecture](img/quota-monitoring-alerting-architecture.png)

The architecture is built using Google Cloud managed services - Cloud
Functions, Pub/Sub, Dataflow and BigQuery.

*   The solution is architected to scale using Pub/Sub.
*   Cloud Scheduler is used to trigger Cloud Functions. This is also an user
    interface to configure frequency, parent nodes, alert threshold and email Ids.
    Parent node could be an organization Id, folder id, list of organization Ids
    or list of folder Ids.
*   Cloud Functions are used to scan quotas across projects for the configured
    parent node.
*   BigQuery is used to store data.
*   Alert threshold will be applicable across all metrics.
*   Alerts can be received by Email, Mobile App, PagerDuty, SMS, Slack,
    Webhooks and Pub/Sub. Cloud Monitoring custom log metric has been leveraged to
    create Alerts.
*   Easy to get started and deploy with Data Studio Dashboard. In addition to
    Data Studio, other visualization tools can be configured.
*   The Data Studio report can be scheduled to be emailed to appropriate team
    for weekly/daily reporting.

## 3. Deployment Guide

### Content

<!-- markdownlint-disable -->
- [Quota Monitoring and Alerting](#quota-monitoring-and-alerting)
  - [1. Summary](#1-summary)
    - [1.1 Four Initial Features](#11-four-initial-features)
  - [2. Architecture](#2-architecture)
  - [3. Deployment Guide](#3-deployment-guide)
    - [Content](#content)
    - [3.1 Prerequisites](#31-prerequisites)
    - [3.2 Initial Setup](#32-initial-setup)
    - [3.3 Create Service Account](#33-create-service-account)
    - [3.4 Grant Roles to Service Account](#34-grant-roles-to-service-account)
      - [3.4.1 Grant Roles in the Host Project](#341-grant-roles-in-the-host-project)
      - [3.4.2 Grant Roles in the Target Folder](#342-grant-roles-in-the-target-folder)
      - [3.4.3 Grant Roles in the Target Organization](#343-grant-roles-in-the-target-organization)
    - [3.5 Download the Source Code](#35-download-the-source-code)
    - [3.6 Download Service Account Key File](#36-download-service-account-key-file)
    - [3.7 Configure Terraform](#37-configure-terraform)
    - [3.8 Run Terraform](#38-run-terraform)
    - [3.9 Testing](#39-testing)
    - [3.10 Data Studio Dashboard setup](#310-data-studio-dashboard-setup)
    - [3.11 Scheduled Reporting](#311-scheduled-reporting)
    - [3.11 Alerting](#311-alerting)
      - [3.11.1 Slack Configuration](#3111-slack-configuration)
        - [3.11.1.1 Create Notification Channel](#31111-create-notification-channel)
        - [3.11.1.2 Configuring Alerting Policy](#31112-configuring-alerting-policy)
  - [4. Release Note](#4-release-note)
    - [4.1 V4: Quota Monitoring across GCP services](#41-v4-quota-monitoring-across-gcp-services)
      - [New](#new)
      - [Known Limitations](#known-limitations)
  - [5. What is Next](#5-what-is-next)
  - [5. Contact Us](#5-contact-us)
<!-- markdownlint-restore -->

### 3.1 Prerequisites

1.  Host Project - A project where the BigQuery instance, Cloud Function and
    Cloud Scheduler will be deployed. For example Project A.
2.  Target Node - The Organization or folder or project which will be scanned
    for Quota Metrics. For example Org A and Folder A.
3.  Project Owner role on host Project A. IAM Admin role in target Org A and
    target Folder A.
4.  Google Cloud SDK is installed. Detailed instructions to install the SDK
    [here](https://cloud.google.com/sdk/docs/install#mac). See the Getting Started
    page for an introduction to using gcloud and terraform.
5.  Terraform version >= 0.14.6 installed. Instructions to install terraform here
    *   Verify terraform version after installing.

    ```sh
    terraform -version
    ```

    The output should look like:

    ```sh
    Terraform v0.14.6
    + provider registry.terraform.io/hashicorp/google v3.57.0
    ```

    *Note - Minimum required version v0.14.6. Lower terraform versions may not work.*

### 3.2 Initial Setup

1.  In local workstation create a new directory to run terraform and store
    credential file

    ```sh
    mkdir <directory name like quota-monitoring-dashboard>
    cd <directory name>
    ```

2.  Set default project in config to host project A

    ```sh
    gcloud config set project <HOST_PROJECT_ID>
    ```

    The output should look like:

    ```sh
    Updated property [core/project].
    ```

3.  Ensure that the latest version of all installed components is installed on
    the local workstation.

    ```sh
    gcloud components update
    ```

4.  Cloud Scheduler depends on the App Engine application. Create an App Engine
    application in the host project. Replace the region. List of regions where
    App Engine is available can be found
    [here](https://cloud.google.com/about/locations#region).

    ```sh
    gcloud app create --region=<region>
    ```

    Note: Cloud Scheduler (below) needs to be in the same region as App Engine.
    Use the same region in terraform as mentioned here.

    The output should look like:

    ```sh
    You are creating an app for project [quota-monitoring-project-3].
    WARNING: Creating an App Engine application for a project is irreversible and the region
    cannot be changed. More information about regions is at
    <https://cloud.google.com/appengine/docs/locations>.

    Creating App Engine application in project [quota-monitoring-project-1] and region [us-east1]....done.

    Success! The app is now created. Please use `gcloud app deploy` to deploy your first app.
    ```

### 3.3 Create Service Account

1.  In local workstation, setup environment variables. Replace the name of the
    Service Account in the commands below

    ```sh
    export DEFAULT_PROJECT_ID=$(gcloud config get-value core/project 2> /dev/null)
    export SERVICE_ACCOUNT_ID="sa-"$DEFAULT_PROJECT_ID
    export DISPLAY_NAME="sa-"$DEFAULT_PROJECT_ID
    ```

2.  Verify host project Id.

    ```sh
    echo $DEFAULT_PROJECT_ID
    ```

3.  Create Service Account

    ```sh
    gcloud iam service-accounts create $SERVICE_ACCOUNT_ID --description="Service Account to scan quota usage" --display-name=$DISPLAY_NAME
    ```

    The output should look like:

    ```sh
    Created service account [sa-quota-monitoring-project-1].
    ```

### 3.4 Grant Roles to Service Account

#### 3.4.1 Grant Roles in the Host Project

The following roles need to be added to the Service Account in the host
project i.e. Project A:

*   BigQuery
    *   BigQuery Data Editor
    *   BigQuery Job User
*   Cloud Functions
    *   Cloud Functions Admin
*   Cloud Scheduler
    *   Cloud Scheduler Admin
*   Pub/Sub
    *   Pub/Sub Admin
*   Run Terraform
    *   Service Account User
    *   Enable APIs
    *   Service Usage Admin
*   Storage Bucket
    *   Storage Admin
*   Scan Quotas
    *   Cloud Asset Viewer
    *   Compute Network Viewer
    *   Compute Viewer
*   Monitoring
    *   Notification Channel Editor
    *   Alert Policy Editor
    *   Viewer
    *   Metric Writer
*   Logs
    *   Logs Configuration Writer
    *   Log Writer
*   IAM
    *   Security Admin

1.  Run following commands to assign the roles:

    ```sh
    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/bigquery.dataEditor" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/bigquery.jobUser" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/cloudfunctions.admin" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/cloudscheduler.admin" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/pubsub.admin" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/iam.serviceAccountUser" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/storage.admin" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/serviceusage.serviceUsageAdmin" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/cloudasset.viewer" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/compute.networkViewer" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/compute.viewer" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/monitoring.notificationChannelEditor" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/monitoring.alertPolicyEditor" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/logging.configWriter" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/logging.logWriter" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/monitoring.viewer" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/monitoring.metricWriter" --condition=None

    gcloud projects add-iam-policy-binding $DEFAULT_PROJECT_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/iam.securityAdmin" --condition=None
    ```

#### 3.4.2 Grant Roles in the Target Folder

SKIP THIS STEP IF THE FOLDER IS NOT THE TARGET TO SCAN QUOTA

If you want to scan projects in the folder, add following roles to the Service
Account created in the previous step at the target folder A:

*   Cloud Asset Viewer
*   Compute Network Viewer
*   Compute Viewer
*   Folder Viewer
*   Monitoring Viewer

1.  Set target folder id

    ```sh
    export TARGET_FOLDER_ID=<target folder id like 38659473572>
    ```

2.  Run the following commands add to the roles to the service account

    ```sh
    gcloud alpha resource-manager folders add-iam-policy-binding  $TARGET_FOLDER_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/cloudasset.viewer"

    gcloud alpha resource-manager folders add-iam-policy-binding  $TARGET_FOLDER_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/compute.networkViewer"

    gcloud alpha resource-manager folders add-iam-policy-binding  $TARGET_FOLDER_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/compute.viewer"

    gcloud alpha resource-manager folders add-iam-policy-binding  $TARGET_FOLDER_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/resourcemanager.folderViewer"

    gcloud alpha resource-manager folders add-iam-policy-binding  $TARGET_FOLDER_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/monitoring.viewer"
    ```

    Note: If this fails, run the commands again

#### 3.4.3 Grant Roles in the Target Organization

SKIP THIS STEP IF THE ORGANIZATION IS NOT THE TARGET

If you want to scan projects in the org, add following roles to the Service
Account created in the previous step at the Org A:

*   Cloud Asset Viewer
*   Compute Network Viewer
*   Compute Viewer
*   Org Viewer
*   Folder Viewer
*   Monitoring Viewer

![org-service-acccount-roles](img/service_account_roles.png)

1.  Set target organization id

    ```sh
    export TARGET_ORG_ID=<target org id ex. 38659473572>
    ```

2.  Run the following commands to add to the roles to the service account

    ```sh
    gcloud organizations add-iam-policy-binding  $TARGET_ORG_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com" --role="roles/cloudasset.viewer" --condition=None

    gcloud organizations add-iam-policy-binding  $TARGET_ORG_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com"  --role="roles/compute.networkViewer" --condition=None

    gcloud organizations add-iam-policy-binding  $TARGET_ORG_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com"  --role="roles/compute.viewer" --condition=None

    gcloud organizations add-iam-policy-binding  $TARGET_ORG_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com"  --role="roles/resourcemanager.folderViewer" --condition=None

    gcloud organizations add-iam-policy-binding  $TARGET_ORG_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com"  --role="roles/resourcemanager.organizationViewer" --condition=None

    gcloud organizations add-iam-policy-binding  $TARGET_ORG_ID --member="serviceAccount:$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com"  --role="roles/monitoring.viewer" --condition=None
    ```

### 3.5 Download the Source Code

1.  Clone the Quota Management Solution repo

    ```sh
    git clone https://github.com/google/quota-monitoring-solution.git quota-monitorings-solution
    ```

2.  Change directories into the Terraform example

    ```sh
    cd ./quota-monitorings-solution/terraform/example
    ```

### 3.6 Download Service Account Key File

Create Service Account key from host project A. The service account key file
will be downloaded to your machine as key.json. After you download the key
file, you cannot download it again.

```sh
gcloud iam service-accounts keys create key.json \
    --iam-account=$SERVICE_ACCOUNT_ID@$DEFAULT_PROJECT_ID.iam.gserviceaccount.com
```

### 3.7 Configure Terraform

1.  Verify that you have these 4 files in your local directory:
    *   key.json
    *   main.tf
    *   variables.tf
    *   terraform.tfvars
2.  Open terraform.tfvars file in your favourite editor and change values for
    the variable
3.  Values for variable source_code_base_url, qms_version, source_code_zip and
    source_code_notification_zip are used to download the source for the QMS
    Cloud Functions. If you want to upgrade to
    latest code changes everytime you run 'terraform apply', change to this code
    source repository. DO NOT CHANGE if you do not want to receive latest code
    changes while running 'terraform apply' everytime after deployment.
4.  For region, use the same region as used for app engine in earlier steps.

    ```sh
    vi terraform.tfvars
    ```

![updated-tfvars](img/terraform-updated.png)

### 3.8 Run Terraform

1.  Run terraform commands
    *   terraform init
    *   terraform plan
    *   terraform apply
        *   On Prompt Enter a value: yes

2.  This will:
    *   Enable required APIs
    *   Create all resources and connect them.

Note: In case terraform fails, run terraform plan and terraform apply again

### 3.9 Testing

1.  Click ‘Run Now’ on Cloud Job scheduler.

    *Note: The status of the ‘Run Now’ button changes to ‘Running’ for a fraction
    of seconds.*

    ![run-cloud-scheduler](img/run_cloud_scheduler.png)

2.  To verify that the program ran successfully, check the BigQuery Table. The
    time to load data in BigQuery might take a few minutes. The execution time
    depends on the number of projects to scan. A sample BigQuery table will look
    like this:
    ![test-bigquery-table](img/test_bigquery_table.png)

### 3.10 Data Studio Dashboard setup

1.  Go to the [Data Studio dashboard template](https://datastudio.google.com/c/u/0/reporting/41cc9be3-6a55-4f32-b904-37f63e4f2c75/page/xxWVB).
    If this link is not accessible, reach out to
    quota-monitoring-solution@google.com to share the dashboard template with your
    email id. A Data Studio dashboard will look like this:
    ![ds-updated-quotas-dashboard](img/ds-updated-quotas-dashboard.png)
2.  Make a copy of the template from the copy icon at the top bar (top - right
    corner)
    ![ds-dropdown-copy](img/ds-dropdown-copy.png)
3.  Click on ‘Copy Report’ button
    ![ds-copy-report-fixed-new-data-source](img/ds-copy-report-fixed-new-data-source.png)
4.  This will create a copy of the report and open in Edit mode. If not click on
    ‘Edit’ button on top right corner in copied template:
    ![ds-edit-mode-updated](img/ds-edit-mode-updated.png)
5.  Select any one table like below ‘Disks Total GB - Quotas’ is selected. On the
    right panel in ‘Data’ tab, click on icon ‘edit data source’
    ![ds_edit_data_source](img/ds_edit_data_source.png)
    It will open the data source details
    ![ds_datasource_config_step_1]img/ds_datasource_config_step_1.png
6.  In the panel, select BigQuery project, dataset id and table name
    ![updated-bq-only-latest-row](img/updated-bq-only-latest-row.png)
7.  Verify the query by running in BigQuery Editor to make sure query returns right
    results and there are no syntax errors:

    Note: Replace BigQuery project id, dataset id and table name:

    ```sql
    WITH quota AS
    ( SELECT
    project_id as project_id,
    region,
    metric,
    DATE_TRUNC(addedAt, HOUR) AS HOUR,
    MAX(CASE
    WHEN mv_type='limit' THEN m_value
    ELSE
    NULL
    END
    ) AS q_limit,
    MAX(CASE
    WHEN mv_type='usage' THEN m_value
    ELSE
    NULL
    END
    ) AS usage
    FROM
    quota-monitoring-project-34.quota_monitoring_dataset.quota_monitoring_table
    GROUP BY
    1,
    2,
    3,
    4 )
    SELECT
    project_id,
    region,
    metric,
    HOUR,
    CASE
    WHEN q_limit='9223372036854775807' THEN 'unlimited'
    ELSE
    q_limit
    END
    AS q_limit,
    usage,
    ROUND((SAFE_DIVIDE(CAST(t.usage AS BIGNUMERIC),
    CAST(t.q_limit AS BIGNUMERIC))*100),2) AS consumption
    FROM (
    select *,
    RANK() OVER (PARTITION BY project_id,region,metric ORDER BY HOUR desc) AS latest_row
    FROM quota) t
    WHERE
    latest_row=1
    AND usage is not null
    AND q_limit is not null
    AND usage != '0'
    AND q_limit != '0'
    ```

8.  After making sure that query is returning results, replace it in the Data
    Studio, click on the ‘Reconnect’ button in the data source pane.
    ![ds_data_source_config_step_3](img/ds_data_source_config_step_3.png)
9.  In the next window, click on the ‘Done’ button.
    ![ds_data_source_config_step_2](img/ds_data_source_config_step_2.png)
10.  Click on ‘Region’ tab and repeat steps from 5 - 9 above with different
     query: ![ds-region-middle](img/ds-region-middle.png)

     The query is as follows: (Replace the project id, dataset id and table
     name and verify query running in Bigquery editor)

     ```sql
     SELECT region, metric FROM quota-monitoring-project-49.quota_monitoring_dataset.quota_monitoring_table
     WHERE m_value not like "0%"
     GROUP BY
         org_id,
         project_id,
         metric,
         region,
         vpc_name,
         targetpool_name,
         threshold,
         m_value
     ```

11.  Once the data source is configured, click on the ‘View’ button on the top
     right corner.
     Note: make additional changes in the layout like which metrics to be displayed
     on Dashboard, color shades for consumption column, number of rows for each
     table etc in the ‘Edit’ mode.
     ![ds-switch-to-view-mode](img/ds-switch-to-view-mode.png)

### 3.11 Scheduled Reporting

Quota monitoring reports can be scheduled from the Data Studio dashboard using
‘Schedule email delivery’. The screenshot of the Data studio dashboard will be
delivered as a pdf report to the configured email Ids.

![ds-schedule-email-button](img/ds-schedule-email-button.png)

### 3.11 Alerting

The alerts about services nearing their quota limits can be configured to be
sent via email as well as following external services:

*   Slack
*   PagerDuty
*   SMS
*   Custom Webhooks

#### 3.11.1 Slack Configuration

To configure notifications to be sent to a Slack channel, you must have the
Monitoring Notification Channel Editor role on the host project.

##### 3.11.1.1 Create Notification Channel

1.  In the Cloud Console, use the project picker to select your Google Cloud
    project, and then select Monitoring, or click the link here: Go to Monitoring
2.  In the Monitoring navigation pane, click  Alerting.
3.  Click Edit notification channels.
4.  In the Slack section, click Add new. This brings you to the Slack sign-in
    page:
    *   Select your Slack workspace.
    *   Click Allow to enable Google Cloud Monitoring access to your Slack
        workspace. This action takes you back to the Monitoring configuration page
        for your notification channel.
    *   Enter the name of the Slack channel you want to use for notifications.
    *   Enter a display name for the notification channel.
5.  In your Slack workspace:
    *   Invite the Monitoring app to the channel by sending the following
        message in the channel:
    *   /invite @Google Cloud Monitoring
    *   Be sure you invite the Monitoring app to the channel you specified when
        creating the notification channel in Monitoring.

##### 3.11.1.2 Configuring Alerting Policy

1.  In the Alerting section, click on Policies.
2.  Find the Policy named ‘Resource Reaching Quotas’. This policy was created
    via Terraform code above.
3.  Click Edit.
4.  It opens an Edit Alerting Policy page. Leave the current condition metric as
    is, and click on Next.
5.  In the Notification Options, Select the Slack Channel that you created above.
6.  Click on Save.

You should now receive alerts in your Slack channel whenever a quota reaches
the specified threshold limit.

## 4. Release Note

### 4.1 V4: Quota Monitoring across GCP services

#### New

*   The new version provides visibility into Quotas across various GCP services
    beyond the original GCE (Compute).
*   New Data Studio Dashboard template reporting metrics across GCP services

#### Known Limitations

*   The records are grouped by hour. Scheduler need to be configured to start
    running preferably at the beginning of the hour.
*   Out of the box solution is configured to scan quotas ‘once every day’. The
    SQL query to build the dashboard uses current date to filter the records. If
    you change the frequency, make changes to the query to rightly reflect the
    latest data.

## 5. What is Next

1.  Graphs (Quota utilization over a period of time)
2.  Search project, folder, org, region
3.  Threshold configurable for each metric

## 5. Contact Us

For any comments, issues or feedback, please reach out to us at quota-monitoring-solution@google.com