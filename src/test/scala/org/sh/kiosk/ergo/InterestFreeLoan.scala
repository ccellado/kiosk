package org.sh.kiosk.ergo

import org.ergoplatform.{Pay2SAddress, Pay2SHAddress}
import org.sh.kiosk.ergo.ErgoMix.$ergoScript
import org.sh.kiosk.ergo.util.ErgoScriptUtil._
import org.sh.cryptonode.util.BytesUtil._

import scorex.crypto.hash.Blake2b256
import sigmastate.serialization.ErgoTreeSerializer.DefaultSerializer

object InterestFreeLoan extends App {
  /* using the description at https://www.ergoforum.org/t/interest-free-loan-contract/67

     Alice wants to borrow 100 USD using Ergs as collateral
     Bob wants to lend 100 USD to Alice against installments of 10 USD per month for 10 months

     The amount of collateral is adjusted based on the amount still owed.
     At any time, the collateral must be 1.2 times the value of amount pending to be paid to Bob

     For simplicity, we assume that the loan is paid back by Alice in tokens tethered to USD
     These tokens (with tokenID euroToken below) are assumed to be exchangeable by the lender and
     borrower at the rate of 1 token per USD.

     To summarize:
      Loan will be given in actual USD (with offchain trusted setup)
      Collateral will be in Ergs
      Repayment will be in secondary tokens tethered to USD.

   */
  runTest
  def runTest = {
    val rateOracleTokenID:Array[Byte] = Blake2b256("rate").toArray
    val usdTokenID:Array[Byte] = Blake2b256("USD").toArray // bob's one-way USD token

    val env = new Env
    env.setCollByte("rateOracleTokenID", rateOracleTokenID)
    env.setCollByte("usdTokenID", usdTokenID)

    // borrower
    val alicePrivateKey = getRandomBigInt
    val alice = hexToGroupElement(ECC.gExp(alicePrivateKey))

    // lender
    val bobPrivateKey = getRandomBigInt
    val bob = hexToGroupElement(ECC.gExp(bobPrivateKey))

    val oneMonth = 720*30 // 720 blocks per day
    val fiveDays = 720*5 // 720 blocks per day

    val startDate = 10000 // block height at which loan was started

    env.setGroupElement("alice", alice)
    env.setGroupElement("bob", bob)
    env.setInt("oneMonth", oneMonth)
    env.setInt("fiveDays", fiveDays)
    env.setInt("emi", 1000) // equal monthly installment in USD cents


    val ergoScript = new ErgoScript(env) {}

    val src =
      """{
        |  val dataInput = CONTEXT.dataInputs(0)
        |  val rate = dataInput.R4[Long].get // rate (how many USD for 1 ERG)
        |
        |  // Check 1: CORRECT RATE ORACLE (has the correct token id)
        |  val correctRateOracle = dataInput.tokens(0)._1 == rateOracleTokenID
        |
        |  val out = OUTPUTS(0) // should be same box script
        |  val currentUSD = SELF.R4[Long].get // how many USD owed to Bob
        |
        |  val lastPaymentHeight = SELF.R5[Int].get // what height last payment was made
        |  val thisPaymentHeight = out.R5[Int].get // what the current height is
        |
        |  // Check 2: CORRECT HEIGHT (within a day of current HEIGHT and higher than previous height)
        |  val correctHeight = thisPaymentHeight <= HEIGHT &&
        |                      thisPaymentHeight >= HEIGHT - 720 && // within a day
        |                      thisPaymentHeight > lastPaymentHeight
        |
        |  // Check 3: CORRECT SCRIPT
        |  val correctScript = out.propositionBytes == SELF.propositionBytes
        |
        |  val outUSD = out.R4[Long].get
        |  val usdDiff = currentUSD - outUSD
        |  val ergsDiff = SELF.value - out.value
        |  val correctErgsDiff = usdDiff * rate == ergsDiff
        |
        |  // Check 4: CORRECT Ergs difference
        |  val correctDiff = usdDiff == emi && correctErgsDiff
        |
        |  val correctTx = correctDiff && correctScript && correctRateOracle && correctHeight
        |
        |  // Four different ways box can be spent
        |  //
        |  //   1. Alice makes 10 Euro payment within 35 days of last payment
        |
        |  val bobBox = OUTPUTS(1) // this is the box where Alice will pay to Bob
        |  val correctBobAmt = bobBox.tokens(0)._1 == usdTokenID && bobBox.tokens(0)._2 == emi
        |  val correctBobScript = bobBox.propositionBytes == proveDlog(bob).propBytes
        |  val correctBobBox = correctBobAmt && correctBobScript
        |  val payment = correctTx && proveDlog(alice) && correctBobBox
        |
        |  //   2. Alice does not make payment within 35 days of last payment. Bob takes out due himself
        |  val nonPayment = correctTx && proveDlog(bob) && ((HEIGHT - lastPaymentHeight) > (oneMonth + fiveDays))
        |
        |  //   3. Price drops anytime (margin call)
        |  val marginCall = (currentUSD * rate > SELF.value) && proveDlog(bob)
        |
        |  //   4. Price increases anytime (profit sharing)
        |  val reqd = currentUSD * rate * 12 / 10
        |  val profit = SELF.value - reqd
        |  val ergPriceHigh = profit > 0
        |  val profitSharing = ergPriceHigh && correctScript && out.value == reqd && usdDiff == 0 &&
        |  OUTPUTS(1).propositionBytes == proveDlog(bob).propBytes && OUTPUTS(1).value == profit &&
        |  OUTPUTS(2).propositionBytes == proveDlog(alice).propBytes && OUTPUTS(2).value == profit &&
        |  lastPaymentHeight == thisPaymentHeight
        |
        |  anyOf(
        |    Coll(
        |      profitSharing,
        |      marginCall,
        |      payment,
        |      nonPayment
        |    )
        |  )
        |
        |}""".stripMargin

    val ergoTree = ergoScript.$compile(src)

    import ergoScript.$ergoAddressEncoder

    println(Pay2SAddress(ergoTree))
  }

}
