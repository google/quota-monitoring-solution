/*
Copyright 2022 Google LLC

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

variable "project_id" {
  description = "Value of the Project Id to deploy the solution"
  type        = string
}

variable "region" {
  description = "Value of the region to deploy the solution. Use the same region as used for App Engine"
  type        = string
}

variable "creds_file" {
  type = string
  default = "key.json"
}

variable "service_account_email" {
  description = "Value of the Service Account"
  type        = string
}

variable "folders" {
  description = "Value of the list of folders to be scanned for quota"
  type        = string
}

variable "organizations" {
  description = "Value of the list of organization Ids to scanned for quota"
  type        = string
}

variable "threshold" {
  description = "Value of threshold for all metrics. If any metric usage >= the threshold, notification will be created"
  type        = string
}

variable "notification_email_address" {
  description = "Email Address to receive email notifications"
  type        = string
}

variable "alert_log_bucket_name" {
  description = "Bucket Name for alert Log Sink (must be globally unique)"
  type        = string
}
