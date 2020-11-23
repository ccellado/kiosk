package kiosk.offchain.compiler

import kiosk.offchain.compiler.model.{Input, Output, Protocol, RegNum, Register, Token}

class Loader(implicit dictionary: Dictionary) {
  def load(p: Protocol): Unit = {
    (optSeq(p.constants) ++ optSeq(p.unaryOps) ++ optSeq(p.binaryOps) ++ optSeq(p.conversions)).foreach(dictionary.addDeclaration)
    optSeq(p.dataInputs).zipWithIndex.foreach { case (input, index) => loadInput(input, index, true) }
    p.inputs.zipWithIndex.foreach { case (input, index)             => loadInput(input, index, false) }
    p.outputs.foreach(loadOutput)
  }

  private def loadOutput(output: Output): Unit = {
    dictionary.addDeclarationLazily(output.address)
    optSeq(output.registers).foreach(register => dictionary.addDeclarationLazily(register))
    optSeq(output.tokens).foreach { outToken =>
      dictionary.addDeclarationLazily(outToken.id)
      dictionary.addDeclarationLazily(outToken.amount)
    }
    dictionary.addDeclarationLazily(output.nanoErgs)
    dictionary.commit
  }

  private def loadInput(input: Input, inputIndex: Int, isDataInput: Boolean): Unit = {
    input.id.foreach { id =>
      id.onChainVariable.foreach(dictionary.addOnChainDeclaration(_, isDataInput, _(inputIndex).boxId))
      dictionary.addDeclarationLazily(id)
    }
    input.address.foreach { ergoTree =>
      ergoTree.onChainVariable.foreach(dictionary.addOnChainDeclaration(_, isDataInput, _(inputIndex).address))
      dictionary.addDeclarationLazily(ergoTree)
    }
    optSeq(input.registers).foreach(register => loadRegister(register, inputIndex, isDataInput))
    optSeq(input.tokens).foreach(token => loadToken(token, inputIndex, isDataInput))
    input.nanoErgs.foreach { long =>
      long.onChainVariable.foreach(dictionary.addOnChainDeclaration(_, isDataInput, _(inputIndex).nanoErgs))
      dictionary.addDeclarationLazily(long)
    }
    dictionary.commit
  }

  private def loadRegister(register: Register, inputIndex: Int, isDataInput: Boolean): Unit = {
    register.onChainVariable.map(dictionary.addOnChainDeclaration(_, isDataInput, _(inputIndex).registers(RegNum.getIndex(register.num))))
    dictionary.addDeclarationLazily(register)
  }

  private def loadToken(token: Token, inputIndex: Int, isDataInput: Boolean): Unit = {
    token.id.onChainVariable.map(dictionary.addOnChainDeclaration(_, isDataInput, _(inputIndex).tokenIds(token.index)))
    dictionary.addDeclarationLazily(token.id)
    token.amount.onChainVariable.map(dictionary.addOnChainDeclaration(_, isDataInput, _(inputIndex).tokenAmounts(token.index)))
    dictionary.addDeclarationLazily(token.amount)
  }
}
