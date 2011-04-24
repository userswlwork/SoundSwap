/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package net.peterd.soundswap;

import android.media.AudioFormat;

public class Constants {

  public static final String TAG = "SoundSwap";

  /**
   * Account type string.
   */
  public static final String ACCOUNT_TYPE = "com.google";

  public static final String AUTH_TOKEN_TYPE = "ah";

  public static final String RECORDING_FILE_EXTENSION = "wav";
  public static final int RECORDING_SAMPLE_RATE = 22050;
  public static final int RECORDING_CHANNEL =
      AudioFormat.CHANNEL_CONFIGURATION_MONO;
  public static final int RECORDING_ENCODING =
      AudioFormat.ENCODING_PCM_16BIT;

  public static final String APPENGINE_DOMAIN = "sound-swap.appspot.com";
  public static final String HOST = "http://" + APPENGINE_DOMAIN;
  public static final String HOST_SECURE = "https://" + APPENGINE_DOMAIN;
  public static final String FORM_REDIRECT_URL = HOST + "/api/sound/upload_form_redirect";
  public static final String FETCH_SOUND_URL = HOST + "/api/sound";
  public static final String LIST_SOUNDS_URL = HOST + "/api/sound/list";
}
