package org.ergoplatform.appkit

import org.ergoplatform.appkit.HttpClientTesting.{MockData, createMockedErgoClient, networkType}
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.FixtureAnyPropSpec

abstract class MockErgoClient extends FixtureAnyPropSpec with Matchers {
  case class FixtureParam(client: FileMockedErgoClient)
  val _networkType: NetworkType = networkType
  val ergoClient: FileMockedErgoClient = createMockedErgoClient(MockData(Nil, Nil))

  def withFixture(test: OneArgTest) = {
      try {
//        ergoClient.start()
      test(FixtureParam(ergoClient))
    }
    finally {
      ergoClient.stop()
    }
  }
}
