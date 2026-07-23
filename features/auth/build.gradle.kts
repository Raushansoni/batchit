/*
 * Copyright 2023 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
  id("getstream.android.library")
  id("getstream.android.library.compose")
  id("getstream.android.feature")
  id("getstream.android.hilt")
  id("getstream.spotless")
  id(libs.plugins.google.secrets.get().pluginId)
}

android {
  namespace = "io.getstream.whatsappclone.auth"
}

secrets {
  propertiesFileName = "secrets.properties"
  defaultPropertiesFileName = "secrets.defaults.properties"
  ignoreList.add("keyToIgnore")
  ignoreList.add("sdk.*")
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.functions)

  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.google.id)
  implementation(libs.androidx.activity.compose)
  implementation(libs.accompanist.permissions)

  implementation(libs.stream.chat.client)
  implementation(libs.stream.video.core)

  implementation(libs.okhttp.logging)
  implementation(libs.okhttp)

  implementation(libs.androidx.lifecycle.runtimeCompose)
  implementation(libs.androidx.lifecycle.viewModelCompose)
  implementation(libs.stream.log)
}
