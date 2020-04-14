// #Sireum

package org.sireum.hamr.act.periodic

import org.sireum._
import org.sireum.hamr.act.util.{AadlThread, SymbolTable}
import org.sireum.hamr.act.{ActOptions, CamkesAssemblyContribution, CamkesComponentContributions, CamkesGlueCodeContributions, Counter}
import org.sireum.hamr.ir
import org.sireum.message.Reporter

object Dispatcher {

  def handlePeriodicComponents(symbolTable: SymbolTable,
                               actOptions: ActOptions,
                              
                               connectionCounter: Counter,
                               timerAttributeCounter: Counter,
                              
                               headerInclude: String,
                               reporter: Reporter): CamkesAssemblyContribution = {

    return if(PeriodicUtil.usePacer(symbolTable, actOptions.platform))
      Pacer(symbolTable, actOptions).handlePeriodicComponents(
        connectionCounter,
        timerAttributeCounter,
        headerInclude,
        reporter)
    else
      PeriodicDispatcher(symbolTable, actOptions).handlePeriodicComponents(
        connectionCounter,
        timerAttributeCounter,
        headerInclude,
        reporter)
  }

  def handlePeriodicComponent(symbolTable: SymbolTable,
                              actOptions: ActOptions,
                              
                              c: ir.Component,
                              reporter: Reporter): (CamkesComponentContributions, CamkesGlueCodeContributions) = {
    
    val aadlThread: AadlThread = symbolTable.getThread(c)
    
    return if(PeriodicUtil.usePacer(symbolTable, actOptions.platform))
      Pacer(symbolTable, actOptions).handlePeriodicComponent(aadlThread, reporter)
    else
      PeriodicDispatcher(symbolTable, actOptions).handlePeriodicComponent(aadlThread, reporter)
  }

}

