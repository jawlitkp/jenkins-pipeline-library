/*-
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2017 - 2020 wcm.io DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.wcm.devops.jenkins.pipeline.config.GenericConfigConstants
import io.wcm.devops.jenkins.pipeline.config.GenericConfigUtils
import io.wcm.devops.jenkins.pipeline.utils.logging.Logger
import io.wcm.devops.jenkins.pipeline.utils.maps.MapMergeMode
import io.wcm.devops.jenkins.pipeline.utils.maps.MapUtils

import static io.wcm.devops.jenkins.pipeline.utils.ConfigConstants.*

/**
 * Adapter function for sending instant messages to mattermost using a configMap
 *
 * @param config The config for the mattermost step
 */
void mattermost(Map config) {
  Map defaultConfig = [
    (NOTIFY_MATTERMOST): [
      (MAP_MERGE_MODE)                          : (MapMergeMode.REPLACE),
      (NOTIFY_MATTERMOST_CHANNEL)               : null,
      (NOTIFY_MATTERMOST_ENDPOINT)              : null,
      (NOTIFY_MATTERMOST_ENDPOINT_CREDENTIAL_ID): null,
      (NOTIFY_MATTERMOST_ICON)                  : null,
      (NOTIFY_MATTERMOST_COLOR)                 : null,
      (NOTIFY_MATTERMOST_TEXT)                  : null,
      (NOTIFY_MATTERMOST_MESSAGE)               : null,
      (NOTIFY_MATTERMOST_FAIL_ON_ERROR)         : false,
    ]
  ]
  Map mattermostConfig = MapUtils.merge(defaultConfig, config)[NOTIFY_MATTERMOST]

  String message = mattermostConfig[NOTIFY_MATTERMOST_MESSAGE]
  String text = mattermostConfig[NOTIFY_MATTERMOST_TEXT]
  String color = mattermostConfig[NOTIFY_MATTERMOST_COLOR]
  String channel = mattermostConfig[NOTIFY_MATTERMOST_CHANNEL]
  String icon = mattermostConfig[NOTIFY_MATTERMOST_ICON]
  String endpointCredentialId = mattermostConfig[NOTIFY_MATTERMOST_ENDPOINT_CREDENTIAL_ID]
  String endpoint = mattermostConfig[NOTIFY_MATTERMOST_ENDPOINT]
  Boolean failOnError = mattermostConfig[NOTIFY_MATTERMOST_FAIL_ON_ERROR]

  String endpointOrCredentialId = null
  if (endpoint) {
    endpointOrCredentialId = endpoint
  }
  else if (endpointCredentialId) {
    endpointOrCredentialId = endpointCredentialId
  }
  this.mattermost(message, text, color, channel, icon, endpointOrCredentialId, failOnError)
}

/**
 * Sends a instant mattermost message.
 *
 * @param message The message to send
 * @param text The text to send
 * @param color The color for the message
 * @param channel The channel to send to. When no value is provided the steps tries to
 * retrieve the channel using the Generic Configuration mechanmism.
 * @param icon The custom icon to use
 * @param endpointOrCredentialId The endpoint or an credential id for a string credential containing the endpoint. When no value is provided the steps tries to
 * retrieve the endpoint using the Generic Configuration mechanmism
 * @param failOnError Fail or not-fail on errors during sending mattermost message
 */
void mattermost(String message, String text = null, String color = null, String channel = null, String icon = null, String endpointOrCredentialId = null, failOnError = false) {
  Logger log = new Logger("im.mattermost")
  String endpoint = null
  String endpointCredentialId = null
  List credentials = []

  log.debug("message", message)
  log.debug("text", text)
  log.debug("color", color)
  log.debug("channel", channel)
  log.debug("icon", icon)
  log.debug("endpointOrCredentialId", endpointOrCredentialId)
  log.debug("failOnError", failOnError)

  GenericConfigUtils genericConfigUtils = new GenericConfigUtils(this)
  String search = genericConfigUtils.getFQJN()
  log.debug("Fully-Qualified Job Name (FQJN)", search)

  // load yamlConfig
  Map yamlConfig = genericConfig.load(GenericConfigConstants.NOTIFY_MATTERMOST_CONFIG_PATH, search, NOTIFY_MATTERMOST)
  Map notifyMattermost = yamlConfig[NOTIFY_MATTERMOST] ?: [:]

  if (channel == null) {
    log.debug("channel ($channel) is null, try to retrieve from generic config")
    channel = notifyMattermost[NOTIFY_MATTERMOST_CHANNEL] ?: channel
  }

  if (endpointOrCredentialId == null) {
    log.debug("endpointCredentialId ($endpointCredentialId) is null, load generic config")

    channel = notifyMattermost[NOTIFY_MATTERMOST_CHANNEL] ?: channel
    endpointCredentialId = notifyMattermost[NOTIFY_MATTERMOST_ENDPOINT_CREDENTIAL_ID] ?: endpointCredentialId
  } else if (endpointOrCredentialId.startsWith("http://") || endpointOrCredentialId.startsWith("https://")) {
    endpoint = endpointOrCredentialId
  } else {
    endpointCredentialId = endpointOrCredentialId
  }

  if (endpointCredentialId != null) {
    credentials.push(string(credentialsId: endpointCredentialId, variable: 'MATTERMOST_ENDPOINT'))
  }

  log.debug("endpointCredentialId", endpointCredentialId)
  log.debug("channel", channel)

  withCredentials(credentials) {
    if (credentials.size() > 0) {
      endpoint = "$MATTERMOST_ENDPOINT"
    }

    try {
      mattermostSend(
        message: message,
        text: text,
        color: color,
        channel: channel,
        icon: icon,
        endpoint: endpoint,
        failOnError: failOnError
      )
    } catch (Exception ex) {
      log.error("Unable to send mattermost notification. " +
        "Have you configured the endpoint? " +
        "See https://github.com/wcm-io-devops/jenkins-pipeline-library/blob/master/vars/im.md for details", ex.getCause().toString())
    }

  }
}
