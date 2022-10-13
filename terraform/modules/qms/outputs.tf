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

output "email_notif_channels" {
  value = [ for e in google_monitoring_notification_channel.emails  : e.name ]
  description = "List of full resource identifiers for email notification channels"
}

output "pubsub_notif_channels" {
  value = [ for p in google_monitoring_notification_channel.pubsubs : p.name ]
  description = "List of full resource identifiers for pubsub notification channels"
}
