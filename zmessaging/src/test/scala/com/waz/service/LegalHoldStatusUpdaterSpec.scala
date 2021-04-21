package com.waz.service

import com.waz.api.IConversation.LegalHoldStatus
import com.waz.api.IConversation.LegalHoldStatus._
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.model.{ConvId, ConversationData, ConversationMemberData, UserId}
import com.waz.specs.AndroidFreeSpec
import com.wire.signals.{EventStream, SourceStream}

import scala.concurrent.Future

class LegalHoldStatusUpdaterSpec extends AndroidFreeSpec {

  // Mocks

  private val clients = mock[OtrClientsStorage]
  private val convs = mock[ConversationStorage]
  private val members = mock[MembersStorage]

  // Set up

  private var statusUpdater: LegalHoldStatusUpdater = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    // The status updater reacts to these event streams so we need to mock
    // them, but we return empty streams and instead test the status updater
    // methods directly.

    (clients.onChanged _)
      .expects()
      .once()
      .returning(EventStream())

    (members.onAdded _)
      .expects()
      .once()
      .returning(EventStream())

    (members.onDeleted _)
      .expects()
      .once()
      .returning(EventStream())

    statusUpdater = new LegalHoldStatusUpdater(clients, convs, members)
  }


  // Tests

  feature("it updates the conversation") {
    // Given
    val user1 = UserId("user1")
    val user2 = UserId("user2")
    val client1 = Client(ClientId("client1"), "")
    val client2 = Client(ClientId("client2"), "")
    val convId = ConvId("conv1")

    scenario("for specific conversation") {
      // Expectations
      setUpExpectationsForConversationUpdate()

      // When

      result(statusUpdater.updateLegalHoldStatus(Seq(convId)))
    }

    scenario("on clients changed") {
      // Given
      val userClients = UserClients(user1, Map(client1.id -> client1))

      // Expectations
      (members.getActiveConvs _)
          .expects(user1)
          .once()
          .returning(Future.successful(Seq(convId)))

      setUpExpectationsForConversationUpdate()

      // When
      result(statusUpdater.onClientsChanged(Seq(userClients)))
    }

    def setUpExpectationsForConversationUpdate(): Unit = {
      // Get the active users of the conversations.
      (members.getActiveUsers _)
        .expects(convId)
        .once()
        .returning(Future.successful(Seq(user1, user2)))

      // And all of their clients.
      (clients.getClients _)
        .expects(user1)
        .once()
        .returning(Future.successful(Seq(client1)))

      (clients.getClients _)
        .expects(user2)
        .once()
        .returning(Future.successful(Seq(client2)))

      // Finally update the conversation.
      (convs.updateAll2 _)
        .expects(Seq(convId), *)
        .once()
        // The return value is not important.
        .returning(Future.successful(Seq()))
    }

  }

  feature("it calculates status when") {

    scenario("existing status is disabled") {
      assert(existingStatus = DISABLED, detectedLegalHoldDevice = true, expectation = PENDING_APPROVAL)
      assert(existingStatus = DISABLED, detectedLegalHoldDevice = false, expectation = DISABLED)
    }

    scenario("existing status is pending approval") {
      assert(existingStatus = PENDING_APPROVAL, detectedLegalHoldDevice = true, expectation = PENDING_APPROVAL)
      assert(existingStatus = PENDING_APPROVAL, detectedLegalHoldDevice = false, expectation = DISABLED)
    }

    scenario("existing status is enabled") {
      assert(existingStatus = ENABLED, detectedLegalHoldDevice = true, expectation = ENABLED)
      assert(existingStatus = ENABLED, detectedLegalHoldDevice = false, expectation = DISABLED)
    }

    def assert(existingStatus: LegalHoldStatus,
               detectedLegalHoldDevice: Boolean,
               expectation: LegalHoldStatus): Unit = {
      // Given
      val conv = ConversationData(legalHoldStatus = existingStatus)

      // When
      val result = statusUpdater.update(conv, detectedLegalHoldDevice)

      // Then
      result.legalHoldStatus shouldEqual expectation
    }

  }

}