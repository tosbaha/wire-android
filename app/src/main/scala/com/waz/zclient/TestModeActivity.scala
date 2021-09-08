/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.waz.zclient

import android.app.{NotificationChannel, NotificationManager}
import android.content.{Context, Intent}
import android.os.{Build, Environment}
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{BackendConfig, FirebaseOptions}
import com.waz.utils.IoUtils
import com.waz.utils.wrappers.URI
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.BackendController
import org.json.{JSONException, JSONObject}

import java.io.FileInputStream

class TestModeActivity extends AppCompatActivity with ActivityHelper with DerivedLogTag {

  private val ZETA_TEST_MODE_NOTIFICATION_ID: Int = 1367874
  private val TestModeNotificationChannelId = "TEST_MODE_NOTIFICATION_CHANNEL_ID"
  private lazy val backendController = inject[BackendController]

  override def onStart() = {
    super.onStart()
    Toast.makeText(getApplicationContext, s"Test-Mode activated", Toast.LENGTH_LONG).show()

    val notificationManager = getApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    val channel = new NotificationChannel(TestModeNotificationChannelId, "TEST_MODE_NOTIFICATION_CHANNEL", NotificationManager.IMPORTANCE_MAX)
    channel.enableVibration(false)
    channel.setShowBadge(false)
    notificationManager.createNotificationChannel(channel)
    val builder = new NotificationCompat.Builder(getApplicationContext, TestModeNotificationChannelId)
      .setSmallIcon(R.drawable.red_alert)
      .setStyle(new NotificationCompat.BigTextStyle())
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setContentTitle(s"Wire is in Test-Mode")
      .setContentText(s"This mode is not intended for production use as it is insecure")
    notificationManager.notify(ZETA_TEST_MODE_NOTIFICATION_ID, builder.build())

    val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val files = downloadFolder.listFiles().filter(_.getName.equals(s"wire.json"))
    if (files.length > 0) {
      val content = IoUtils.asString(new FileInputStream(files.head))
      try {
        val json = new JSONObject(content)
        val config = new BackendConfig(
          json.getString("environment"),
          URI.parse(json.getString("baseUrl")),
          URI.parse(json.getString("websocketUrl")),
          None,
          URI.parse(json.getString("teamsUrl")),
          URI.parse(json.getString("accountsUrl")),
          URI.parse(json.getString("websiteUrl")),
          new FirebaseOptions("pushSenderId", "appId", "apiKey") // This is not applied anyway
        )
        backendController.setStoredBackendConfig(config)
      } catch {
        case e: JSONException => Toast.makeText(getApplicationContext, s"wire.json cannot be parsed", Toast.LENGTH_LONG).show()
      }
    } else {
      Toast.makeText(getApplicationContext, s"Did not find wire.json in " + downloadFolder.getAbsolutePath, Toast.LENGTH_LONG).show()
    }

    startMain()
  }

  override protected def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    verbose(l"Setting intent")
    setIntent(intent)
  }

  // Navigation //////////////////////////////////////////////////
  private def startMain() = {
    startActivity(new Intent(this, classOf[LaunchActivity]))
    finish()
  }

  private def startSignUp() = {
    startActivity(AppEntryActivity.newIntent(this))
    finish()
  }
}
